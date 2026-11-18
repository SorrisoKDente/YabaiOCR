package dev.yabai.ocr.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Divider
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
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import dev.yabai.ocr.desktop.i18n.DesktopStrings
import java.awt.EventQueue
import java.nio.file.Path
import kotlin.system.exitProcess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Everything the application scope found when it looked for newer versions.
 *
 * Both kinds arrive in one window on purpose. Two always-on-top notices fight for focus, and «baixar o
 * app» and «atualizar o dicionário» are the same decision for the reader: «update what you have». The
 * only real conflict is operational — an app update ends in a restart, which would kill a dictionary
 * download halfway — and one window is what lets that rule be said once.
 */
internal data class UpdatesNotice(
    val app: AppUpdateInfo? = null,
    val dictionaries: List<DictionaryUpdateOffer> = emptyList(),
)

/**
 * The notice window: the app row (download, restart, release notes) and the dictionary row (what is
 * newer, «update»).
 *
 * Before this window the dictionary check only wrote the tray tooltip, so on a desktop without a tray
 * (GNOME without AppIndicator) a found update was invisible, and on Windows it was a tooltip nobody
 * hovers over. Finding an update and being able to act on it belong in the same place.
 */
@Composable
internal fun UpdatesNoticeWindow(
    notice: UpdatesNotice,
    onDismiss: () -> Unit,
    onOpenReleaseNotes: () -> Unit,
    onDictionaryUpdatesInstalled: () -> Unit,
) {
    Window(
        onCloseRequest = onDismiss,
        title = "Yabai OCR - ${DesktopStrings.updatesTitle}",
        // Fits the content: the default 800×600 dp frame left three quarters of the window blank on
        // a scaled display, and a scroll container cannot be measured without a height limit.
        state = rememberWindowState(size = DpSize.Unspecified),
        alwaysOnTop = true,
        resizable = false,
    ) {
        YabaiDesktopTheme(darkTheme = remember { DesktopSettings().darkTheme }) {
            Surface(color = MaterialTheme.colors.background) {
                val scope = rememberCoroutineScope()
                val app = notice.app
                var isDownloading by remember { mutableStateOf(false) }
                var progress by remember { mutableStateOf<Int?>(null) }
                var message by remember { mutableStateOf<String?>(null) }
                var readyToRestart by remember { mutableStateOf<Path?>(null) }
                var installingDictionaries by remember { mutableStateOf(false) }
                var dictionaryProgress by remember { mutableStateOf<String?>(null) }
                var dictionariesInstalled by remember { mutableStateOf(false) }

                // An app update ends in a restart, which would kill a dictionary download halfway.
                // One at a time, and the window says which one comes first.
                val appBusy = isDownloading || readyToRestart != null

                Column(
                    modifier = Modifier
                        .width(470.dp)
                        .padding(24.dp),
                ) {
                    Text(
                        text = if (app != null) DesktopStrings.updateAvailable else DesktopStrings.updatesTitle,
                        style = MaterialTheme.typography.h6,
                    )

                    if (app != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = DesktopStrings.updateAvailableMessage(app.latestVersion),
                            style = MaterialTheme.typography.subtitle2,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = DesktopStrings.updateGeneralNotice,
                            style = MaterialTheme.typography.body2,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                enabled = !isDownloading && readyToRestart == null,
                                onClick = {
                                    val url = app.downloadUrl
                                    if (url.isNullOrBlank()) {
                                        // A release without an asset for this platform can only be
                                        // opened in the browser.
                                        scope.launch {
                                            val opened = withContext(Dispatchers.IO) {
                                                DesktopUrlOpener().open(app.releaseUrl)
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
                                                message = DesktopStrings.updateReadyRestart(app.latestVersion)
                                            }

                                            outcome is UpdateOutcome.InstallerLaunched -> {
                                                message = DesktopStrings.updateInstallerLaunched(app.latestVersion)
                                            }

                                            attempt.isFailure -> {
                                                message = DesktopStrings.updateDownloadFailed
                                            }

                                            else -> {
                                                // An unpacked build cannot replace itself; open the
                                                // release page instead of failing silently.
                                                message = DesktopStrings.updateManualFallback
                                                withContext(Dispatchers.IO) {
                                                    DesktopUrlOpener().open(app.releaseUrl)
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
                                        app.downloadUrl != null -> DesktopStrings.btnDownloadUpdate
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
                    }

                    if (notice.dictionaries.isNotEmpty()) {
                        if (app != null) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Divider()
                            Spacer(modifier = Modifier.height(14.dp))
                        } else {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        Text(
                            text = DesktopStrings.updatesDictionariesHeader(notice.dictionaries.size),
                            style = MaterialTheme.typography.subtitle2,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        notice.dictionaries.forEach { offer ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = offer.entry.name,
                                    style = MaterialTheme.typography.body2,
                                )
                                Text(
                                    text = DesktopStrings.dictionaryUpdateVersions(
                                        offer.installedRevision,
                                        offer.remoteRevision,
                                    ),
                                    style = MaterialTheme.typography.caption,
                                    color = MaterialTheme.colors.secondary,
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        if (appBusy) {
                            Text(
                                text = DesktopStrings.updatesDictBlockedByApp,
                                style = MaterialTheme.typography.caption,
                                color = MaterialTheme.colors.secondary,
                            )
                        } else {
                            Button(
                                enabled = !installingDictionaries && !dictionariesInstalled,
                                onClick = {
                                    installingDictionaries = true
                                    dictionaryProgress = null
                                    message = null
                                    scope.launch {
                                        // The install path is the manager window's own: the notice is
                                        // a second entrance, not a second implementation.
                                        val dictDir = DesktopSettings().dictDirectory()
                                        val attempt = runCatching {
                                            notice.dictionaries.forEach { offer ->
                                                withContext(Dispatchers.IO) {
                                                    downloadAndInstallDictionary(
                                                        entry = offer.entry,
                                                        dictDir = dictDir,
                                                        onProgress = { text, _ ->
                                                            EventQueue.invokeLater { dictionaryProgress = text }
                                                        },
                                                    )
                                                }
                                            }
                                        }
                                        installingDictionaries = false
                                        dictionaryProgress = null
                                        if (attempt.isSuccess) {
                                            dictionariesInstalled = true
                                            message = DesktopStrings.updatesDictInstalledNotice
                                            onDictionaryUpdatesInstalled()
                                        } else {
                                            message = attempt.exceptionOrNull()?.message
                                                ?: DesktopStrings.updatesDictInstallError
                                        }
                                    }
                                },
                            ) {
                                Text(
                                    when {
                                        installingDictionaries -> DesktopStrings.updatingStatus
                                        dictionariesInstalled -> DesktopStrings.updatesDictInstalledNotice
                                        else -> DesktopStrings.btnUpdateDictionaries
                                    }
                                )
                            }
                        }

                        val dictStatus = dictionaryProgress
                        if (installingDictionaries && dictStatus != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = dictStatus,
                                style = MaterialTheme.typography.caption,
                                color = MaterialTheme.colors.secondary,
                            )
                        }
                    }

                    val note = message
                    if (note != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(text = note, style = MaterialTheme.typography.body2)
                    }

                    Spacer(modifier = Modifier.height(14.dp))
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
