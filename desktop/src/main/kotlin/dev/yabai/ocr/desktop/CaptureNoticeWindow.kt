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
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import dev.yabai.ocr.desktop.i18n.DesktopStrings

/**
 * Visible explanation when a capture cannot happen (permission denied, unsupported session, or any
 * unexpected failure). Before this window existed the failure only reached stderr and the user saw
 * nothing at all.
 *
 * The window packs itself to its content: [rememberWindowState] defaults to 800×600 dp, so on a
 * display scaled at 150–200% the notice filled a quarter of the frame and the rest stayed blank.
 */
@Composable
fun CaptureNoticeWindow(
    message: String,
    onDismiss: () -> Unit,
    title: String = DesktopStrings.captureNoticeTitle,
    heading: String = DesktopStrings.captureNoticeHeading,
) {
    Window(
        onCloseRequest = onDismiss,
        title = title,
        state = rememberWindowState(size = DpSize.Unspecified),
        alwaysOnTop = true,
        resizable = false,
    ) {
        YabaiDesktopTheme(darkTheme = remember { DesktopSettings().darkTheme }) {
            // No `fillMaxSize` here: it made the first measure report the frame instead of the
            // content, and the packed window ended above the button (cut pill, no label).
            Surface(color = MaterialTheme.colors.background) {
                Column(
                    modifier = Modifier
                        .width(430.dp)
                        .padding(24.dp),
                ) {
                    Text(
                        text = heading,
                        style = MaterialTheme.typography.h6,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.body2,
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Button(onClick = onDismiss) {
                            Text("OK")
                        }
                    }
                }
            }
        }
    }
}
