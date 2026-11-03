package dev.yabai.ocr.desktop

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
import dev.yabai.ocr.core.dict.*
import dev.yabai.ocr.core.lens.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URI
import javax.imageio.ImageIO

@Composable
fun ResultWindow(
    croppedImage: BufferedImage,
    onClose: () -> Unit,
    onNewSelectionRequested: () -> Unit,
    onOpenFullDictRequested: (String) -> Unit,
) {
    val settings = remember { DesktopSettings() }

    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var recognizedText by remember { mutableStateOf("") }
    var elapsedMs by remember { mutableStateOf(0L) }
    var matches by remember { mutableStateOf<List<TokenMatch>>(emptyList()) }
    var selectedFilter by remember { mutableStateOf(settings.dictionaryFilter) }

    val imageBitmap = remember { croppedImage.toComposeImageBitmap() }

    // Load LookupEngine from configured dictionary directory
    val dictDir = remember { settings.dictDirectory() }
    val engine = remember {
        try {
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

    val installedDictTitles = remember(dictDir) {
        val list = mutableListOf<String>()
        try {
            if (dictDir.exists() && dictDir.isDirectory) {
                dictDir.listFiles()?.forEach { file ->
                    if (file.extension == "yabai" || file.isDirectory) {
                        val dict = runCatching { CompactDictionary.open(file) }.getOrNull()
                        if (dict != null) list.add(dict.info.title)
                    }
                }
            }
        } catch (_: Exception) {}
        list.toList()
    }

    // Run OCR asynchronously
    LaunchedEffect(croppedImage) {
        isLoading = true
        errorMessage = null

        withContext(Dispatchers.IO) {
            try {
                // Convert BufferedImage to PNG EncodedImage
                val baos = ByteArrayOutputStream()
                ImageIO.write(croppedImage, "png", baos)
                val bytes = baos.toByteArray()
                val encodedImage = EncodedImage(bytes, croppedImage.width, croppedImage.height)

                val start = System.currentTimeMillis()
                val client = LensClient()
                val result = client.recognize(
                    image = encodedImage,
                    options = LensOptions(language = "ja")
                )
                val duration = System.currentTimeMillis() - start

                val fullText = result.joinLines()
                recognizedText = fullText
                elapsedMs = duration

                if (fullText.isNotBlank() && engine != null) {
                    val onlySet = if (selectedFilter.isBlank()) emptySet() else setOf(selectedFilter)
                    val found = engine.lookup(fullText, only = onlySet)
                    matches = found
                } else {
                    matches = emptyList()
                }
            } catch (e: Exception) {
                errorMessage = e.message ?: "Erro ao reconhecer a imagem."
            } finally {
                isLoading = false
            }
        }
    }

    // Re-run lookup when recognized text or dictionary filter changes
    LaunchedEffect(recognizedText, selectedFilter, engine) {
        if (recognizedText.isNotBlank() && engine != null) {
            val onlySet = if (selectedFilter.isBlank()) emptySet() else setOf(selectedFilter)
            matches = engine.lookup(recognizedText, only = onlySet)
        } else {
            matches = emptyList()
        }
    }

    Window(
        onCloseRequest = onClose,
        title = "Yabai OCR - Resultado do Reconhecimento",
        state = WindowState(width = 820.dp, height = 680.dp)
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
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color(0xFF12141A)
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(18.dp)) {
                    // --- Header Bar with Quick Action Buttons ---
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Texto Reconhecido",
                                style = MaterialTheme.typography.h6.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                            if (!isLoading && recognizedText.isNotBlank()) {
                                Text(
                                    text = "Reconhecido em ${elapsedMs}ms • Google Lens",
                                    style = MaterialTheme.typography.caption,
                                    color = Color(0xFF81C784)
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Copiar
                            Button(
                                onClick = {
                                    if (recognizedText.isNotBlank()) {
                                        val selection = StringSelection(recognizedText)
                                        Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
                                    }
                                },
                                enabled = recognizedText.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF2E3344))
                            ) {
                                Text("📋 Copiar", fontSize = 12.sp, color = Color.White)
                            }

                            // Perguntar à IA
                            if (settings.showAiButton) {
                                Button(
                                    onClick = {
                                        if (recognizedText.isNotBlank()) {
                                            val url = settings.buildAiUrl(recognizedText)
                                            runCatching { Desktop.getDesktop().browse(URI.create(url)) }
                                        }
                                    },
                                    enabled = recognizedText.isNotBlank(),
                                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF3D6BF3))
                                ) {
                                    Text("🤖 Perguntar à IA", fontSize = 12.sp, color = Color.White)
                                }
                            }

                            // Buscar no Dicionário Completo
                            Button(
                                onClick = {
                                    if (recognizedText.isNotBlank()) {
                                        onOpenFullDictRequested(recognizedText)
                                    }
                                },
                                enabled = recognizedText.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF2E3344))
                            ) {
                                Text("🔍 Dicionário", fontSize = 12.sp, color = Color.White)
                            }

                            // Nova Seleção
                            OutlinedButton(
                                onClick = {
                                    onClose()
                                    onNewSelectionRequested()
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                            ) {
                                Text("✂️ Recortar", fontSize = 12.sp)
                            }
                        }
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
                                Text("Reconhecendo texto em japonês...", color = Color(0xFFA0A6B8))
                            }
                        }
                    } else if (errorMessage != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            backgroundColor = Color(0xFF3A1C1C),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Falha no OCR", style = MaterialTheme.typography.subtitle1, color = Color(0xFFFFB4AB))
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
                            // --- Left Column: Editable Recognized Text & Image ---
                            Column(
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            ) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    backgroundColor = Color(0xFF1B1E28),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            text = "Texto Reconhecido (edite ou selecione para ajustar):",
                                            style = MaterialTheme.typography.caption,
                                            color = Color(0xFFA0A6B8)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        OutlinedTextField(
                                            value = recognizedText,
                                            onValueChange = { recognizedText = it },
                                            modifier = Modifier.fillMaxWidth(),
                                            textStyle = TextStyle(
                                                fontSize = 20.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color.White,
                                                lineHeight = 28.sp
                                            ),
                                            colors = TextFieldDefaults.outlinedTextFieldColors(
                                                textColor = Color.White,
                                                focusedBorderColor = MaterialTheme.colors.primary,
                                                unfocusedBorderColor = Color(0xFF2E3344)
                                            )
                                        )
                                    }
                                }

                                // Image Preview if enabled
                                if (settings.showImage) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Card(
                                        modifier = Modifier.fillMaxWidth().weight(1f),
                                        backgroundColor = Color(0xFF1B1E28),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier.fillMaxSize().padding(8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Image(
                                                bitmap = imageBitmap,
                                                contentDescription = "Recorte Selecionado",
                                                modifier = Modifier.fillMaxSize()
                                            )
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
                                            text = "Verbetes e Dicionário",
                                            style = MaterialTheme.typography.subtitle1.copy(fontWeight = FontWeight.Bold),
                                            color = Color(0xFF5D84FF)
                                        )

                                        var expandedFilter by remember { mutableStateOf(false) }
                                        Box {
                                            OutlinedButton(
                                                onClick = { expandedFilter = true },
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                                            ) {
                                                Text(if (selectedFilter.isBlank()) "🌐 Todos" else "📖 $selectedFilter", fontSize = 11.sp)
                                            }
                                            DropdownMenu(
                                                expanded = expandedFilter,
                                                onDismissRequest = { expandedFilter = false }
                                            ) {
                                                DropdownMenuItem(onClick = {
                                                    selectedFilter = ""
                                                    expandedFilter = false
                                                }) {
                                                    Text("🌐 Todos os Dicionários")
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

                                    if (matches.isEmpty()) {
                                        Text(
                                            text = if (engine == null)
                                                "Nenhum dicionário instalado na pasta (${dictDir.absolutePath}) para consultar verbetes."
                                            else
                                                "Nenhum verbete encontrado para a frase no dicionário.",
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
                        color = Color.White
                    )
                    if (entry.reading.isNotBlank() && entry.reading != entry.term) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "【${entry.reading}】",
                            style = MaterialTheme.typography.body2,
                            color = Color(0xFF5D84FF)
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
                    text = "Flexão: ${match.reasons.joinToString(" → ")}",
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
