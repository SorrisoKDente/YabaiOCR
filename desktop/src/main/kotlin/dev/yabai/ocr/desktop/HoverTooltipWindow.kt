package dev.yabai.ocr.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.darkColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import dev.yabai.ocr.core.dict.TokenMatch
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import kotlinx.coroutines.delay

/**
 * Card shown next to the pointer with the dictionary entry for the word under it.
 *
 * Positioning is done through AWT (`window.setLocation`) rather than `WindowPosition`: the pointer
 * from `MouseInfo` is in screen pixels, and converting those pixels to Compose `Dp` is exactly the
 * kind of HiDPI assumption that put the old card off screen. The window is also clamped to the
 * monitor bounds and flips to the other side of the pointer instead of running off the edge.
 */
@Composable
fun HoverTooltipWindow(
    matches: List<TokenMatch>,
    mousePoint: Point,
    onDismiss: () -> Unit,
    onBoundsChanged: (Rectangle?) -> Unit = {},
) {
    val height = remember(matches) { estimateHeight(matches) }
    // No position in the state: Compose would re-apply it on every recomposition and undo the
    // placement done through AWT. The card is placed explicitly below.
    val state = remember(matches) {
        WindowState(
            width = 460.dp,
            height = height.dp,
        )
    }

    Window(
        onCloseRequest = onDismiss,
        title = "Yabai OCR - Hover",
        undecorated = true,
        alwaysOnTop = true,
        focusable = false,
        transparent = true,
        state = state,
    ) {
        LaunchedEffect(mousePoint, window) {
            window.isAutoRequestFocus = false
            window.background = java.awt.Color(0, 0, 0, 0)
            placeWindow(window, mousePoint)
            // Compose finishes its first layout after this effect starts and may size the window
            // once more; placing again here keeps the card next to the pointer instead of in the
            // corner the toolkit picks by default.
            delay(80)
            placeWindow(window, mousePoint)
            println("[Hover] cartão em ${window.location.x},${window.location.y} (${window.width}×${window.height})")
            onBoundsChanged(window.bounds)
        }

        DisposableEffect(window) {
            val listener = object : ComponentAdapter() {
                override fun componentMoved(e: ComponentEvent) = onBoundsChanged(window.bounds)
                override fun componentResized(e: ComponentEvent) = onBoundsChanged(window.bounds)
            }
            window.addComponentListener(listener)
            onDispose {
                window.removeComponentListener(listener)
                onBoundsChanged(null)
            }
        }

        MaterialTheme(
            colors = darkColors(
                background = Color(0xFF1B1E28),
                surface = Color(0xFF1B1E28),
                primary = Color(0xFF4C78FF),
                onPrimary = Color.White,
                onBackground = Color.White,
                onSurface = Color.White,
            )
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color(0xFF1B1E28),
                shape = RoundedCornerShape(10.dp),
                elevation = 8.dp,
            ) {
                SelectionContainer {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(matches) { match ->
                            val entry = match.entry
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = if (entry.reading.isBlank()) {
                                            entry.term
                                        } else {
                                            "${entry.term}  【${entry.reading}】"
                                        },
                                        style = MaterialTheme.typography.subtitle2.copy(fontWeight = FontWeight.Bold),
                                        color = Color.White,
                                    )
                                    if (match.dictionaryTitle.isNotBlank()) {
                                        Text(
                                            text = match.dictionaryTitle,
                                            style = MaterialTheme.typography.caption,
                                            color = Color(0xFFA0A6B8),
                                        )
                                    }
                                }

                                if (match.surface != entry.term) {
                                    Text(
                                        text = "${match.surface} → ${entry.term}",
                                        style = MaterialTheme.typography.caption,
                                        color = Color(0xFF81D4FA),
                                    )
                                }
                                if (match.reasons.isNotEmpty()) {
                                    Text(
                                        text = match.reasons.joinToString(" → "),
                                        style = MaterialTheme.typography.caption,
                                        color = Color(0xFF81D4FA),
                                    )
                                }
                                if (entry.tags.isNotBlank()) {
                                    Text(
                                        text = entry.tags,
                                        style = MaterialTheme.typography.caption,
                                        color = Color(0xFFE5A93C),
                                    )
                                }

                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = entry.definition,
                                    style = MaterialTheme.typography.body2.copy(lineHeight = 17.sp),
                                    color = Color(0xFFC7CCDA),
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Divider(color = Color(0xFF2E3344), thickness = 0.5.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Picks a reasonable initial height from the amount of text; the card scrolls if it is long. */
private fun estimateHeight(matches: List<TokenMatch>): Int {
    var pixels = 42
    for (match in matches.take(4)) {
        pixels += 26
        if (match.surface != match.entry.term) pixels += 16
        if (match.reasons.isNotEmpty()) pixels += 16
        if (match.entry.tags.isNotBlank()) pixels += 16
        val lines = match.entry.definition.count { it == '\n' } + 1
        pixels += lines.coerceAtMost(14) * 17 + 10
    }
    return pixels.coerceIn(150, 560)
}

/** Places the card below/right of the pointer, flipping and clamping so it always stays on screen. */
private fun placeWindow(window: java.awt.Window, anchor: Point) {
    val screen = HoverGeometry.screenBounds()
    val size = window.size
    val width = if (size.width > 0) size.width else 460
    val height = if (size.height > 0) size.height else 280
    val point = HoverGeometry.placeTooltip(
        pointer = anchor,
        size = java.awt.Dimension(width, height),
        screen = screen,
    )
    window.setLocation(point.x, point.y)
}
