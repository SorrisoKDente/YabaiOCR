package dev.yabai.ocr.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.WindowPlacement
import dev.yabai.ocr.core.dict.LookupEngine
import dev.yabai.ocr.core.lens.EncodedImage
import dev.yabai.ocr.core.lens.LensClient
import dev.yabai.ocr.core.lens.LensOptions
import dev.yabai.ocr.core.lens.LensResult
import dev.yabai.ocr.core.lens.LensSegment
import dev.yabai.ocr.core.lens.PixelBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Dimension
import java.awt.Point
import java.awt.Rectangle
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/** A reading that belongs to a stretch of a recognised line, in the image's pixels. */
data class PickerReading(val text: String, val box: PixelBox, val certain: Boolean)

/** One column or row of a block, with where its readings go. */
data class PickerLine(val text: String, val box: PixelBox, val readings: List<PickerReading>)

/** One paragraph of the screen, ready to be drawn and clicked. */
data class PickerBlock(
    val text: String,
    val box: PixelBox?,
    val lines: List<PickerLine>,
)

/** What the full-screen scan is doing, so the window can say it instead of looking frozen. */
sealed interface PickerScan {
    data object Scanning : PickerScan
    data class Ready(val result: LensResult, val blocks: List<PickerBlock>) : PickerScan
    data class Failed(val message: String) : PickerScan
}

