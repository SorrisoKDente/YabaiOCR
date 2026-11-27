package dev.yabai.ocr.desktop

import dev.yabai.ocr.core.anki.AnkiNote
import dev.yabai.ocr.desktop.i18n.DesktopStrings

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.rememberWindowState
import dev.yabai.ocr.core.dict.*
import dev.yabai.ocr.core.lens.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
    /**
     * Returns to the full-screen picker this result came from, with the same screenshot, so another
     * paragraph can be clicked without capturing the screen again — the «trechos» mode of the phone
     * app. Null when this result did not come from the picker (a hand-drawn crop has nothing to go
     * back to).
     */
    onBackToPicker: (() -> Unit)? = null,
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
    var ankiNotice by remember { mutableStateOf<String?>(null) }
    var ankiUndo by remember { mutableStateOf<Long?>(null) }
    val scope = rememberCoroutineScope()

    // D103: the result opens where the last one was left, and only while a monitor still contains
    // that corner. Otherwise it centres itself, as a window that never moved.
    val screens = remember { screenBoundsNow() }
    val windowState = rememberWindowState(
        width = 860.dp,
        height = 700.dp,
        position = rememberedWindowCorner(settings.resultWindowX, settings.resultWindowY, screens)
            ?.let { WindowPosition(it.x.dp, it.y.dp) }
            ?: WindowPosition(Alignment.Center),
    )

    // Written whenever the window moves — including the move the compositor makes when centring an
    // aligned position, which is the corner the next result reopens at.
    LaunchedEffect(windowState.position) {
        val absolute = windowState.position as? WindowPosition.Absolute ?: return@LaunchedEffect
        settings.resultWindowX = absolute.x.value.toInt()
        settings.resultWindowY = absolute.y.value.toInt()
    }
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

    // Names only: opening each compiled dictionary here read every key index just to print its
    // title, which delayed the window the user is waiting for (D81).
    val installedDictTitles = remember(settings) { DesktopDictLoader.installedTitles(settings) }

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

        // Opened from a hotkey while another app had focus: without this KWin leaves the window in
        // the taskbar and the result looks minimised. Keyed on the capture, not on the window's
        // creation: a window the user minimised comes back when the next result is shown in it.
        LaunchedEffect(croppedImage, initialText) {
            activateOnOpen(window)
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
                        Column(modifier = Modifier.weight(1f)) {
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

                            // Anki: the sentence, with the first matched word in bold
                            if (settings.showAnkiButton && recognizedText.isNotBlank()) {
                                Button(
                                    onClick = {
                                        val note = AnkiNote.sentence(recognizedText, matches)
                                        scope.launch {
                                            val result = withContext(Dispatchers.IO) {
                                                AnkiSender(settings).send(note)
                                            }
                                            ankiNotice = when (result.status) {
                                                AnkiSendStatus.ADDED -> {
                                                    ankiUndo = result.noteId
                                                    DesktopStrings.ankiAdded(settings.ankiDeck)
                                                }
                                                AnkiSendStatus.ALREADY_IN_ANKI -> {
                                                    // Nothing was created, so there is nothing to undo.
                                                    ankiUndo = null
                                                    DesktopStrings.ankiAlreadyThere
                                                }
                                                // The card is already in the queue; saying so is the
                                                // difference between «nothing happened» and «it is saved».
                                                AnkiSendStatus.QUEUED -> {
                                                    ankiUndo = null
                                                    DesktopStrings.ankiQueued(result.detail)
                                                }
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF8C5A2E)),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(DesktopStrings.ankiAddSentence, fontSize = 11.sp, color = Color.White, maxLines = 1, softWrap = false)
                                }
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

                            // Trechos: back to the picker, same capture, another paragraph
                            if (onBackToPicker != null) {
                                OutlinedButton(
                                    onClick = {
                                        // Out of the picture before the picker opens over it.
                                        // `WindowState` has no visibility to set, and the window is
                                        // torn down right after (the result state is cleared), so the
                                        // AWT window is the one to hide here.
                                        window.isVisible = false
                                        scope.launch {
                                            delay(RESULT_WINDOW_HIDE_DELAY_MS)
                                            onBackToPicker()
                                        }
                                    },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF8AB4FF)),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(DesktopStrings.btnSnippets, fontSize = 11.sp, maxLines = 1, softWrap = false)
                                }
                            }

                            // Nova Seleção
                            OutlinedButton(
                                onClick = {
                                    // The capture happens immediately, and a window still on screen
                                    // would be in the screenshot and over the text underneath it —
                                    // that is why «new crop» could not crop what the result covered.
                                    window.isVisible = false
                                    scope.launch {
                                        delay(RESULT_WINDOW_HIDE_DELAY_MS)
                                        onClose()
                                        onNewSelectionRequested()
                                    }
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(DesktopStrings.btnCropAgain, fontSize = 11.sp, maxLines = 1, softWrap = false)
                            }
                        }
                    }

                    val sent = ankiNotice
                    if (sent != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = sent,
                                style = MaterialTheme.typography.caption,
                                color = Color(0xFF8FBF7A)
                            )
                            val undoId = ankiUndo
                            if (undoId != null && undoId > 0) {
                                TextButton(
                                    onClick = {
                                        scope.launch {
                                            val removed = withContext(Dispatchers.IO) {
                                                runCatching {
                                                    AnkiConnect(settings.ankiUrl).deleteNote(undoId)
                                                }.isSuccess
                                            }
                                            ankiNotice = if (removed) {
                                                DesktopStrings.ankiUndone
                                            } else {
                                                DesktopStrings.ankiUndoFailed
                                            }
                                            ankiUndo = null
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                ) {
                                    Text(DesktopStrings.btnUndo, fontSize = 11.sp, color = Color(0xFF8AB4FF))
                                }
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
                            ScrollableLazyColumn(
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
                                            ScrollableLazyColumn(
                                                modifier = Modifier.fillMaxSize(),
                                                verticalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                items(matches) { match ->
                                                    DictionaryMatchCard(
                                                        match = match,
                                                        showAnki = settings.showAnkiButton,
                                                        onAnki = { word ->
                                                            val note = AnkiNote.word(word, recognizedText)
                                                            scope.launch {
                                                                val result = withContext(Dispatchers.IO) {
                                                                    AnkiSender(settings).send(note)
                                                                }
                                                                ankiNotice = when (result.status) {
                                                                    AnkiSendStatus.ADDED -> {
                                                                        ankiUndo = result.noteId
                                                                        DesktopStrings.ankiAdded(settings.ankiDeck)
                                                                    }
                                                                    AnkiSendStatus.ALREADY_IN_ANKI -> {
                                                                        ankiUndo = null
                                                                        DesktopStrings.ankiAlreadyThere
                                                                    }
                                                                    AnkiSendStatus.QUEUED -> {
                                                                        ankiUndo = null
                                                                        DesktopStrings.ankiQueued(result.detail)
                                                                    }
                                                                }
                                                            }
                                                        }
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

@Composable
private fun DictionaryMatchCard(
    match: TokenMatch,
    showAnki: Boolean = false,
    onAnki: (TokenMatch) -> Unit = {},
) {
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

            dictionaryFormLine(match)?.let { line ->
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = line,
                    style = MaterialTheme.typography.caption,
                    color = Color(0xFF81D4FA),
                )
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

            if (showAnki) {
                Spacer(modifier = Modifier.height(4.dp))
                TextButton(
                    onClick = { onAnki(match) },
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 2.dp)
                ) {
                    Text(DesktopStrings.ankiAddWord, fontSize = 11.sp, color = Color(0xFFE5A93C))
                }
            }
        }
    }
}

/**
 * How long the result window stays hidden before the screen is captured again.
 *
 * The compositor needs a frame or two to take the window off screen; capturing sooner would save the
 * window into the screenshot — and, on Wayland, the window is still there for the pixel the crop
 * would have to see through.
 */
private const val RESULT_WINDOW_HIDE_DELAY_MS = 180L
