package dev.yabai.ocr.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.window.Window
import java.awt.image.BufferedImage

@Composable
fun TemporaryImageViewerWindow(
    croppedImage: BufferedImage,
    onClose: () -> Unit,
) {
    val imageBitmap = croppedImage.toComposeImageBitmap()

    Window(
        onCloseRequest = onClose,
        title = "Yabai OCR - Visualizador Temporário (Recorte)"
    ) {
        MaterialTheme {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Recorte Selecionado (${croppedImage.width} x ${croppedImage.height} px)") }
                    )
                }
            ) { padding ->
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = imageBitmap,
                        contentDescription = "Recorte de Tela",
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
