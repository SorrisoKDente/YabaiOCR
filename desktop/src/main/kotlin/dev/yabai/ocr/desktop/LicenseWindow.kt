package dev.yabai.ocr.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import dev.yabai.ocr.desktop.i18n.DesktopStrings

/**
 * The license text that ships inside the jar.
 *
 * The file is not a copy kept by hand: the build copies the repository `LICENSE` into the resources
 * as `LICENSE.txt`, so the text shown here is always the one that governs the build.
 */
object LicenseText {
    fun load(): String = LicenseText::class.java
        .getResourceAsStream("/LICENSE.txt")
        ?.bufferedReader()
        ?.use { it.readText() }
        .orEmpty()
}

/** Read-only view of the license, opened from Settings → About. */
@Composable
fun LicenseWindow(onClose: () -> Unit) {
    Window(
        onCloseRequest = onClose,
        title = DesktopStrings.licenseWindowTitle,
        state = WindowState(width = 720.dp, height = 760.dp),
    ) {
        LaunchedEffect(Unit) {
            window.iconImage = AppIconGenerator.createWindowImage(64)
        }

        YabaiDesktopTheme(darkTheme = remember { DesktopSettings().darkTheme }) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colors.background,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                ) {
                    Text(
                        text = LicenseText.load().ifEmpty { DesktopStrings.licenseMissing },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = MaterialTheme.colors.onSurface,
                    )
                }
            }
        }
    }
}
