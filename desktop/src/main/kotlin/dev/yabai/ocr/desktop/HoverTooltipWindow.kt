package dev.yabai.ocr.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.darkColors
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
import androidx.compose.ui.window.WindowState
import dev.yabai.ocr.core.dict.TokenMatch
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent

/**
 * Tooltip window anchored next to the pointer with dictionary definitions.
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
    // Keep WindowState constant so changing matches never resets window position!
    val state = remember {
        WindowState(
            width = 460.dp,
            height = 320.dp,
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
        LaunchedEffect(Unit) {
            window.isAutoRequestFocus = false
            window.background = java.awt.Color(0, 0, 0, 0)
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
                Column(modifier = Modifier.fillMaxSize()) {
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
                            text = if (activeFilter.isBlank()) "🌐 Todos os Dicionários" else "📖 $activeFilter",
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
                                    text = "📖 Selecionar Dicionário ▾",
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
                                    Text("🌐 Todos os Dicionários")
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
}

private fun placeWindow(window: java.awt.Window, anchor: Point) {
    val screen = HoverGeometry.screenAt(anchor)
    val size = window.size
    val width = if (size.width > 0) size.width else 460
    val height = if (size.height > 0) size.height else 320
    val point = HoverGeometry.placeTooltip(
        pointer = anchor,
        size = java.awt.Dimension(width, height),
        screen = screen,
    )
    window.setLocation(point.x, point.y)
}
