package dev.yabai.ocr.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.WindowPlacement
import java.awt.image.BufferedImage
import kotlin.math.abs

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun RegionSelectionWindow(
    screenImage: BufferedImage,
    onRegionSelected: (BufferedImage) -> Unit,
    onDismiss: () -> Unit,
) {
    val imageBitmap = remember { screenImage.toComposeImageBitmap() }
    var startPos by remember { mutableStateOf<Offset?>(null) }
    var currentPos by remember { mutableStateOf<Offset?>(null) }

    Window(
        onCloseRequest = onDismiss,
        title = "Yabai OCR - Selecionar Área",
        undecorated = true,
        alwaysOnTop = true,
        focusable = true,
        transparent = true,
        state = WindowState(placement = WindowPlacement.Maximized),
        onKeyEvent = { keyEvent ->
            if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Escape) {
                onDismiss()
                true
            } else {
                false
            }
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            startPos = offset
                            currentPos = offset
                        },
                        onDrag = { change, dragAmount ->
                            currentPos = currentPos?.plus(dragAmount) ?: change.position
                        },
                        onDragEnd = {
                            val start = startPos
                            val current = currentPos
                            if (start != null && current != null) {
                                val canvasWidth = size.width.toFloat()
                                val canvasHeight = size.height.toFloat()

                                val scaleX = screenImage.width.toFloat() / canvasWidth
                                val scaleY = screenImage.height.toFloat() / canvasHeight

                                val left = minOf(start.x, current.x)
                                val top = minOf(start.y, current.y)
                                val width = abs(current.x - start.x)
                                val height = abs(current.y - start.y)

                                if (width > 8 && height > 8) {
                                    val realX = (left * scaleX).toInt()
                                    val realY = (top * scaleY).toInt()
                                    val realW = (width * scaleX).toInt()
                                    val realH = (height * scaleY).toInt()

                                    val cropped = ScreenCapture.cropImage(
                                        source = screenImage,
                                        x = realX,
                                        y = realY,
                                        width = realW,
                                        height = realH
                                    )
                                    onRegionSelected(cropped)
                                } else {
                                    onDismiss()
                                }
                            } else {
                                onDismiss()
                            }
                        },
                        onDragCancel = {
                            onDismiss()
                        }
                    )
                }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.button == PointerButton.Secondary) { // Right click to cancel
                                onDismiss()
                            }
                        }
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasWidth = size.width.toInt()
                val canvasHeight = size.height.toInt()

                // 1. Draw captured background image scaled to fill 100% of window
                drawImage(
                    image = imageBitmap,
                    dstSize = IntSize(canvasWidth, canvasHeight)
                )

                val start = startPos
                val current = currentPos

                if (start != null && current != null) {
                    val left = minOf(start.x, current.x)
                    val top = minOf(start.y, current.y)
                    val right = maxOf(start.x, current.x)
                    val bottom = maxOf(start.y, current.y)
                    val rect = Rect(left, top, right, bottom)

                    // 2. Draw dim dark scrim everywhere EXCEPT the selection box
                    clipRect(
                        left = left,
                        top = top,
                        right = right,
                        bottom = bottom,
                        clipOp = ClipOp.Difference
                    ) {
                        drawRect(Color.Black.copy(alpha = 0.5f))
                    }

                    // 3. Draw sharp accent border around selected region
                    drawRect(
                        color = Color(0xFF4C78FF),
                        topLeft = rect.topLeft,
                        size = rect.size,
                        style = Stroke(width = 2f)
                    )
                } else {
                    // Draw light dim overlay while waiting for user to start dragging
                    drawRect(Color.Black.copy(alpha = 0.35f))
                }
            }
        }
    }
}
