package dev.yabai.ocr.desktop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The search window.
 *
 * [activation] changes every time the user asks for the window again — the tray entry and the
 * global shortcut both count — so a window that was minimised comes back instead of the request
 * doing nothing (D103).
 */
@Composable
fun QuickDictWindow(activation: Int = 0, onClose: () -> Unit) {
    val settings = remember { DesktopSettings() }

    // D103: a corner left on a monitor that is no longer attached would open the window outside
    // every screen; then the window centres itself instead.
    val screens = remember { screenBoundsNow() }
    val windowState = rememberWindowState(
        width = settings.quickDictWidth.dp,
        height = settings.quickDictHeight.dp,
        position = rememberedWindowCorner(settings.quickDictX, settings.quickDictY, screens)
            ?.let { WindowPosition(it.x.dp, it.y.dp) }
            ?: WindowPosition(Alignment.Center),
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
    var lookupError by remember { mutableStateOf<String?>(null) }
    var ankiNotice by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // Load dictionaries from configured dictionary directory
    val engine = remember {
        try {
            DesktopDictLoader.loadEngine(settings)
        } catch (e: Exception) {
            null
        }
    }

    // Names only: opening each compiled dictionary here read every key index just to print its
    // title, which delayed the window the user is waiting for (D81).
    val installedDictTitles = remember(settings) { DesktopDictLoader.installedTitles(settings) }

    val suggestions = remember(query, engine, selectedFilter, translationMode) {
        // A Latin word has no Japanese headword to suggest; «espada» must not offer エスパダ.
        if (!translationMode && engine != null && query.isNotBlank()) {
            val onlySet = if (selectedFilter.isBlank()) emptySet() else setOf(selectedFilter)
            // Composition must stay total: a suggestion from a dictionary that cannot be read simply
            // does not appear, instead of throwing while Compose is building the window.
            runCatching { engine.suggest(query, only = onlySet) }.getOrDefault(emptyList())
        } else emptyList()
    }

    LaunchedEffect(query, selectedFilter, translationMode) {
        // The answer belongs to the word it was asked for, not to the next search.
        ankiNotice = null
        val current = engine
        if (current != null && query.isNotBlank()) {
            val onlySet = if (selectedFilter.isBlank()) emptySet() else setOf(selectedFilter)
            val state = withContext(Dispatchers.Default) {
                // Guarded for the same reason as the result window: a dictionary closed or replaced
                // mid-query would otherwise escape as a fatal Compose coroutine exception.
                DictLookup.run(query, onlySet) { text, only ->
                    if (translationMode) {
                        status = DesktopStrings.dictIndexPreparing
                        val found = current.searchTranslation(text, only = only) { progress ->
                            if (progress.building) {
                                status = "${DesktopStrings.dictIndexPreparing} ${(progress.fraction * 100).toInt()}%"
                            }
                        }
                        status = ""
                        found
                    } else {
                        status = ""
                        current.search(text, only = only)
                    }
                }
            }
            results = state.matches
            lookupError = state.error
        } else {
            results = emptyList()
            status = ""
            lookupError = null
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

        // The tray or a hotkey can open this while another app is active; KWin otherwise keeps it in
        // the taskbar, which reads as «abriu minimizado». Keyed on the request so asking again while
        // the window is minimised brings it back.
        LaunchedEffect(activation) {
            activateOnOpen(window)
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

                    val sent = ankiNotice
                    if (sent != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = sent,
                            style = MaterialTheme.typography.caption,
                            color = palette.warning
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (engine == null) {
                        Text(
                            DesktopStrings.noDictsWarning,
                            color = MaterialTheme.colors.error,
                            style = MaterialTheme.typography.caption
                        )
                    } else if (lookupError != null) {
                        Text(
                            DesktopStrings.lookupFailed(lookupError!!),
                            color = MaterialTheme.colors.error,
                            style = MaterialTheme.typography.caption
                        )
                    } else if (results.isEmpty() && query.isNotBlank() && !translationMode && selectedFilter.isNotBlank()) {
                        Text(
                            DesktopStrings.selectedDictHasNoEntry(selectedFilter),
                            color = MaterialTheme.colors.secondary,
                            style = MaterialTheme.typography.caption
                        )
                    } else {
                        SelectionContainer {
                            ScrollableLazyColumn(
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

                                            // One word, one card: this window knows no sentence.
                                            if (settings.showAnkiButton) {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                TextButton(
                                                    onClick = {
                                                        scope.launch {
                                                            val notice = withContext(Dispatchers.IO) {
                                                                sendToAnki(settings, wordNote(match))
                                                            }
                                                            ankiNotice = notice.text
                                                        }
                                                    },
                                                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        DesktopStrings.ankiAddWordShort,
                                                        fontSize = 11.sp,
                                                        color = palette.warning
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
    }
}
