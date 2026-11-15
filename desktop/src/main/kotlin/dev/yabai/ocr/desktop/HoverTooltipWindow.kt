package dev.yabai.ocr.desktop

import dev.yabai.ocr.desktop.i18n.DesktopStrings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.Divider
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import kotlinx.coroutines.delay
import dev.yabai.ocr.core.dict.TokenMatch
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent

/**
 * Tooltip window anchored next to the pointer with dictionary definitions.
 * Sized dynamically to wrap content tightly without empty space or cutting text at the bottom.
 */
@Composable
fun HoverTooltipWindow(
    matches: List<TokenMatch>,
    mousePoint: Point,
    activeFilter: String = "",
    installedDictTitles: List<String> = emptyList(),
    onSelectDictionary: (String) -> Unit = {},
    onDismiss: () -> Unit,
    onBoundsChanged: (Rectangle?) -> Unit = {},
) {
    val estimatedHeight = remember(matches) { estimateHeight(matches) }

    // Where the card goes, decided before the window exists: Compose places a new window from
    // `WindowState.position`, and a `setLocation` that lands while the window is still being mapped
    // is what the compositor ignores — on KDE/Wayland that left the card in the screen's top-left
    // corner instead of beside the pointer.
    val anchor = remember(mousePoint, estimatedHeight) {
        HoverGeometry.placeTooltip(
            pointer = mousePoint,
            size = java.awt.Dimension(TOOLTIP_WIDTH, estimatedHeight),
            screen = HoverGeometry.screenAt(mousePoint),
        )
    }

    val state = remember(anchor) {
        WindowState(
            width = TOOLTIP_WIDTH.dp,
            height = estimatedHeight.dp,
            position = WindowPosition(anchor.x.dp, anchor.y.dp),
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
        LaunchedEffect(estimatedHeight, mousePoint, window) {
            window.isAutoRequestFocus = false
            window.background = java.awt.Color(0, 0, 0, 0)
            window.setSize(TOOLTIP_WIDTH, estimatedHeight)
            placeWindow(window, mousePoint)
            onBoundsChanged(window.bounds)
            // The window exists now, so the placement is no longer decided by whoever mapped it.
            // A WM that keeps its own position for the first frames is corrected here.
            delay(TOOLTIP_REPOSITION_DELAY_MS)
            placeWindow(window, mousePoint)
            onBoundsChanged(window.bounds)
        }

        DisposableEffect(window) {
            val listener = object : ComponentAdapter() {
                override fun componentMoved(e: ComponentEvent) = onBoundsChanged(window.bounds)
                override fun componentResized(e: ComponentEvent) {
                    onBoundsChanged(window.bounds)
                }
            }
            window.addComponentListener(listener)
            onDispose {
                window.removeComponentListener(listener)
                onBoundsChanged(null)
            }
        }

        YabaiDesktopTheme(darkTheme = remember { DesktopSettings().darkTheme }) {
            Surface(
                modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                color = Color(0xFF1B1E28),
                shape = RoundedCornerShape(10.dp),
                elevation = 8.dp,
            ) {
                Column(modifier = Modifier.fillMaxWidth().wrapContentHeight()) {
                    // Header Bar showing Active Filter & Dropdown Selector
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF141620))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (activeFilter.isBlank()) DesktopStrings.allDictionaries else "📖 $activeFilter",
                            style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold),
                            color = Color(0xFF5D84FF)
                        )

                        var expandedMenu by remember { mutableStateOf(false) }
                        Box {
                            Surface(
                                modifier = Modifier.clickable { expandedMenu = true },
                                color = Color(0xFF2E3344),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = DesktopStrings.selectDictDropdown,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    fontSize = 11.sp,
                                    color = Color.White
                                )
                            }

                            DropdownMenu(
                                expanded = expandedMenu,
                                onDismissRequest = { expandedMenu = false }
                            ) {
                                DropdownMenuItem(onClick = {
                                    expandedMenu = false
                                    onSelectDictionary("")
                                }) {
                                    Text(DesktopStrings.allDictionaries)
                                }
                                installedDictTitles.forEach { title ->
                                    DropdownMenuItem(onClick = {
                                        expandedMenu = false
                                        onSelectDictionary(title)
                                    }) {
                                        Text("📖 $title")
                                    }
                                }
                            }
                        }
                    }

                    SelectionContainer {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().wrapContentHeight().padding(12.dp),
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
}

/** Accurately estimates content height so window wraps definitions tightly without cutting text off. */
private fun estimateHeight(matches: List<TokenMatch>): Int {
    var pixels = 58 // Header bar (28px) + container padding + safety buffer
    for (match in matches.take(3)) {
        pixels += 26 // Term & Reading line
        if (match.surface != match.entry.term) pixels += 18
        if (match.reasons.isNotEmpty()) pixels += 18
        if (match.entry.tags.isNotBlank()) pixels += 18

        val lines = match.entry.definition.split('\n').sumOf { line ->
            // Full-width Japanese & formatted translations wrap at ~32 chars in 460dp width
            if (line.isEmpty()) 1 else (line.length / 32) + 1
        }
        pixels += lines * 20 + 16
    }
    return pixels.coerceIn(130, 520)
}

/** Width of the card, in pixels; also the width [estimateHeight]'s line wrapping assumes. */
private const val TOOLTIP_WIDTH = 460

/**
 * How long to wait before placing the card again.
 *
 * The first placement runs while the window is being mapped, and every compositor is free to put
 * the window where it wants until it is on screen. 120 ms is long enough for the map on this
 * desktop and short enough that the card does not visibly jump.
 */
private const val TOOLTIP_REPOSITION_DELAY_MS = 120L

private fun placeWindow(window: java.awt.Window, anchor: Point) {
    val screen = HoverGeometry.screenAt(anchor)
    val size = window.size
    val width = if (size.width > 0) size.width else TOOLTIP_WIDTH
    val height = if (size.height > 0) size.height else 220
    val point = HoverGeometry.placeTooltip(
        pointer = anchor,
        size = java.awt.Dimension(width, height),
        screen = screen,
    )
    window.setLocation(point.x, point.y)
}
