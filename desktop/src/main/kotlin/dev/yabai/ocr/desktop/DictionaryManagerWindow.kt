package dev.yabai.ocr.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import dev.yabai.ocr.core.dict.*
import java.awt.Desktop
import java.io.File
import java.net.URI
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

@Composable
fun DictionaryManagerContent(
    initialQuery: String = "",
    onBackRequested: (() -> Unit)? = null
) {
    var selectedTab by remember { mutableStateOf(0) } // 0: Search, 1: Installed, 2: Catalog

    Column(modifier = Modifier.fillMaxSize()) {
        if (onBackRequested != null) {
            Surface(
                color = Color(0xFF1B1E28),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onBackRequested,
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = Color(0xFF2E3344),
                            contentColor = Color.White
                        )
                    ) {
                        Text("◀ Voltar ao Menu Anterior", fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "Gerenciador de Dicionários",
                        style = MaterialTheme.typography.subtitle1.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }
            }
        }

        // --- Top Tabs ---
        TabRow(
            selectedTabIndex = selectedTab,
            backgroundColor = Color(0xFF1B1E28),
            contentColor = MaterialTheme.colors.primary
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("🔍 Consultar Dicionários", fontWeight = FontWeight.SemiBold) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("📚 Dicionários Instalados", fontWeight = FontWeight.SemiBold) }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text("🌐 Catálogo de Downloads", fontWeight = FontWeight.SemiBold) }
            )
        }

        Box(modifier = Modifier.fillMaxSize().padding(20.dp)) {
            when (selectedTab) {
                0 -> SearchTab(initialQuery = initialQuery)
                1 -> InstalledTab()
                2 -> CatalogTab()
            }
        }
    }
}

@Composable
fun DictionaryManagerWindow(
    initialQuery: String = "",
    onClose: () -> Unit
) {
    Window(
        onCloseRequest = onClose,
        title = "Yabai OCR - Gerenciador de Dicionários",
        state = WindowState(width = 860.dp, height = 700.dp)
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
                DictionaryManagerContent(initialQuery = initialQuery)
            }
        }
    }
}

@Composable
private fun SearchTab(initialQuery: String) {
    var query by remember { mutableStateOf(initialQuery) }
    var results by remember { mutableStateOf<List<TokenMatch>>(emptyList()) }

    val dictDir = remember { DesktopSettings().dictDirectory() }
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

    LaunchedEffect(query) {
        if (engine != null && query.isNotBlank()) {
            results = engine.search(query)
        } else {
            results = emptyList()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Pesquisar palavra, kanji, leitura (hiragana/romaji) ou tradução...") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = TextFieldDefaults.outlinedTextFieldColors(
                textColor = Color.White,
                focusedBorderColor = MaterialTheme.colors.primary,
                unfocusedBorderColor = Color(0xFF2E3344)
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (engine == null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = Color(0xFF3A1C1C),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Nenhum dicionário disponível", style = MaterialTheme.typography.subtitle1, color = Color(0xFFFFB4AB))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Coloque arquivos de dicionário (.yabai) na pasta ~/.yabai_ocr/dicts/ para realizar consultas.",
                        color = Color.White,
                        style = MaterialTheme.typography.body2
                    )
                }
            }
        } else {
            if (results.isEmpty() && query.isNotBlank()) {
                Text(
                    text = "Nenhum resultado encontrado para 「$query」",
                    color = Color(0xFFA0A6B8),
                    style = MaterialTheme.typography.body1
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(results) { match ->
                        DictionarySearchCard(match = match)
                    }
                }
            }
        }
    }
}

