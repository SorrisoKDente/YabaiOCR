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
import androidx.compose.ui.window.WindowState
import dev.yabai.ocr.core.dict.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
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
    var installedRefreshKey by remember { mutableStateOf(0) }

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
                0 -> SearchTab(initialQuery = initialQuery, refreshKey = installedRefreshKey)
                1 -> InstalledTab(refreshKey = installedRefreshKey, onRefresh = { installedRefreshKey++ })
                2 -> CatalogTab(onInstalledChanged = { installedRefreshKey++ })
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
private fun SearchTab(initialQuery: String, refreshKey: Int) {
    var query by remember { mutableStateOf(initialQuery) }
    var results by remember { mutableStateOf<List<TokenMatch>>(emptyList()) }
    var selectedFilter by remember { mutableStateOf("") }

    val dictDir = remember(refreshKey) { DesktopSettings().dictDirectory() }
    val engine = remember(refreshKey) {
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

    val installedDictTitles = remember(refreshKey) {
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

    val suggestions = remember(query, engine, selectedFilter) {
        if (engine != null && query.isNotBlank()) {
            val onlySet = if (selectedFilter.isBlank()) emptySet() else setOf(selectedFilter)
            engine.suggest(query, only = onlySet)
        } else emptyList()
    }

    LaunchedEffect(query, refreshKey, selectedFilter) {
        if (engine != null && query.isNotBlank()) {
            val onlySet = if (selectedFilter.isBlank()) emptySet() else setOf(selectedFilter)
            results = engine.search(query, only = onlySet)
        } else {
            results = emptyList()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Pesquisar nos Dicionários", style = MaterialTheme.typography.subtitle1.copy(fontWeight = FontWeight.Bold), color = Color.White)

            var expandedFilter by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(
                    onClick = { expandedFilter = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                ) {
                    Text(if (selectedFilter.isBlank()) "🌐 Todos os Dicionários" else "📖 $selectedFilter", fontSize = 11.sp)
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

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Pesquisar palavra, kanji, leitura (hiragana/romaji) ou tradução...") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    textColor = Color.White,
                    focusedBorderColor = MaterialTheme.colors.primary,
                    unfocusedBorderColor = Color(0xFF2E3344)
                )
            )

            if (query.isNotBlank()) {
                Button(
                    onClick = { query = "" },
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Color(0xFF2E3344),
                        contentColor = Color.White
                    )
                ) {
                    Text("❌ Apagar", fontSize = 11.sp)
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
                        backgroundColor = Color(0xFF2E3344),
                        contentColor = Color.White
                    )
                ) {
                    Text("📋 Colar", fontSize = 11.sp)
                }
            }
        }

        if (suggestions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                suggestions.take(5).forEach { suggestion ->
                    Surface(
                        modifier = Modifier.clickable { query = suggestion },
                        color = Color(0xFF232736),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = suggestion,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.caption.copy(color = Color(0xFF81D4FA))
                        )
                    }
                }
            }
        }

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
                        "Coloque arquivos de dicionário (.yabai) na pasta ${dictDir.absolutePath} para realizar consultas.",
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
                SelectionContainer {
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
}

@Composable
private fun InstalledTab(refreshKey: Int, onRefresh: () -> Unit) {
    val dictDir = remember(refreshKey) { DesktopSettings().dictDirectory() }
    var installedFiles by remember(refreshKey) {
        mutableStateOf(
            if (dictDir.exists()) dictDir.listFiles()?.filter { it.extension == "yabai" || it.isDirectory }.orEmpty() else emptyList()
        )
    }

    fun refreshFiles() {
        if (!dictDir.exists()) dictDir.mkdirs()
        installedFiles = dictDir.listFiles()?.filter { it.extension == "yabai" || it.isDirectory }.orEmpty()
        onRefresh()
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
                        chooser.dialogTitle = "Selecionar arquivo de dicionário (.yabai ou .zip)"
                        chooser.fileFilter = FileNameExtensionFilter("Dicionários Yabai / Yomitan (.yabai, .zip)", "yabai", "zip")
                        val result = chooser.showOpenDialog(null)
                        if (result == JFileChooser.APPROVE_OPTION) {
                            val selected = chooser.selectedFile
                            if (!dictDir.exists()) dictDir.mkdirs()
                            if (selected.extension.lowercase() == "zip") {
                                val targetDir = File(dictDir, "${selected.nameWithoutExtension}.yabai")
                                runCatching {
                                    val compiler = DictionaryCompiler(selected, targetDir, selected.nameWithoutExtension)
                                    compiler.compile()
                                }
                            } else {
                                val dest = File(dictDir, selected.name)
                                selected.copyTo(dest, overwrite = true)
                            }
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

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (installedFiles.isEmpty()) {
                item {
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
                                "Você pode importar arquivos .yabai ou .zip existentes usando o botão acima, ou navegar pela aba 'Catálogo de Downloads' para baixar os dicionários recomendados.",
                                style = MaterialTheme.typography.body2,
                                color = Color(0xFFA0A6B8)
                            )
                        }
                    }
                }
            } else {
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
                                    file.deleteRecursively()
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

            item {
                Spacer(modifier = Modifier.height(8.dp))
                CreditsAndLicensesSection(installedFiles)
            }
        }
    }
}

