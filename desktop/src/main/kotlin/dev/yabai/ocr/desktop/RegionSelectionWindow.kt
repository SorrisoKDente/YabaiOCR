package dev.yabai.ocr.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.WindowPlacement
import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

enum class CropHandle {
    TOP_LEFT, TOP, TOP_RIGHT,
    LEFT, RIGHT,
    BOTTOM_LEFT, BOTTOM, BOTTOM_RIGHT,
    INSIDE, NONE
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun RegionSelectionWindow(
    screenImage: BufferedImage,
    onRegionSelected: (BufferedImage) -> Unit,
    onDismiss: () -> Unit,
) {
    val imageBitmap = remember { screenImage.toComposeImageBitmap() }

    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragCurrent by remember { mutableStateOf<Offset?>(null) }
    var cropRect by remember { mutableStateOf<Rect?>(null) }

    var activeHandle by remember { mutableStateOf(CropHandle.NONE) }
    var initialRectForDrag by remember { mutableStateOf<Rect?>(null) }

    fun confirmCrop(rect: Rect, canvasWidth: Float, canvasHeight: Float) {
        if (rect.width < 10 || rect.height < 10) {
            onDismiss()
            return
        }
        val scaleX = screenImage.width.toFloat() / canvasWidth
        val scaleY = screenImage.height.toFloat() / canvasHeight

        val realX = (rect.left * scaleX).toInt()
        val realY = (rect.top * scaleY).toInt()
        val realW = (rect.width * scaleX).toInt()
        val realH = (rect.height * scaleY).toInt()

        val cropped = ScreenCapture.cropImage(
            source = screenImage,
            x = realX,
            y = realY,
            width = realW,
            height = realH
        )
        onRegionSelected(cropped)
    }

    Window(
        onCloseRequest = onDismiss,
        title = "Yabai OCR - Selecionar Área (Lightshot)",
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
        var canvasSize by remember { mutableStateOf(IntSize.Zero) }

        val handleRadius = 16f
        fun hitTest(pos: Offset, rect: Rect): CropHandle {
            val tol = handleRadius * 1.5f
            val tl = rect.topLeft
            val tr = rect.topRight
            val bl = rect.bottomLeft
            val br = rect.bottomRight

            if ((pos - tl).getDistance() <= tol) return CropHandle.TOP_LEFT
            if ((pos - tr).getDistance() <= tol) return CropHandle.TOP_RIGHT
            if ((pos - bl).getDistance() <= tol) return CropHandle.BOTTOM_LEFT
            if ((pos - br).getDistance() <= tol) return CropHandle.BOTTOM_RIGHT

            if (abs(pos.x - rect.left) <= tol && pos.y in rect.top..rect.bottom) return CropHandle.LEFT
            if (abs(pos.x - rect.right) <= tol && pos.y in rect.top..rect.bottom) return CropHandle.RIGHT
            if (abs(pos.y - rect.top) <= tol && pos.x in rect.left..rect.right) return CropHandle.TOP
            if (abs(pos.y - rect.bottom) <= tol && pos.x in rect.left..rect.right) return CropHandle.BOTTOM

            if (rect.contains(pos)) return CropHandle.INSIDE
            return CropHandle.NONE
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            dragStart = offset
                            dragCurrent = offset
                            val currentRect = cropRect

                            if (currentRect != null) {
                                val handle = hitTest(offset, currentRect)
                                if (handle != CropHandle.NONE) {
                                    activeHandle = handle
                                    initialRectForDrag = currentRect
                                    return@detectDragGestures
                                }
                            }
                            activeHandle = CropHandle.NONE
                            cropRect = null
                            initialRectForDrag = null
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragCurrent = dragCurrent?.plus(dragAmount) ?: change.position
                            val start = dragStart ?: return@detectDragGestures
                            val current = dragCurrent ?: return@detectDragGestures

                            val rect = initialRectForDrag
                            if (rect != null && activeHandle != CropHandle.NONE) {
                                val dx = current.x - start.x
                                val dy = current.y - start.y
                                var l = rect.left
                                var t = rect.top
                                var r = rect.right
                                var b = rect.bottom

                                when (activeHandle) {
                                    CropHandle.TOP_LEFT -> { l += dx; t += dy }
                                    CropHandle.TOP -> { t += dy }
                                    CropHandle.TOP_RIGHT -> { r += dx; t += dy }
                                    CropHandle.LEFT -> { l += dx }
                                    CropHandle.RIGHT -> { r += dx }
                                    CropHandle.BOTTOM_LEFT -> { l += dx; b += dy }
                                    CropHandle.BOTTOM -> { b += dy }
                                    CropHandle.BOTTOM_RIGHT -> { r += dx; b += dy }
                                    CropHandle.INSIDE -> {
                                        l += dx; r += dx; t += dy; b += dy
                                    }
                                    else -> {}
                                }

                                val finalLeft = min(l, r)
                                val finalRight = max(l, r)
                                val finalTop = min(t, b)
                                val finalBottom = max(t, b)

                                cropRect = Rect(finalLeft, finalTop, finalRight, finalBottom)
                            } else {
                                val left = min(start.x, current.x)
                                val top = min(start.y, current.y)
                                val right = max(start.x, current.x)
                                val bottom = max(start.y, current.y)
                                cropRect = Rect(left, top, right, bottom)
                            }
                        },
                        onDragEnd = {
                            activeHandle = CropHandle.NONE
                            initialRectForDrag = null
                        },
                        onDragCancel = {
                            activeHandle = CropHandle.NONE
                            initialRectForDrag = null
                        }
                    )
                }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.button == PointerButton.Secondary) {
                                onDismiss()
                            } else {
                                val pos = event.changes.firstOrNull()?.position
                                val rect = cropRect
                                if (pos != null) {
                                    val handle = if (rect != null) hitTest(pos, rect) else CropHandle.NONE
                                    val cursorType = when (handle) {
                                        CropHandle.TOP_LEFT -> java.awt.Cursor.NW_RESIZE_CURSOR
                                        CropHandle.TOP_RIGHT -> java.awt.Cursor.NE_RESIZE_CURSOR
                                        CropHandle.BOTTOM_LEFT -> java.awt.Cursor.SW_RESIZE_CURSOR
                                        CropHandle.BOTTOM_RIGHT -> java.awt.Cursor.SE_RESIZE_CURSOR
                                        CropHandle.LEFT -> java.awt.Cursor.W_RESIZE_CURSOR
                                        CropHandle.RIGHT -> java.awt.Cursor.E_RESIZE_CURSOR
                                        CropHandle.TOP -> java.awt.Cursor.N_RESIZE_CURSOR
                                        CropHandle.BOTTOM -> java.awt.Cursor.S_RESIZE_CURSOR
                                        CropHandle.INSIDE -> java.awt.Cursor.MOVE_CURSOR
                                        else -> java.awt.Cursor.CROSSHAIR_CURSOR
                                    }
                                    window.cursor = java.awt.Cursor.getPredefinedCursor(cursorType)
                                }
                            }
                        }
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                canvasSize = IntSize(size.width.toInt(), size.height.toInt())
                val canvasWidth = size.width
                val canvasHeight = size.height

                // 1. Draw background image
                drawImage(
                    image = imageBitmap,
                    dstSize = IntSize(canvasWidth.toInt(), canvasHeight.toInt())
                )

                val rect = cropRect
                if (rect != null) {
                    // 2. Dim outside selection
                    clipRect(
                        left = rect.left,
                        top = rect.top,
                        right = rect.right,
                        bottom = rect.bottom,
                        clipOp = ClipOp.Difference
                    ) {
                        drawRect(Color.Black.copy(alpha = 0.55f))
                    }

                    // 3. Selection border
                    drawRect(
                        color = Color(0xFF4C78FF),
                        topLeft = rect.topLeft,
                        size = rect.size,
                        style = Stroke(width = 2f)
                    )

                    // 4. Resize handles
                    val handleSize = 10f
                    val handles = listOf(
                        rect.topLeft,
                        Offset(rect.center.x, rect.top),
                        rect.topRight,
                        Offset(rect.right, rect.center.y),
                        rect.bottomRight,
                        Offset(rect.center.x, rect.bottom),
                        rect.bottomLeft,
                        Offset(rect.left, rect.center.y)
                    )
                    for (h in handles) {
                        drawRect(
                            color = Color.White,
                            topLeft = Offset(h.x - handleSize / 2, h.y - handleSize / 2),
                            size = Size(handleSize, handleSize)
                        )
                        drawRect(
                            color = Color(0xFF4C78FF),
                            topLeft = Offset(h.x - handleSize / 2, h.y - handleSize / 2),
                            size = Size(handleSize, handleSize),
                            style = Stroke(width = 1f)
                        )
                    }
                } else {
                    drawRect(Color.Black.copy(alpha = 0.35f))
                }
            }

            // Top instruction hint badge
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
                    .background(Color(0xFF1B1E28).copy(alpha = 0.9f), RoundedCornerShape(8.dp))
                    .border(1.dp, Color(0xFF4C78FF), RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "🖱️ Arraste para selecionar • Puxe as bordas para redimensionar  •  [ESC] ou [Direito] para cancelar",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Lightshot-style Floating Action Toolbar
            val rect = cropRect
            if (rect != null && canvasSize != IntSize.Zero) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(24.dp)
                        .background(Color(0xFF1B1E28), RoundedCornerShape(10.dp))
                        .border(1.dp, Color(0xFF4C78FF), RoundedCornerShape(10.dp))
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            confirmCrop(rect, canvasSize.width.toFloat(), canvasSize.height.toFloat())
                        },
                        colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF4C78FF), contentColor = Color.White)
                    ) {
                        Text("🔍 Reconhecer (OCR)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    OutlinedButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFFB4AB))
                    ) {
                        Text("✕ Cancelar", fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
