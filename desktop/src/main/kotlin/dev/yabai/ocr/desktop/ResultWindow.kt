package dev.yabai.ocr.desktop

import dev.yabai.ocr.desktop.i18n.DesktopStrings

import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.rememberWindowState
import dev.yabai.ocr.core.dict.*
import dev.yabai.ocr.core.lens.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.image.BufferedImage

@Composable
fun ResultWindow(
    croppedImage: BufferedImage,
    initialText: String? = null,
    onClose: () -> Unit,
    onNewSelectionRequested: () -> Unit,
) {
    val settings = remember { DesktopSettings() }

    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var recognizedText by remember { mutableStateOf("") }
    var elapsedMs by remember { mutableStateOf(0L) }
    // The engine that actually answered, not a fixed label: with «Automático» the fallback decides.
    var engineUsed by remember { mutableStateOf<DesktopEngineChoice?>(null) }
    var matches by remember { mutableStateOf<List<TokenMatch>>(emptyList()) }
    var lookupError by remember { mutableStateOf<String?>(null) }
    var selectedFilter by remember { mutableStateOf(settings.dictionaryFilter) }
    var showImageToggle by remember { mutableStateOf(settings.showImage) }
    var aiNotice by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val windowState = rememberWindowState(width = 860.dp, height = 700.dp)
    val imageBitmap = remember { croppedImage.toComposeImageBitmap() }
    val isVerticalText = croppedImage.height > croppedImage.width

    // Load LookupEngine from configured dictionary directory
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

    // Run OCR asynchronously
    LaunchedEffect(croppedImage, initialText) {
        isLoading = true
        errorMessage = null

        // A paragraph chosen in the full-screen picker already came back recognised, and it is the
        // exact text the user clicked. Recognising the crop again would cost a second round trip
        // and could return a different reading of the same ink.
        if (initialText != null) {
            recognizedText = initialText
            elapsedMs = 0
            isLoading = false
            return@LaunchedEffect
        }

        withContext(Dispatchers.IO) {
            try {
                // The engine is the one chosen in the settings: this window used to call Lens
                // directly, so «Somente Manga-OCR» was a setting nothing read.
                val start = System.currentTimeMillis()
                val answer = DesktopOcr.recognize(croppedImage, settings)
                val duration = System.currentTimeMillis() - start
                val result = answer.result

                val fullText = result.joinLines()
                recognizedText = fullText
                elapsedMs = duration
                engineUsed = answer.engine

                if (fullText.isNotBlank() && engine != null) {
                    val onlySet = if (selectedFilter.isBlank()) emptySet() else setOf(selectedFilter)
                    val found = engine.lookup(fullText, only = onlySet)
                    matches = found
                } else {
                    matches = emptyList()
                }
            } catch (e: Exception) {
                errorMessage = e.message ?: DesktopStrings.ocrFailedGeneric
            } finally {
                isLoading = false
            }
        }
    }

    // Re-run lookup when recognized text or dictionary filter changes
    LaunchedEffect(recognizedText, selectedFilter, engine) {
        val current = engine
        if (recognizedText.isNotBlank() && current != null) {
            val onlySet = if (selectedFilter.isBlank()) emptySet() else setOf(selectedFilter)
            // Off the UI thread and guarded: switching the dictionary maps that `.yabai` again, and a
            // dictionary that fails here must show a notice, not kill the window's coroutine.
            val state = withContext(Dispatchers.Default) {
                DictLookup.run(recognizedText, onlySet) { text, only ->
                    current.lookup(text, only = only)
                }
            }
            matches = state.matches
            lookupError = state.error
        } else {
            matches = emptyList()
            lookupError = null
        }
    }

    Window(
        onCloseRequest = onClose,
        title = DesktopStrings.resultWindowTitle,
        state = windowState
    ) {
        LaunchedEffect(Unit) {
            window.iconImage = AppIconGenerator.createWindowImage(64)
        }

        YabaiDesktopTheme(darkTheme = remember { DesktopSettings().darkTheme }) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color(0xFF12141A)
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(18.dp)) {
                    // --- Header Bar with Compact Action Buttons ---
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = DesktopStrings.resultTitle,
                                style = MaterialTheme.typography.h6.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                            if (!isLoading && recognizedText.isNotBlank()) {
                                Text(
                                    text = if (initialText != null) {
                                        DesktopStrings.resultFromPicker
                                    } else {
                                        DesktopStrings.resultRecognizedIn(
                                            elapsedMs,
                                            DesktopOcr.label(engineUsed ?: DesktopEngineChoice.AUTO),
                                        )
                                    },
                                    style = MaterialTheme.typography.caption,
                                    color = Color(0xFF81C784)
                                )
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Imagem Toggle
                            Button(
                                onClick = { showImageToggle = !showImageToggle },
                                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF2E3344)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    if (showImageToggle) DesktopStrings.btnHideImage else DesktopStrings.btnShowImage,
                                    fontSize = 11.sp, color = Color.White, maxLines = 1, softWrap = false
                                )
                            }

                            // Copiar
                            Button(
                                onClick = {
                                    if (recognizedText.isNotBlank()) {
                                        val selection = StringSelection(recognizedText)
                                        Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
                                    }
                                },
                                enabled = recognizedText.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF2E3344)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(DesktopStrings.btnCopyText, fontSize = 11.sp, color = Color.White, maxLines = 1, softWrap = false)
                            }

                            // Perguntar à IA
                            if (settings.showAiButton) {
                                Button(
                                    onClick = {
                                        if (recognizedText.isNotBlank()) {
                                            val textToAsk = recognizedText
                                            scope.launch {
                                                // Opening a browser can block on the launcher; the
                                                // window must not freeze while it happens.
                                                val opened = withContext(Dispatchers.IO) {
                                                    DesktopUrlOpener().open(settings.buildAiUrl(textToAsk))
                                                }
                                                if (!opened) {
                                                    // Keep the URL reachable instead of losing it: the
                                                    // user can paste it in any browser.
                                                    val selection = StringSelection(settings.buildAiUrl(textToAsk))
                                                    Toolkit.getDefaultToolkit().systemClipboard
                                                        .setContents(selection, selection)
                                                }
                                                aiNotice = if (opened) null else DesktopStrings.aiLinkOpenFailed
                                            }
                                        }
                                    },
                                    enabled = recognizedText.isNotBlank(),
                                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF3D6BF3)),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(DesktopStrings.btnAskAiCompact, fontSize = 11.sp, color = Color.White, maxLines = 1, softWrap = false)
                                }
                            }

                            // Nova Seleção
                            OutlinedButton(
                                onClick = {
                                    onClose()
                                    onNewSelectionRequested()
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(DesktopStrings.btnCropAgain, fontSize = 11.sp, maxLines = 1, softWrap = false)
                            }
                        }
                    }

                    val notice = aiNotice
                    if (notice != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = notice,
                            style = MaterialTheme.typography.caption,
                            color = Color(0xFFE5A93C)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (isLoading) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = MaterialTheme.colors.primary)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(DesktopStrings.recognizesTextProgress, color = Color(0xFFA0A6B8))
                            }
                        }
                    } else if (errorMessage != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            backgroundColor = Color(0xFF3A1C1C),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(DesktopStrings.ocrFailed, style = MaterialTheme.typography.subtitle1, color = Color(0xFFFFB4AB))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(errorMessage.orEmpty(), style = MaterialTheme.typography.body2, color = Color.White)
                            }
                        }
                    } else {
                        // --- Split Content: Left (Editable Text & Image) | Right (Dictionary Cards) ---
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // --- Left Column: Scrollable Editable Text & Image ---
                            LazyColumn(
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                item {
                                    if (isVerticalText && showImageToggle) {
                                        // Vertical text layout: Image on the left of text field
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Card(
                                                modifier = Modifier.width(130.dp).height(260.dp),
                                                backgroundColor = Color(0xFF1B1E28),
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier.fillMaxSize().padding(4.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Image(
                                                        bitmap = imageBitmap,
                                                        contentDescription = "Recorte Vertical",
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                }
                                            }

                                            Card(
                                                modifier = Modifier.weight(1f),
                                                backgroundColor = Color(0xFF1B1E28),
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Column(modifier = Modifier.padding(16.dp)) {
                                                    Text(
                                                        text = DesktopStrings.resultEditLabel,
                                                        style = MaterialTheme.typography.caption,
                                                        color = Color(0xFFA0A6B8)
                                                    )
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    OutlinedTextField(
                                                        value = recognizedText,
                                                        onValueChange = { recognizedText = it },
                                                        modifier = Modifier.fillMaxWidth(),
                                                        textStyle = TextStyle(
                                                            fontSize = 18.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = Color.White,
                                                            lineHeight = 26.sp
                                                        ),
                                                        colors = TextFieldDefaults.outlinedTextFieldColors(
                                                            textColor = Color.White,
                                                            focusedBorderColor = MaterialTheme.colors.primary,
                                                            unfocusedBorderColor = Color(0xFF2E3344)
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                    } else {
                                        // Standard layout (Horizontal text or image toggled off)
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            if (!isVerticalText && showImageToggle) {
                                                Card(
                                                    modifier = Modifier.fillMaxWidth().height(150.dp),
                                                    backgroundColor = Color(0xFF1B1E28),
                                                    shape = RoundedCornerShape(12.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier.fillMaxSize().padding(4.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Image(
                                                            bitmap = imageBitmap,
                                                            contentDescription = "Recorte Horizontal",
                                                            modifier = Modifier.fillMaxSize()
                                                        )
                                                    }
                                                }
                                            }

                                            Card(
                                                modifier = Modifier.fillMaxWidth(),
                                                backgroundColor = Color(0xFF1B1E28),
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Column(modifier = Modifier.padding(16.dp)) {
                                                    Text(
                                                        text = DesktopStrings.resultEditLabel,
                                                        style = MaterialTheme.typography.caption,
                                                        color = Color(0xFFA0A6B8)
                                                    )
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    OutlinedTextField(
                                                        value = recognizedText,
                                                        onValueChange = { recognizedText = it },
                                                        modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp, max = 240.dp),
                                                        textStyle = TextStyle(
                                                            fontSize = 18.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = Color.White,
                                                            lineHeight = 26.sp
                                                        ),
                                                        colors = TextFieldDefaults.outlinedTextFieldColors(
                                                            textColor = Color.White,
                                                            focusedBorderColor = MaterialTheme.colors.primary,
                                                            unfocusedBorderColor = Color(0xFF2E3344)
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // --- Right Column: Dictionary Matches & Word Inspector ---
                            Card(
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                backgroundColor = Color(0xFF1B1E28),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = DesktopStrings.entriesAndDictHeader,
                                            style = MaterialTheme.typography.subtitle1.copy(fontWeight = FontWeight.Bold),
                                            color = Color(0xFF5D84FF)
                                        )

                                        var expandedFilter by remember { mutableStateOf(false) }
                                        Box {
                                            OutlinedButton(
                                                onClick = { expandedFilter = true },
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                                            ) {
                                                Text(if (selectedFilter.isBlank()) DesktopStrings.allDictsLabel else "📖 $selectedFilter", fontSize = 11.sp)
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

                                    Spacer(modifier = Modifier.height(8.dp))

                                    if (lookupError != null) {
                                        Text(
                                            text = DesktopStrings.lookupFailed(lookupError!!),
                                            style = MaterialTheme.typography.body2,
                                            color = Color(0xFFE57373)
                                        )
                                    } else if (matches.isEmpty()) {
                                        Text(
                                            text = if (engine == null)
                                                DesktopStrings.noDictsInFolder(settings.dictDirectory().absolutePath)
                                            else if (selectedFilter.isNotBlank())
                                                // The dictionary the user picked simply has no entry for
                                                // this word; that is not an error, and the other
                                                // dictionaries are one click away.
                                                DesktopStrings.selectedDictHasNoEntry(selectedFilter)
                                            else
                                                DesktopStrings.noEntriesFoundForSentence,
                                            style = MaterialTheme.typography.body2,
                                            color = Color(0xFFA0A6B8)
                                        )
                                    } else {
                                        SelectionContainer {
                                            LazyColumn(
                                                modifier = Modifier.fillMaxSize(),
                                                verticalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                items(matches) { match ->
                                                    DictionaryMatchCard(match = match)
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

@Composable
private fun DictionaryMatchCard(match: TokenMatch) {
    val entry = match.entry

    Card(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = Color(0xFF141620),
        shape = RoundedCornerShape(8.dp),
        elevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = entry.term,
                        style = MaterialTheme.typography.h6.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF5D84FF)
                    )
                    if (entry.reading.isNotBlank() && entry.reading != entry.term) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "【${entry.reading}】",
                            style = MaterialTheme.typography.body2,
                            color = Color.White
                        )
                    }
                }
                if (match.dictionaryTitle.isNotBlank()) {
                    Text(
                        text = match.dictionaryTitle,
                        style = MaterialTheme.typography.caption,
                        color = Color(0xFFA0A6B8)
                    )
                }
            }

            if (entry.tags.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = entry.tags,
                    style = MaterialTheme.typography.caption,
                    color = Color(0xFFE5A93C)
                )
            }

            if (match.reasons.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = DesktopStrings.flexionPrefix + match.reasons.joinToString(" → "),
                    style = MaterialTheme.typography.caption,
                    color = Color(0xFFA0A6B8)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = entry.definition,
                style = MaterialTheme.typography.body2.copy(lineHeight = 18.sp),
                color = Color.White
            )
        }
    }
}