@Composable
private fun InstalledTab() {
    val dictDir = remember { File(System.getProperty("user.home"), ".yabai_ocr/dicts") }
    var installedFiles by remember {
        mutableStateOf(
            if (dictDir.exists()) dictDir.listFiles()?.filter { it.extension == "yabai" || it.isDirectory }.orEmpty() else emptyList()
        )
    }

    fun refreshFiles() {
        if (!dictDir.exists()) dictDir.mkdirs()
        installedFiles = dictDir.listFiles()?.filter { it.extension == "yabai" || it.isDirectory }.orEmpty()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Dicionários Locais", style = MaterialTheme.typography.h6.copy(fontWeight = FontWeight.Bold), color = Color.White)
                Text("Pasta de dados: ${dictDir.absolutePath}", style = MaterialTheme.typography.caption, color = Color(0xFFA0A6B8))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val chooser = JFileChooser()
                        chooser.dialogTitle = "Selecionar arquivo de dicionário (.yabai)"
                        chooser.fileFilter = FileNameExtensionFilter("Dicionários Yabai (.yabai)", "yabai", "zip")
                        val result = chooser.showOpenDialog(null)
                        if (result == JFileChooser.APPROVE_OPTION) {
                            val selected = chooser.selectedFile
                            if (!dictDir.exists()) dictDir.mkdirs()
                            val dest = File(dictDir, selected.name)
                            selected.copyTo(dest, overwrite = true)
                            refreshFiles()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.primary)
                ) {
                    Text("➕ Importar Arquivo")
                }

                OutlinedButton(
                    onClick = {
                        if (!dictDir.exists()) dictDir.mkdirs()
                        runCatching { Desktop.getDesktop().open(dictDir) }
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                ) {
                    Text("📁 Abrir Pasta")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (installedFiles.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = Color(0xFF1B1E28),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        "Nenhum dicionário instalado no momento.",
                        style = MaterialTheme.typography.subtitle1,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Você pode importar arquivos .yabai existentes usando o botão acima, ou navegar pela aba 'Catálogo de Downloads' para baixar os dicionários recomendados.",
                        style = MaterialTheme.typography.body2,
                        color = Color(0xFFA0A6B8)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(installedFiles) { file ->
                    val sizeMb = "%.1f MB".format(file.length().toDouble() / (1024 * 1024))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = Color(0xFF1B1E28),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(file.nameWithoutExtension, style = MaterialTheme.typography.subtitle1.copy(fontWeight = FontWeight.Bold), color = Color.White)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("Arquivo: ${file.name} • Tamanho: $sizeMb", style = MaterialTheme.typography.caption, color = Color(0xFFA0A6B8))
                            }

                            Button(
                                onClick = {
                                    file.delete()
                                    refreshFiles()
                                },
                                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF3A1C1C), contentColor = Color(0xFFFFB4AB))
                            ) {
                                Text("Remover")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogTab() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "Dicionários Recomendados",
                style = MaterialTheme.typography.h6.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Baixe arquivos de dicionário desenvolvidos para leitores de japonês e coloque-os na pasta de dados (~/.yabai_ocr/dicts/).",
                style = MaterialTheme.typography.body2,
                color = Color(0xFFA0A6B8)
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        items(DesktopDictionaryCatalog.entries) { entry ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = Color(0xFF1B1E28),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(entry.name, style = MaterialTheme.typography.subtitle1.copy(fontWeight = FontWeight.Bold), color = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = Color(0xFF2E3344),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = entry.language.uppercase(),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    fontSize = 10.sp,
                                    color = Color(0xFF5D84FF)
                                )
                            }
                        }

                        Text(entry.sizeLabel, style = MaterialTheme.typography.caption, color = Color(0xFFA0A6B8))
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(entry.description, style = MaterialTheme.typography.body2, color = Color(0xFFA0A6B8))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Fonte: ${entry.attribution}", style = MaterialTheme.typography.caption, color = Color(0xFF70778B))

                    Spacer(modifier = Modifier.height(10.dp))

                    val downloadUrl = entry.url ?: entry.indexUrl
                    if (downloadUrl != null) {
                        Button(
                            onClick = {
                                runCatching { Desktop.getDesktop().browse(URI.create(downloadUrl)) }
                            },
                            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF2E3344), contentColor = Color.White)
                        ) {
                            Text("🌐 Abrir Download no Navegador")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DictionarySearchCard(match: TokenMatch) {
    val entry = match.entry

    Card(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = Color(0xFF1B1E28),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
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
                            style = MaterialTheme.typography.body1,
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
                Text(entry.tags, style = MaterialTheme.typography.caption, color = Color(0xFFE5A93C))
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
