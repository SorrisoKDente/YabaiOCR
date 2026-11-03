package dev.yabai.ocr.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import dev.yabai.ocr.core.dict.*

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

    // Load dictionaries from configured dictionary directory
    val engine = remember {
        try {
            val dictDir = settings.dictDirectory()
            if (dictDir.exists() && dictDir.isDirectory) {
                val dicts = dictDir.listFiles()?.filter { it.extension == "yabai" || it.isDirectory }?.mapNotNull { file ->
                    runCatching { CompactDictionary.open(file) }.getOrNull()
                }.orEmpty()
                LookupEngine(dicts)
            } else null
        } catch (e: Exception) {
            null
        }
    }

    LaunchedEffect(query) {
        if (engine != null && query.isNotBlank()) {
            results = engine.search(query)
        } else {
            results = emptyList()
        }
    }

    Window(
        onCloseRequest = onClose,
        title = "Yabai OCR - Busca Rápida",
        state = windowState
    ) {
        LaunchedEffect(Unit) {
            window.iconImage = AppIconGenerator.createWindowImage(64)
        }

        MaterialTheme(
            colors = darkColors(
                background = Color(0xFF12141A),
                surface = Color(0xFF1B1E28),
                primary = Color(0xFF4C78FF),
                onPrimary = Color.White,
                onBackground = Color.White,
                onSurface = Color.White
            )
        ) {
            Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF12141A)) {
                Column(modifier = Modifier.padding(14.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Pesquisar palavra, kanji ou leitura...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            textColor = Color.White,
                            focusedBorderColor = MaterialTheme.colors.primary,
                            unfocusedBorderColor = Color(0xFF2E3344)
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    if (engine == null) {
                        Text(
                            "Nenhum dicionário encontrado na pasta de dados.\nAdicione arquivos .yabai para consultar.",
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
                                        backgroundColor = Color(0xFF1B1E28),
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
                                                    style = MaterialTheme.typography.subtitle1.copy(color = Color.White)
                                                )
                                                if (match.dictionaryTitle.isNotBlank()) {
                                                    Text(
                                                        text = match.dictionaryTitle,
                                                        style = MaterialTheme.typography.caption,
                                                        color = Color(0xFFA0A6B8)
                                                    )
                                                }
                                            }

                                            if (match.entry.tags.isNotBlank()) {
                                                Text(
                                                    text = match.entry.tags,
                                                    style = MaterialTheme.typography.caption,
                                                    color = Color(0xFFE5A93C)
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(4.dp))

                                            Text(
                                                text = match.entry.definition,
                                                style = MaterialTheme.typography.body2,
                                                color = Color.White
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