@Composable
private fun CatalogTab(onInstalledChanged: () -> Unit) {
    val dictDir = remember { DesktopSettings().dictDirectory() }

    var downloadingId by remember { mutableStateOf<String?>(null) }
    var downloadProgressText by remember { mutableStateOf("") }
    var downloadProgressFraction by remember { mutableStateOf(0f) }
    var downloadError by remember { mutableStateOf<String?>(null) }

    val coroutineScope = rememberCoroutineScope()

    val installedNames = remember(downloadingId) {
        if (dictDir.exists()) {
            dictDir.listFiles()?.map { it.nameWithoutExtension.lowercase() }?.toSet().orEmpty()
        } else emptySet()
    }

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
                "Baixe e instale dicionários diretamente no aplicativo sem sair. Eles serão salvos e ativados na pasta de dados: ${dictDir.absolutePath}",
                style = MaterialTheme.typography.body2,
                color = Color(0xFFA0A6B8)
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (downloadError != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = Color(0xFF3A1C1C),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Erro no Download", style = MaterialTheme.typography.subtitle2, color = Color(0xFFFFB4AB))
                        Text(downloadError.orEmpty(), style = MaterialTheme.typography.caption, color = Color.White)
                    }
                }
            }
        }

        items(DesktopDictionaryCatalog.entries) { entry ->
            val isInstalled = installedNames.contains(entry.id.lowercase()) || installedNames.contains(entry.name.lowercase())
            val isDownloading = downloadingId == entry.id

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

                    if (isDownloading) {
                        Column {
                            LinearProgressIndicator(
                                progress = downloadProgressFraction,
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colors.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(downloadProgressText, style = MaterialTheme.typography.caption, color = Color.White)
                        }
                    } else if (isInstalled) {
                        Surface(
                            color = Color(0xFF1B382B),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "✓ Instalado",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.body2.copy(fontWeight = FontWeight.SemiBold),
                                color = Color(0xFF81C784)
                            )
                        }
                    } else {
                        Button(
                            onClick = {
                                downloadingId = entry.id
                                downloadError = null
                                coroutineScope.launch(Dispatchers.IO) {
                                    try {
                                        downloadAndInstallDictionary(
                                            entry = entry,
                                            dictDir = dictDir,
                                            onProgress = { text, fraction ->
                                                downloadProgressText = text
                                                downloadProgressFraction = fraction
                                            }
                                        )
                                        onInstalledChanged()
                                    } catch (e: Exception) {
                                        downloadError = e.message ?: "Erro ao baixar o dicionário."
                                    } finally {
                                        downloadingId = null
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.primary, contentColor = Color.White)
                        ) {
                            Text("⬇️ Baixar e Instalar no App")
                        }
                    }
                }
            }
        }
    }
}

