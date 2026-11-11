package dev.yabai.ocr.desktop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import dev.yabai.ocr.core.dict.*
import dev.yabai.ocr.desktop.i18n.DesktopStrings
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun QuickDictWindow(onClose: () -> Unit) {
    val settings = remember { DesktopSettings() }

    val windowState = rememberWindowState(
        width = settings.quickDictWidth.dp,
        height = settings.quickDictHeight.dp,
        position = if (settings.quickDictX != null && settings.quickDictY != null) {
            WindowPosition(settings.quickDictX!!.dp, settings.quickDictY!!.dp)
        } else {
            WindowPosition(Alignment.Center)
        }
    )

    // Save size and position whenever they change
    LaunchedEffect(windowState.size, windowState.position) {
        settings.quickDictWidth = windowState.size.width.value.toInt()
        settings.quickDictHeight = windowState.size.height.value.toInt()
        val pos = windowState.position
        if (pos is WindowPosition.Absolute) {
            settings.quickDictX = pos.x.value.toInt()
            settings.quickDictY = pos.y.value.toInt()
        }
    }

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<TokenMatch>>(emptyList()) }
    var selectedFilter by remember { mutableStateOf(settings.dictionaryFilter) }
    // Word (headword/reading) or translation (definition) search; see DesktopSettings.searchByTranslation.
    var translationMode by remember { mutableStateOf(settings.searchByTranslation) }
    var status by remember { mutableStateOf("") }

    // Load dictionaries from configured dictionary directory
    val engine = remember {
        try {
            DesktopDictLoader.loadEngine(settings)
        } catch (e: Exception) {
            null
        }
    }

    val installedDictTitles = remember(settings) {
        val list = mutableListOf<String>()
        try {
            DesktopDictLoader.loadInstalledFiles(settings).forEach { file ->
                val dict = runCatching { CompactDictionary.open(file) }.getOrNull()
                if (dict != null) list.add(dict.info.title)
            }
        } catch (_: Exception) {}
        list.toList()
    }

    val suggestions = remember(query, engine, selectedFilter, translationMode) {
        // A Latin word has no Japanese headword to suggest; «espada» must not offer エスパダ.
        if (!translationMode && engine != null && query.isNotBlank()) {
            val onlySet = if (selectedFilter.isBlank()) emptySet() else setOf(selectedFilter)
            engine.suggest(query, only = onlySet)
        } else emptyList()
    }

    LaunchedEffect(query, selectedFilter, translationMode) {
        val current = engine
        if (current != null && query.isNotBlank()) {
            val onlySet = if (selectedFilter.isBlank()) emptySet() else setOf(selectedFilter)
            results = withContext(Dispatchers.Default) {
                if (translationMode) {
                    status = DesktopStrings.dictIndexPreparing
                    val found = current.searchTranslation(query, only = onlySet) { progress ->
                        if (progress.building) {
                            status = "${DesktopStrings.dictIndexPreparing} ${(progress.fraction * 100).toInt()}%"
                        }
                    }
                    status = ""
                    found
                } else {
                    status = ""
                    current.search(query, only = onlySet)
                }
            }
        } else {
            results = emptyList()
            status = ""
        }
    }

    Window(
        onCloseRequest = onClose,
        title = DesktopStrings.quickDictTitle,
        state = windowState
    ) {
        LaunchedEffect(Unit) {
            window.iconImage = AppIconGenerator.createWindowImage(64)
        }

        YabaiDesktopTheme(darkTheme = remember { DesktopSettings().darkTheme }) {
            val palette = yabaiPalette()
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colors.background) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(DesktopStrings.quickDictShortTitle, style = MaterialTheme.typography.subtitle2.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colors.onSurface)

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = {
                                    translationMode = !translationMode
                                    settings.searchByTranslation = translationMode
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colors.onSurface)
                            ) {
                                Text(
                                    if (translationMode) DesktopStrings.searchTypeTranslation else DesktopStrings.searchTypeWord,
                                    fontSize = 10.sp
                                )
                            }

                            var expandedFilter by remember { mutableStateOf(false) }
                            Box {
                                OutlinedButton(
                                    onClick = { expandedFilter = true },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colors.onSurface)
                                ) {
                                    Text(if (selectedFilter.isBlank()) DesktopStrings.allDictsLabel else "📖 $selectedFilter", fontSize = 10.sp)
                                }
                                DropdownMenu(
                                    expanded = expandedFilter,
                                    onDismissRequest = { expandedFilter = false }
                                ) {
                                    DropdownMenuItem(onClick = {
                                        selectedFilter = ""
                                        expandedFilter = false
                                    }) {
                                        Text(DesktopStrings.allDictionaries)
                                    }
                                    installedDictTitles.forEach { title ->
                                        DropdownMenuItem(onClick = {
                                            selectedFilter = title
                                            expandedFilter = false
                                        }) {
                                            Text("📖 $title")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            label = {
                                Text(
                                    if (translationMode) DesktopStrings.searchPlaceholderDefinition
                                    else DesktopStrings.searchPlaceholderExtended
                                )
                            },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = TextFieldDefaults.outlinedTextFieldColors(
                                textColor = MaterialTheme.colors.onSurface,
                                focusedBorderColor = MaterialTheme.colors.primary,
                                unfocusedBorderColor = palette.fieldBorder
                            )
                        )

                        if (query.isNotBlank()) {
                            Button(
                                onClick = { query = "" },
                                colors = ButtonDefaults.buttonColors(
                                    backgroundColor = palette.field,
                                    contentColor = MaterialTheme.colors.onSurface
                                )
                            ) {
                                Text(DesktopStrings.btnClear, fontSize = 11.sp)
                            }
                        } else {
                            Button(
                                onClick = {
                                    val clip = runCatching {
                                        Toolkit.getDefaultToolkit().systemClipboard
                                            .getData(DataFlavor.stringFlavor) as? String
                                    }.getOrNull()
                                    if (!clip.isNullOrBlank()) {
                                        query = clip.trim()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    backgroundColor = palette.field,
                                    contentColor = MaterialTheme.colors.onSurface
                                )
                            ) {
                                Text(DesktopStrings.btnPaste, fontSize = 11.sp)
                            }
                        }
                    }

                    if (status.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = status,
                            style = MaterialTheme.typography.caption,
                            color = MaterialTheme.colors.secondary
                        )
                    }

                    if (suggestions.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            suggestions.take(4).forEach { suggestion ->
                                Surface(
                                    modifier = Modifier.clickable { query = suggestion },
                                    color = palette.chip,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = suggestion,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.caption.copy(color = palette.info)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (engine == null) {
                        Text(
                            DesktopStrings.noDictsWarning,
                            color = MaterialTheme.colors.error,
                            style = MaterialTheme.typography.caption
                        )
                    } else {
                        SelectionContainer {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(results) { match ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        backgroundColor = MaterialTheme.colors.surface,
                                        elevation = 0.dp
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "${match.entry.term}  【${match.entry.reading}】",
                                                    style = MaterialTheme.typography.subtitle1.copy(color = MaterialTheme.colors.onSurface)
                                                )
                                                if (match.dictionaryTitle.isNotBlank()) {
                                                    Text(
                                                        text = match.dictionaryTitle,
                                                        style = MaterialTheme.typography.caption,
                                                        color = MaterialTheme.colors.secondary
                                                    )
                                                }
                                            }

                                            if (match.entry.tags.isNotBlank()) {
                                                Text(
                                                    text = match.entry.tags,
                                                    style = MaterialTheme.typography.caption,
                                                    color = palette.warning
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(4.dp))

                                            Text(
                                                text = match.entry.definition,
                                                style = MaterialTheme.typography.body2,
                                                color = MaterialTheme.colors.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
