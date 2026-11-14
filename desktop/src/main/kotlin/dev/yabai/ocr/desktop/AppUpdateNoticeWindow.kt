package dev.yabai.ocr.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import dev.yabai.ocr.desktop.i18n.DesktopStrings
import java.awt.EventQueue
import java.nio.file.Path
import kotlin.system.exitProcess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The update the automatic check found when the app opened.
 *
 * Before this window the startup check only wrote a line in the tray menu, so a user who never
 * opened that menu never learned a new version existed. The window carries the download itself
 * instead of pointing at Settings: «there is an update» and «here is how to get it» belong together.
 */
@Composable
fun AppUpdateNoticeWindow(
    info: AppUpdateInfo,
    onDismiss: () -> Unit,
    onOpenReleaseNotes: () -> Unit,
) {
    Window(
        onCloseRequest = onDismiss,
        title = "Yabai OCR - ${DesktopStrings.updateAvailable}",
        alwaysOnTop = true,
        resizable = false,
    ) {
        YabaiDesktopTheme(darkTheme = remember { DesktopSettings().darkTheme }) {
            Surface(color = MaterialTheme.colors.background) {
                val scope = rememberCoroutineScope()
                var isDownloading by remember { mutableStateOf(false) }
                var progress by remember { mutableStateOf<Int?>(null) }
                var message by remember { mutableStateOf<String?>(null) }
                var readyToRestart by remember { mutableStateOf<Path?>(null) }

                Column(
                    modifier = Modifier
                        .width(450.dp)
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = DesktopStrings.updateAvailable,
                        style = MaterialTheme.typography.h6,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = DesktopStrings.updateAvailableMessage(info.latestVersion),
                        style = MaterialTheme.typography.subtitle2,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = DesktopStrings.updateGeneralNotice,
                        style = MaterialTheme.typography.body2,
                    )

                    val note = message
                    if (note != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(text = note, style = MaterialTheme.typography.body2)
                    }

                    Spacer(modifier = Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            enabled = !isDownloading && readyToRestart == null,
                            onClick = {
                                val url = info.downloadUrl
                                if (url.isNullOrBlank()) {
                                    // A release without an asset for this platform can only be
                                    // opened in the browser.
                                    scope.launch {
                                        val opened = withContext(Dispatchers.IO) {
                                            DesktopUrlOpener().open(info.releaseUrl)
                                        }
                                        if (!opened) message = DesktopStrings.updateLinkOpenFailed
                                    }
                                    return@Button
                                }

                                isDownloading = true
                                progress = null
                                message = null
                                scope.launch {
                                    val attempt = withContext(Dispatchers.IO) {
                                        runCatching {
                                            UpdateInstaller.install(
                                                url = url,
                                                family = DesktopPlatform.family,
                                                environment = System.getenv(),
                                            ) { percent ->
                                                // Called from the download thread; Compose state
                                                // belongs to the UI thread.
                                                EventQueue.invokeLater { progress = percent }
                                            }
                                        }
                                    }
                                    isDownloading = false
                                    progress = null

                                    val outcome = attempt.getOrNull()
                                    when {
                                        outcome is UpdateOutcome.Replaced -> {
                                            readyToRestart = outcome.appImage
                                            message = DesktopStrings.updateReadyRestart(info.latestVersion)
                                        }

                                        outcome is UpdateOutcome.InstallerLaunched -> {
                                            message = DesktopStrings.updateInstallerLaunched(info.latestVersion)
                                        }

                                        attempt.isFailure -> {
                                            message = DesktopStrings.updateDownloadFailed
                                        }

                                        else -> {
                                            // An unpacked build cannot replace itself; open the
                                            // release page instead of failing silently.
                                            message = DesktopStrings.updateManualFallback
                                            withContext(Dispatchers.IO) {
                                                DesktopUrlOpener().open(info.releaseUrl)
                                            }
                                        }
                                    }
                                }
                            },
                        ) {
                            Text(
                                when {
                                    isDownloading -> DesktopStrings.updateDownloading(progress)
                                    readyToRestart != null -> DesktopStrings.updateInstalled
                                    info.downloadUrl != null -> DesktopStrings.btnDownloadUpdate
                                    else -> DesktopStrings.btnDownloadOnGithub
                                }
                            )
                        }

                        if (readyToRestart != null) {
                            Button(
                                onClick = {
                                    val appImage = readyToRestart ?: return@Button
                                    if (UpdateInstaller.restart(appImage)) {
                                        // The new build is already running; this one must go.
                                        exitProcess(0)
                                    } else {
                                        message = DesktopStrings.updateRestartFailed
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    backgroundColor = MaterialTheme.colors.primary,
                                ),
                            ) {
                                Text(DesktopStrings.btnRestartNow)
                            }
                        }

                        OutlinedButton(onClick = onOpenReleaseNotes) {
                            Text(DesktopStrings.btnViewReleaseNotes)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        OutlinedButton(onClick = onDismiss) {
                            Text(DesktopStrings.btnLater)
                        }
                    }
                }
            }
        }
    }
}