/**
 * The full-screen picker: the screen as it was, with a clickable box over every paragraph.
 *
 * This is the desktop face of the mobile «choose the snippet on the page» mode, and it exists
 * because a hand-drawn crop asks the user to guess where a sentence starts: drag one pixel too
 * high and the OCR loses the start of the line. Lens already reports where each paragraph is, so
 * the box is the crop — and clicking it is the choice.
 *
 * With readings on, the picker also explains the page before it is chosen: **ruby** above the
 * kanji (or in the gutter, for vertical text), or a **readable strip** beside each box with the
 * recognised sentence in normal type. The strip is what makes a page photographed at 8 pt legible
 * without zooming.
 *
 * Dragging a crop is still possible — the «Recortar à mão» button hands the same screenshot back to
 * [RegionSelectionWindow] — so a page the recogniser cannot make sense of is never a dead end.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SegmentPickerWindow(
    screenImage: BufferedImage,
    readingMode: DesktopPickerReadingMode,
    dictionaryFilter: String,
    engineProvider: () -> LookupEngine?,
    onBlockSelected: (BufferedImage, String) -> Unit,
    onManualCrop: () -> Unit,
    onDismiss: () -> Unit,
) {
    val imageBitmap = remember { screenImage.toComposeImageBitmap() }
    val imageSize = remember { Dimension(screenImage.width, screenImage.height) }

    var scan by remember { mutableStateOf<PickerScan>(PickerScan.Scanning) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var hovered by remember { mutableStateOf<Int?>(null) }
    var blockRects by remember { mutableStateOf<List<Rectangle?>>(emptyList()) }

    LaunchedEffect(screenImage, readingMode, dictionaryFilter) {
        scan = PickerScan.Scanning
        scan = withContext(Dispatchers.IO) {
            scanScreen(screenImage, readingMode, dictionaryFilter, engineProvider)
        }
    }

    Window(
        onCloseRequest = onDismiss,
        title = "Yabai OCR - Escolher trecho",
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
        },
    ) {
        // The text measurer needs the font providers of the window composition, not of the
        // application scope that hosts the window.
        val textMeasurer = rememberTextMeasurer()

        // Without a theme the window would inherit Material's light defaults: the toolbar button
        // came out white with white text.
        MaterialTheme(
            colors = darkColors(
                background = Color(0xFF12141A),
                surface = Color(0xFF1B1E28),
                primary = Color(0xFF4C78FF),
                onPrimary = Color.White,
                onBackground = Color.White,
                onSurface = Color.White,
            )
        ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(scan, canvasSize) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val position = event.changes.firstOrNull()?.position
                            if (position == null) continue

                            when (event.type) {
                                PointerEventType.Move -> {
                                    val point = Point(position.x.toInt(), position.y.toInt())
                                    val index = PickerBoxes.hit(blockRects, point)
                                    hovered = index
                                    window.cursor = java.awt.Cursor.getPredefinedCursor(
                                        if (index != null) java.awt.Cursor.HAND_CURSOR
                                        else java.awt.Cursor.DEFAULT_CURSOR
                                    )
                                }

                                PointerEventType.Press -> {
                                    if (event.button == PointerButton.Secondary) {
                                        onDismiss()
                                        return@awaitPointerEventScope
                                    }
                                    if (event.button == PointerButton.Primary) {
                                        val point = Point(position.x.toInt(), position.y.toInt())
                                        val index = PickerBoxes.hit(blockRects, point)
                                        val ready = scan as? PickerScan.Ready
                                        if (index != null && ready != null) {
                                            val block = ready.blocks.getOrNull(index)
                                            val box = block?.box
                                            if (box != null) {
                                                onBlockSelected(
                                                    cropBlock(screenImage, box, imageSize),
                                                    block.text,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                canvasSize = IntSize(size.width.toInt(), size.height.toInt())
                drawImage(
                    image = imageBitmap,
                    dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                )
                drawRect(Color.Black.copy(alpha = 0.45f))

                val ready = scan as? PickerScan.Ready
                val blocks = ready?.blocks.orEmpty()
                val canvas = Dimension(size.width.toInt(), size.height.toInt())
                blockRects = blocks.map { block ->
                    block.box?.let { PickerBoxes.toCanvas(it, imageSize, canvas) }
                }

                val taken = mutableListOf<Rectangle>()

                blocks.forEachIndexed { index, block ->
                    val rect = blockRects.getOrNull(index) ?: return@forEachIndexed
                    val isHovered = hovered == index
                    val rectTopLeft = Offset(rect.x.toFloat(), rect.y.toFloat())
                    val rectSize = Size(rect.width.toFloat(), rect.height.toFloat())
                    drawRect(
                        color = if (isHovered) Color(0x334C78FF) else Color.Transparent,
                        topLeft = rectTopLeft,
                        size = rectSize,
                    )
                    drawRect(
                        color = if (isHovered) Color(0xFF8AB4FF) else Color(0xFF4C78FF),
                        topLeft = rectTopLeft,
                        size = rectSize,
                        style = Stroke(width = if (isHovered) 2.5f else 1.4f),
                    )

                    if (readingMode == DesktopPickerReadingMode.RUBY) {
                        drawRuby(block, imageSize, canvas, textMeasurer)
                    }

                    if (readingMode == DesktopPickerReadingMode.LABEL) {
                        val style = TextStyle(
                            color = Color.White,
                            fontSize = 13.sp,
                            lineHeight = 17.sp,
                        )
                        val layout = textMeasurer.measure(
                            AnnotatedString(truncate(block.text, 90)),
                            style,
                            constraints = Constraints(maxWidth = 340),
                        )
                        val strip = Dimension(layout.size.width + 16, layout.size.height + 10)
                        val point = PickerLabels.place(rect, strip, canvas, taken)
                        taken += Rectangle(point.x, point.y, strip.width, strip.height)
                        val stripTopLeft = Offset(point.x.toFloat(), point.y.toFloat())
                        val stripSize = Size(strip.width.toFloat(), strip.height.toFloat())
                        drawRect(
                            color = Color(0xFF1B1E28).copy(alpha = 0.94f),
                            topLeft = stripTopLeft,
                            size = stripSize,
                        )
                        drawRect(
                            color = Color(0xFF4C78FF),
                            topLeft = stripTopLeft,
                            size = stripSize,
                            style = Stroke(width = 1.2f),
                        )
                        drawText(
                            textLayoutResult = layout,
                            topLeft = Offset(point.x + 8f, point.y + 5f),
                        )
                    }
                }
            }

            // Instruction badge
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
                    .background(Color(0xFF1B1E28).copy(alpha = 0.92f), RoundedCornerShape(8.dp))
                    .border(1.dp, Color(0xFF4C78FF), RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = when (scan) {
                        is PickerScan.Scanning -> "🔍 Reconhecendo a tela inteira…"
                        is PickerScan.Failed -> "⚠️ O reconhecimento da tela falhou — recorte à mão."
                        is PickerScan.Ready ->
                            "🖱️ Clique num trecho para traduzir • " + readingHint(readingMode) +
                                " • [ESC] ou [Direito] para cancelar"
                    },
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            }

            if (scan is PickerScan.Scanning) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color(0xFF4C78FF))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Lendo a tela inteira e procurando os trechos…",
                            color = Color(0xFFC9CEDC),
                            fontSize = 13.sp,
                        )
                    }
                }
            }

            if (scan is PickerScan.Failed) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Card(
                        backgroundColor = Color(0xFF3A1C1C),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text(
                                text = "Não foi possível reconhecer a tela inteira",
                                color = Color(0xFFFFB4AB),
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = (scan as PickerScan.Failed).message,
                                color = Color.White,
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp)
                    .background(Color(0xFF1B1E28), RoundedCornerShape(10.dp))
                    .border(1.dp, Color(0xFF4C78FF), RoundedCornerShape(10.dp))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = onManualCrop,
                    colors = ButtonDefaults.outlinedButtonColors(
                        backgroundColor = Color(0xFF2E3344),
                        contentColor = Color.White,
                    ),
                ) {
                    Text("✂️ Recortar à mão", fontSize = 13.sp, maxLines = 1, softWrap = false)
                }
                OutlinedButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.outlinedButtonColors(
                        backgroundColor = Color(0xFF2E3344),
                        contentColor = Color(0xFFFFB4AB),
                    ),
                ) {
                    Text("✕ Cancelar", fontSize = 13.sp, maxLines = 1, softWrap = false)
                }
            }
        }
        }
    }
}

/** Draws the dictionary readings of [block]: above horizontal runs, in the gutter of vertical ones. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRuby(
    block: PickerBlock,
    imageSize: Dimension,
    canvas: Dimension,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
) {
    for (line in block.lines) {
        val vertical = HoverTextSelector.isVertical(line.box)
        for (reading in line.readings) {
            val span = PickerBoxes.toCanvas(reading.box, imageSize, canvas)
            val spot = PickerRuby.spot(span, vertical, reading.text.length) ?: continue
            val style = TextStyle(
                color = if (reading.certain) Color.White else Color(0xFFFFD479),
                fontSize = spot.size.toSp(),
                fontWeight = FontWeight.Bold,
            )
            if (spot.stacked) {
                var top = spot.y
                for (character in reading.text) {
                    val layout = textMeasurer.measure(AnnotatedString(character.toString()), style)
                    drawOutlined(layout, Offset(spot.x, top + (spot.size - layout.size.height) / 2f))
                    top += spot.size
                }
            } else {
                val layout = textMeasurer.measure(AnnotatedString(reading.text), style)
                drawOutlined(
                    layout,
                    Offset(
                        spot.x - layout.size.width / 2f,
                        spot.y + (spot.size - layout.size.height) / 2f,
                    ),
                )
            }
        }
    }
}

/** Text that stays readable over a photo: a dark outline first, then the fill. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawOutlined(
    layout: androidx.compose.ui.text.TextLayoutResult,
    topLeft: Offset,
) {
    drawText(
        textLayoutResult = layout,
        topLeft = topLeft,
        drawStyle = Stroke(width = 2.6f),
        color = Color.Black.copy(alpha = 0.85f),
    )
    drawText(textLayoutResult = layout, topLeft = topLeft)
}

private fun readingHint(mode: DesktopPickerReadingMode): String = when (mode) {
    DesktopPickerReadingMode.RUBY -> "rubi sobre os kanjis"
    DesktopPickerReadingMode.LABEL -> "faixa indicativa legível"
    DesktopPickerReadingMode.OFF -> "leituras desativadas nos Ajustes"
}

/** OCR of the whole screen, plus the readings the dictionaries know, done off the UI thread. */
private fun scanScreen(
    screenImage: BufferedImage,
    readingMode: DesktopPickerReadingMode,
    dictionaryFilter: String,
    engineProvider: () -> LookupEngine?,
): PickerScan {
    return try {
        val baos = ByteArrayOutputStream()
        ImageIO.write(screenImage, "png", baos)
        val encoded = EncodedImage(baos.toByteArray(), screenImage.width, screenImage.height)
        val result = LensClient().recognize(encoded, LensOptions(language = "ja"))

        val engine = if (result.blocks.isEmpty()) null else runCatching(engineProvider).getOrNull()
        val only = if (dictionaryFilter.isBlank()) emptySet() else setOf(dictionaryFilter)

        val blocks = result.blocks.map { block ->
            val pieces: List<LensSegment> = block.pieces.ifEmpty {
                block.lines.map { LensSegment(it, block.box) }
            }
            val lines = pieces.mapNotNull { piece ->
                val pieceBox: PixelBox = piece.box ?: return@mapNotNull null
                val readings = if (engine == null || readingMode == DesktopPickerReadingMode.OFF) {
                    emptyList()
                } else {
                    // A dictionary closed by the manager while the scan runs must not cost the
                    // boxes: without readings the picker still clicks and crops.
                    runCatching {
                        val matches = engine.lookup(piece.text, only = only)
                        engine.readings(piece.text, matches, only).mapNotNull { reading ->
                            if (reading.start >= reading.end) return@mapNotNull null
                            PickerReading(
                                text = reading.text,
                                box = HoverTextSelector.tokenBox(
                                    piece.text,
                                    pieceBox,
                                    reading.start,
                                    reading.end,
                                ),
                                certain = reading.certain,
                            )
                        }
                    }.getOrDefault(emptyList<PickerReading>())
                }
                PickerLine(piece.text, pieceBox, readings)
            }
            PickerBlock(text = block.text, box = block.box, lines = lines)
        }
        PickerScan.Ready(result, blocks)
    } catch (e: Exception) {
        PickerScan.Failed(e.message ?: "Erro ao reconhecer a tela.")
    }
}

/** The image rectangle of a block, with the air the second OCR needs, cropped out. */
private fun cropBlock(screenImage: BufferedImage, box: PixelBox, image: Dimension): BufferedImage {
    val padded = PickerBoxes.padded(
        Rectangle(box.x, box.y, box.width, box.height),
        pad = 12,
        image = image,
    )
    val safe = PickerBoxes.sanitize(padded, image)
    return ScreenCapture.cropImage(
        source = screenImage,
        x = safe.x,
        y = safe.y,
        width = safe.width,
        height = safe.height,
    )
}

private fun truncate(text: String, limit: Int): String =
    if (text.length <= limit) text else text.take(limit - 1) + "…"