private fun downloadAndInstallDictionary(
    entry: DesktopDictionaryCatalog.Entry,
    dictDir: File,
    onProgress: (String, Float) -> Unit
) {
    if (!dictDir.exists()) dictDir.mkdirs()

    val client = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    // 1. Resolve URL if indexUrl
    val url: String = entry.url ?: entry.indexUrl?.let { indexUrl ->
        onProgress("Consultando informações do catálogo...", 0.05f)
        val req = Request.Builder().url(indexUrl).build()
        client.newCall(req).execute().use { resp ->
            val jsonText = resp.body?.string().orEmpty()
            val obj = com.google.gson.JsonParser.parseString(jsonText).asJsonObject
            obj.get("downloadUrl")?.asString
        }
    } ?: throw Exception("URL de download não encontrada para ${entry.name}")

    // 2. Download zip file to temp
    val tempZip = File(dictDir, "temp_${entry.id}.zip")
    val request = Request.Builder().url(url).build()

    onProgress("Iniciando download de ${entry.sizeLabel}...", 0.1f)

    client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) throw Exception("Servidor respondeu HTTP ${response.code}")
        val body = response.body ?: throw Exception("Download vazio do servidor")
        val totalBytes = body.contentLength()

        body.byteStream().use { inputStream ->
            tempZip.outputStream().use { outputStream ->
                val buffer = ByteArray(64 * 1024)
                var bytesRead = 0L
                var bytesCount = inputStream.read(buffer)
                while (bytesCount >= 0) {
                    outputStream.write(buffer, 0, bytesCount)
                    bytesRead += bytesCount
                    if (totalBytes > 0) {
                        val fraction = 0.1f + (bytesRead.toFloat() / totalBytes) * 0.7f
                        val mbRead = "%.1f".format(bytesRead.toDouble() / (1024 * 1024))
                        val mbTotal = "%.1f".format(totalBytes.toDouble() / (1024 * 1024))
                        onProgress("Baixando: $mbRead MB / $mbTotal MB (${(fraction * 100).toInt()}%)", fraction)
                    } else {
                        val mbRead = "%.1f".format(bytesRead.toDouble() / (1024 * 1024))
                        onProgress("Baixando: $mbRead MB...", 0.5f)
                    }
                    bytesCount = inputStream.read(buffer)
                }
            }
        }
    }

    // 3. Compile zip into .yabai dictionary
    onProgress("Compilando e indexando dicionário...", 0.85f)
    val targetYabaiDir = File(dictDir, "${entry.id}.yabai")
    if (targetYabaiDir.exists()) targetYabaiDir.deleteRecursively()

    val compiler = DictionaryCompiler(
        zipFile = tempZip,
        outputDir = targetYabaiDir,
        sourceName = entry.name
    )
    compiler.compile()

    // 4. Cleanup
    tempZip.delete()
    onProgress("Instalação concluída com sucesso!", 1.0f)
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

@Composable
private fun CreditsAndLicensesSection(installedFiles: List<File>) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = Color(0xFF1B1E28),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (expanded) "▾ Ocultar créditos legais e licenças dos dicionários" else "▸ Ver créditos legais e licenças dos dicionários",
                    style = MaterialTheme.typography.subtitle1.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFF5D84FF)
                )
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(12.dp))
                if (installedFiles.isEmpty()) {
                    Text(
                        "Nenhum dicionário instalado para exibir créditos e licenças.",
                        style = MaterialTheme.typography.body2,
                        color = Color(0xFFA0A6B8)
                    )
                } else {
                    installedFiles.forEach { file ->
                        val dict = remember(file) { runCatching { CompactDictionary.open(file) }.getOrNull() }
                        val info = dict?.info
                        val title = info?.title?.ifBlank { file.nameWithoutExtension } ?: file.nameWithoutExtension
                        val author = info?.author
                        val description = info?.description
                        val attribution = info?.attribution ?: "Licença Yomitan / Domínio público / EDRDG (CC BY-SA 4.0)"
                        val revision = info?.revision
                        val termCount = info?.termCount ?: 0
                        val kanjiCount = info?.kanjiCount ?: 0

                        Column(modifier = Modifier.padding(vertical = 6.dp)) {
                            Text("• $title", style = MaterialTheme.typography.body1.copy(fontWeight = FontWeight.Bold), color = Color.White)
                            if (!author.isNullOrBlank()) {
                                Text("  Autor / Origem: $author", style = MaterialTheme.typography.caption, color = Color(0xFFA0A6B8))
                            }
                            if (!description.isNullOrBlank()) {
                                Text("  Descrição: $description", style = MaterialTheme.typography.caption, color = Color(0xFFA0A6B8))
                            }
                            Text("  Atribuição / Licença: $attribution", style = MaterialTheme.typography.caption, color = Color(0xFF81C784))
                            if (!revision.isNullOrBlank()) {
                                Text("  Revisão / Versão: $revision", style = MaterialTheme.typography.caption, color = Color(0xFFA0A6B8))
                            }
                            if (termCount > 0 || kanjiCount > 0) {
                                val countStr = buildList {
                                    if (termCount > 0) add("$termCount termos")
                                    if (kanjiCount > 0) add("$kanjiCount kanjis")
                                }.joinToString(" • ")
                                Text("  Entradas: $countStr", style = MaterialTheme.typography.caption, color = Color(0xFFA0A6B8))
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        dict?.close()
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Termos gerais: Os dados de vocabulário e kanjis derivam de projetos abertos como JMdict, KANJIDIC, Wikcionário e Jitendex sob licença Creative Commons (CC BY-SA).",
                    style = MaterialTheme.typography.caption,
                    color = Color(0xFF70778B)
                )
            }
        }
    }
}
