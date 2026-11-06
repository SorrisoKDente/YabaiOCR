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
                color = MaterialTheme.colors.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onBackRequested,
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = yabaiPalette().field,
                            contentColor = MaterialTheme.colors.onSurface
                        )
                    ) {
                        Text("◀ Voltar ao Menu Anterior", fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "Gerenciador de Dicionários",
                        style = MaterialTheme.typography.subtitle1.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colors.onSurface
                    )
                }
            }
        }

        // --- Top Tabs ---
        TabRow(
            selectedTabIndex = selectedTab,
            backgroundColor = MaterialTheme.colors.surface,
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

        YabaiDesktopTheme(darkTheme = remember { DesktopSettings().darkTheme }) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colors.background
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
            Text("Pesquisar nos Dicionários", style = MaterialTheme.typography.subtitle1.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colors.onSurface)

            var expandedFilter by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(
                    onClick = { expandedFilter = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colors.onSurface)
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
                    textColor = MaterialTheme.colors.onSurface,
                    focusedBorderColor = MaterialTheme.colors.primary,
                    unfocusedBorderColor = yabaiPalette().fieldBorder
                )
            )

            if (query.isNotBlank()) {
                Button(
                    onClick = { query = "" },
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = yabaiPalette().field,
                        contentColor = MaterialTheme.colors.onPrimary
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
                        backgroundColor = yabaiPalette().field,
                        contentColor = MaterialTheme.colors.onPrimary
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
                        color = yabaiPalette().chip,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = suggestion,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.caption.copy(color = yabaiPalette().info)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (engine == null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = yabaiPalette().dangerContainer,
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Nenhum dicionário disponível", style = MaterialTheme.typography.subtitle1, color = yabaiPalette().danger)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Coloque arquivos de dicionário (.yabai) na pasta ${dictDir.absolutePath} para realizar consultas.",
                        color = MaterialTheme.colors.onSurface,
                        style = MaterialTheme.typography.body2
                    )
                }
            }
        } else {
            if (results.isEmpty() && query.isNotBlank()) {
                Text(
                    text = "Nenhum resultado encontrado para 「$query」",
                    color = MaterialTheme.colors.secondary,
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
    val settings = remember { DesktopSettings() }
    val dictDir = remember(refreshKey) { settings.dictDirectory() }
    var installedFiles by remember(refreshKey) {
        mutableStateOf(DesktopDictLoader.loadInstalledFiles(settings))
    }
    var disabledSet by remember(refreshKey) {
        mutableStateOf(settings.getDisabledDictionaries())
    }

    var checkingUpdates by remember { mutableStateOf(false) }
    var updateAvailableMap by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var remoteRevisionMap by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var checkMessage by remember { mutableStateOf<String?>(null) }

    var updatingId by remember { mutableStateOf<String?>(null) }
    var updateProgressText by remember { mutableStateOf("") }
    var updateProgressFraction by remember { mutableStateOf(0f) }
    var updateError by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    fun refreshFiles() {
        if (!dictDir.exists()) dictDir.mkdirs()
        installedFiles = DesktopDictLoader.loadInstalledFiles(settings)
        disabledSet = settings.getDisabledDictionaries()
        onRefresh()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Dicionários Locais e Atualizações", style = MaterialTheme.typography.h6.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colors.onSurface)
                Text("Pasta de dados: ${dictDir.absolutePath}", style = MaterialTheme.typography.caption, color = MaterialTheme.colors.secondary)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        checkingUpdates = true
                        checkMessage = null
                        updateError = null
                        coroutineScope.launch(Dispatchers.IO) {
                            try {
                                val client = OkHttpClient.Builder()
                                    .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                                    .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                                    .build()

                                val available = mutableMapOf<String, Boolean>()
                                val remotes = mutableMapOf<String, String>()

                                installedFiles.forEach { file ->
                                    val dict = runCatching { CompactDictionary.open(file) }.getOrNull()
                                    val installedRev = dict?.info?.revision.orEmpty()
                                    dict?.close()

                                    val catalogEntry = DesktopDictionaryCatalog.entries.find { entry ->
                                        file.nameWithoutExtension.lowercase().contains(entry.id.lowercase()) ||
                                        file.nameWithoutExtension.lowercase().contains(entry.name.lowercase().take(6).lowercase())
                                    }

                                    if (catalogEntry != null) {
                                        val indexUrl = catalogEntry.indexUrl
                                        if (indexUrl != null) {
                                            runCatching {
                                                val req = Request.Builder().url(indexUrl).build()
                                                client.newCall(req).execute().use { resp ->
                                                    val jsonText = resp.body?.string().orEmpty()
                                                    val obj = com.google.gson.JsonParser.parseString(jsonText).asJsonObject
                                                    val remoteRev = obj.get("revision")?.takeIf { !it.isJsonNull }?.asString.orEmpty()
                                                    if (remoteRev.isNotBlank()) {
                                                        val order = compareRevisions(installedRev, remoteRev)
                                                        val hasUpdate = installedRev.isBlank() || (order != null && order < 0)
                                                        available[file.name] = hasUpdate
                                                        if (hasUpdate) {
                                                            remotes[file.name] = remoteRev
                                                        }
                                                    } else {
                                                        available[file.name] = false
                                                    }
                                                }
                                            }
                                        } else if (catalogEntry.url != null) {
                                            runCatching {
                                                val req = Request.Builder().url(catalogEntry.url).build()
                                                client.newCall(req).execute().use { resp ->
                                                    val finalUrl = resp.request.url.toString()
                                                    val remoteVersion = extractVersionFromUrl(finalUrl) ?: formatRemoteLabel(resp.header("Last-Modified"), resp.header("ETag"))
                                                    val remoteEtag = resp.header("ETag")
                                                    val remoteLastModified = resp.header("Last-Modified")
                                                    val remoteLength = resp.header("Content-Length")?.toLongOrNull() ?: 0L
                                                    val localSig = readSignature(file)
                                                    val hasUpdate = localSig == null || localSig.length != remoteLength ||
                                                        (remoteEtag != null && localSig.etag != remoteEtag) ||
                                                        (remoteLastModified != null && localSig.lastModified != remoteLastModified)
                                                    available[file.name] = hasUpdate
                                                    if (hasUpdate) {
                                                        remotes[file.name] = remoteVersion
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                updateAvailableMap = available
                                remoteRevisionMap = remotes
                                val count = available.values.count { it }
                                checkMessage = "Verificação concluída. $count ${if (count == 1) "atualização disponível" else "atualizações disponíveis"}."
                            } catch (e: Exception) {
                                updateError = e.message ?: "Erro ao verificar atualizações."
                            } finally {
                                checkingUpdates = false
                            }
                        }
                    },
                    enabled = !checkingUpdates,
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = yabaiPalette().field,
                        contentColor = MaterialTheme.colors.onSurface
                    )
                ) {
                    Text(if (checkingUpdates) "Verificando..." else "🔄 Verificar Atualizações")
                }

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
                    colors = ButtonDefaults.buttonColors(backgroundColor = yabaiPalette().button, contentColor = yabaiPalette().onButton)
                ) {
                    Text("➕ Importar")
                }

                OutlinedButton(
                    onClick = {
                        if (!dictDir.exists()) dictDir.mkdirs()
                        runCatching { Desktop.getDesktop().open(dictDir) }
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colors.onSurface)
                ) {
                    Text("📁 Pasta")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (checkMessage != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = yabaiPalette().successContainer,
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Status da Verificação", style = MaterialTheme.typography.subtitle2, color = yabaiPalette().success)
                    Text(checkMessage.orEmpty(), style = MaterialTheme.typography.caption, color = MaterialTheme.colors.onSurface)
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        if (updateError != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = yabaiPalette().dangerContainer,
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Erro na Atualização", style = MaterialTheme.typography.subtitle2, color = yabaiPalette().danger)
                    Text(updateError.orEmpty(), style = MaterialTheme.typography.caption, color = MaterialTheme.colors.onSurface)
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        if (updateAvailableMap.values.any { it }) {
            Button(
                onClick = {
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            val updates = installedFiles.filter { updateAvailableMap[it.name] == true }
                            updates.forEach { file ->
                                val catalogEntry = DesktopDictionaryCatalog.entries.find { entry ->
                                    file.nameWithoutExtension.lowercase().contains(entry.id.lowercase()) ||
                                    file.nameWithoutExtension.lowercase().contains(entry.name.lowercase().take(6).lowercase())
                                }
                                if (catalogEntry != null) {
                                    updatingId = file.name
                                    downloadAndInstallDictionary(
                                        entry = catalogEntry,
                                        dictDir = dictDir,
                                        onProgress = { text: String, fraction: Float ->
                                            updateProgressText = text
                                            updateProgressFraction = fraction
                                        }
                                    )
                                }
                            }
                            updateAvailableMap = emptyMap()
                            refreshFiles()
                        } catch (e: Exception) {
                            updateError = e.message ?: "Erro ao atualizar todos os dicionários."
                        } finally {
                            updatingId = null
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(backgroundColor = yabaiPalette().button, contentColor = yabaiPalette().onButton),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("🔄 Atualizar Todos os Dicionários Disponíveis")
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (installedFiles.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = MaterialTheme.colors.surface,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                "Nenhum dicionário instalado no momento.",
                                style = MaterialTheme.typography.subtitle1,
                                color = MaterialTheme.colors.onSurface
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "Você pode importar arquivos .yabai ou .zip existentes usando o botão acima, ou navegar pela aba 'Catálogo de Downloads' para baixar os dicionários recomendados.",
                                style = MaterialTheme.typography.body2,
                                color = MaterialTheme.colors.secondary
                            )
                        }
                    }
                }
            } else {
                items(installedFiles) { file ->
                    val dict = remember(file) { runCatching { CompactDictionary.open(file) }.getOrNull() }
                    val info = dict?.info
                    val title = info?.title?.ifBlank { file.nameWithoutExtension } ?: file.nameWithoutExtension
                    val revision = info?.revision.orEmpty()
                    val sizeMb = "%.1f MB".format(file.length().toDouble() / (1024 * 1024))
                    val catalogEntry = DesktopDictionaryCatalog.entries.find { entry ->
                        file.nameWithoutExtension.lowercase().contains(entry.id.lowercase()) ||
                        file.nameWithoutExtension.lowercase().contains(entry.name.lowercase().take(6).lowercase())
                    }
                    val isUpdating = updatingId == file.name
                    val hasUpdate = updateAvailableMap[file.name] == true

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = MaterialTheme.colors.surface,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(title, style = MaterialTheme.typography.subtitle1.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colors.onSurface)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    val revStr = if (revision.isNotBlank()) " • Atual: $revision" else ""
                                    val remoteRev = remoteRevisionMap[file.name]
                                    val updateText = if (hasUpdate && remoteRev != null && remoteRev != "Disponível") {
                                        " • ✨ Nova: $remoteRev"
                                    } else if (hasUpdate) {
                                        " • ✨ Atualização disponível"
                                    } else ""
                                    Text(
                                        "Arquivo: ${file.name} • Tamanho: $sizeMb$revStr$updateText",
                                        style = MaterialTheme.typography.caption,
                                        color = if (updateText.isNotBlank()) yabaiPalette().success else MaterialTheme.colors.secondary
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    val isDisabled = disabledSet.contains(file.name)
                                    Checkbox(
                                        checked = !isDisabled,
                                        onCheckedChange = { isChecked ->
                                            val newDisabled = disabledSet.toMutableSet()
                                            if (isChecked) newDisabled.remove(file.name) else newDisabled.add(file.name)
                                            settings.setDisabledDictionaries(newDisabled)
                                            refreshFiles()
                                        },
                                        colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colors.primary)
                                    )
                                    Text(if (isDisabled) "Desativado" else "Ativo", style = MaterialTheme.typography.caption, color = if (isDisabled) yabaiPalette().danger else yabaiPalette().success)

                                    Spacer(modifier = Modifier.width(4.dp))

                                    val index = installedFiles.indexOf(file)
                                    if (index > 0) {
                                        OutlinedButton(
                                            onClick = {
                                                val mutable = installedFiles.map { it.name }.toMutableList()
                                                val item = mutable.removeAt(index)
                                                mutable.add(index - 1, item)
                                                settings.setDictionaryOrder(mutable)
                                                refreshFiles()
                                            },
                                            modifier = Modifier.size(32.dp),
                                            contentPadding = PaddingValues(0.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colors.onSurface)
                                        ) {
                                            Text("▲", fontSize = 10.sp)
                                        }
                                    }
                                    if (index < installedFiles.size - 1) {
                                        OutlinedButton(
                                            onClick = {
                                                val mutable = installedFiles.map { it.name }.toMutableList()
                                                val item = mutable.removeAt(index)
                                                mutable.add(index + 1, item)
                                                settings.setDictionaryOrder(mutable)
                                                refreshFiles()
                                            },
                                            modifier = Modifier.size(32.dp),
                                            contentPadding = PaddingValues(0.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colors.onSurface)
                                        ) {
                                            Text("▼", fontSize = 10.sp)
                                        }
                                    }

                                    if (hasUpdate || isUpdating) {
                                        Button(
                                            onClick = {
                                                updatingId = file.name
                                                updateError = null
                                                coroutineScope.launch(Dispatchers.IO) {
                                                    try {
                                                        if (catalogEntry != null) {
                                                            downloadAndInstallDictionary(
                                                                entry = catalogEntry,
                                                                dictDir = dictDir,
                                                                onProgress = { text: String, fraction: Float ->
                                                                    updateProgressText = text
                                                                    updateProgressFraction = fraction
                                                                }
                                                            )
                                                            val newMap = updateAvailableMap.toMutableMap()
                                                            newMap[file.name] = false
                                                            updateAvailableMap = newMap
                                                            refreshFiles()
                                                        }
                                                    } catch (e: Exception) {
                                                        updateError = e.message ?: "Erro ao atualizar o dicionário."
                                                    } finally {
                                                        updatingId = null
                                                    }
                                                }
                                            },
                                            enabled = !isUpdating,
                                            colors = ButtonDefaults.buttonColors(backgroundColor = yabaiPalette().button, contentColor = yabaiPalette().onButton)
                                        ) {
                                            Text(if (isUpdating) "Atualizando..." else "🔄 Atualizar")
                                        }
                                    }

                                    Button(
                                        onClick = {
                                            // The picker keeps its dictionaries open between
                                            // captures; on Windows an open blob blocks the delete.
                                            DesktopDictLoader.closeCached()
                                            file.deleteRecursively()
                                            refreshFiles()
                                        },
                                        colors = ButtonDefaults.buttonColors(backgroundColor = yabaiPalette().dangerContainer, contentColor = yabaiPalette().danger)
                                    ) {
                                        Text("Remover")
                                    }
                                }
                            }

                            if (isUpdating) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Column {
                                    LinearProgressIndicator(
                                        progress = updateProgressFraction,
                                        modifier = Modifier.fillMaxWidth(),
                                        color = MaterialTheme.colors.primary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(updateProgressText, style = MaterialTheme.typography.caption, color = MaterialTheme.colors.onSurface)
                                }
                            }
                        }
                    }
                    dict?.close()
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
                color = MaterialTheme.colors.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Baixe e instale dicionários diretamente no aplicativo sem sair. Eles serão salvos e ativados na pasta de dados: ${dictDir.absolutePath}",
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.secondary
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (downloadError != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = yabaiPalette().dangerContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Erro no Download", style = MaterialTheme.typography.subtitle2, color = yabaiPalette().danger)
                        Text(downloadError.orEmpty(), style = MaterialTheme.typography.caption, color = MaterialTheme.colors.onSurface)
                    }
                }
            }
        }

        items(DesktopDictionaryCatalog.entries) { entry ->
            val isInstalled = installedNames.contains(entry.id.lowercase()) || installedNames.contains(entry.name.lowercase())
            val isDownloading = downloadingId == entry.id

            Card(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = MaterialTheme.colors.surface,
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(entry.name, style = MaterialTheme.typography.subtitle1.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colors.onSurface)
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = yabaiPalette().chip,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = entry.language.uppercase(),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    fontSize = 10.sp,
                                    color = yabaiPalette().chipText
                                )
                            }
                        }

                        Text(entry.sizeLabel, style = MaterialTheme.typography.caption, color = MaterialTheme.colors.secondary)
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(entry.description, style = MaterialTheme.typography.body2, color = MaterialTheme.colors.secondary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Fonte: ${entry.attribution}", style = MaterialTheme.typography.caption, color = MaterialTheme.colors.secondary)

                    Spacer(modifier = Modifier.height(10.dp))

                    if (isDownloading) {
                        Column {
                            LinearProgressIndicator(
                                progress = downloadProgressFraction,
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colors.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(downloadProgressText, style = MaterialTheme.typography.caption, color = MaterialTheme.colors.onSurface)
                        }
                    } else if (isInstalled) {
                        Surface(
                            color = yabaiPalette().successContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "✓ Instalado",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.body2.copy(fontWeight = FontWeight.SemiBold),
                                color = yabaiPalette().success
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
                            colors = ButtonDefaults.buttonColors(backgroundColor = yabaiPalette().button, contentColor = yabaiPalette().onButton)
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
    // The picker's cached engine may still hold the old blobs open; without this the delete below
    // is a no-op on Windows and the compile would write into the previous version's files.
    DesktopDictLoader.closeCached()
    if (targetYabaiDir.exists()) targetYabaiDir.deleteRecursively()

    val compiler = DictionaryCompiler(
        zipFile = tempZip,
        outputDir = targetYabaiDir,
        sourceName = entry.name
    )
    compiler.compile()

    // Save signature for future updates check
    runCatching {
        val headReq = Request.Builder().url(url).head().build()
        client.newCall(headReq).execute().use { resp ->
            writeSignature(targetYabaiDir, resp.header("ETag"), resp.header("Last-Modified"), tempZip.length())
        }
    }

    // 4. Cleanup
    tempZip.delete()
    onProgress("Instalação concluída com sucesso!", 1.0f)
}

@Composable
private fun DictionarySearchCard(match: TokenMatch) {
    val entry = match.entry

    Card(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = MaterialTheme.colors.surface,
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
                        color = MaterialTheme.colors.onSurface
                    )
                    if (entry.reading.isNotBlank() && entry.reading != entry.term) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "【${entry.reading}】",
                            style = MaterialTheme.typography.body1,
                            color = yabaiPalette().chipText
                        )
                    }
                }
                if (match.dictionaryTitle.isNotBlank()) {
                    Text(
                        text = match.dictionaryTitle,
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.secondary
                    )
                }
            }

            if (entry.tags.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(entry.tags, style = MaterialTheme.typography.caption, color = yabaiPalette().warning)
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = entry.definition,
                style = MaterialTheme.typography.body2.copy(lineHeight = 18.sp),
                color = MaterialTheme.colors.onSurface
            )
        }
    }
}

@Composable
private fun CreditsAndLicensesSection(installedFiles: List<File>) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = MaterialTheme.colors.surface,
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
                    color = yabaiPalette().chipText
                )
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(12.dp))
                if (installedFiles.isEmpty()) {
                    Text(
                        "Nenhum dicionário instalado para exibir créditos e licenças.",
                        style = MaterialTheme.typography.body2,
                        color = MaterialTheme.colors.secondary
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
                            Text("• $title", style = MaterialTheme.typography.body1.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colors.onSurface)
                            if (!author.isNullOrBlank()) {
                                Text("  Autor / Origem: $author", style = MaterialTheme.typography.caption, color = MaterialTheme.colors.secondary)
                            }
                            if (!description.isNullOrBlank()) {
                                Text("  Descrição: $description", style = MaterialTheme.typography.caption, color = MaterialTheme.colors.secondary)
                            }
                            Text("  Atribuição / Licença: $attribution", style = MaterialTheme.typography.caption, color = yabaiPalette().success)
                            if (!revision.isNullOrBlank()) {
                                Text("  Revisão / Versão: $revision", style = MaterialTheme.typography.caption, color = MaterialTheme.colors.secondary)
                            }
                            if (termCount > 0 || kanjiCount > 0) {
                                val countStr = buildList {
                                    if (termCount > 0) add("$termCount termos")
                                    if (kanjiCount > 0) add("$kanjiCount kanjis")
                                }.joinToString(" • ")
                                Text("  Entradas: $countStr", style = MaterialTheme.typography.caption, color = MaterialTheme.colors.secondary)
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
                    color = MaterialTheme.colors.secondary
                )
            }
        }
    }
}

private fun compareRevisions(installed: String, remote: String): Int? {
    val left = revisionParts(installed) ?: return null
    val right = revisionParts(remote) ?: return null
    val size = maxOf(left.size, right.size)
    for (index in 0 until size) {
        val a = left.getOrElse(index) { 0L }
        val b = right.getOrElse(index) { 0L }
        if (a != b) return a.compareTo(b)
    }
    return 0
}

private fun revisionParts(label: String): List<Long>? {
    val parts = ArrayList<Long>(4)
    var index = 0
    while (index < label.length) {
        if (!label[index].isDigit()) {
            index++
            continue
        }
        val start = index
        while (index < label.length && label[index].isDigit()) index++
        val value = label.substring(start, index).toLongOrNull() ?: return null
        parts.add(value)
    }
    return parts.ifEmpty { null }
}

private data class DictSignature(val etag: String?, val lastModified: String?, val length: Long)

private fun readSignature(dir: File): DictSignature? {
    val file = File(dir, "signature.properties")
    if (!file.isFile) return null
    val props = java.util.Properties()
    runCatching { file.inputStream().use { props.load(it) } }
    return DictSignature(
        props.getProperty("etag"),
        props.getProperty("lastModified"),
        props.getProperty("length")?.toLongOrNull() ?: 0L
    )
}

private fun writeSignature(dir: File, etag: String?, lastModified: String?, length: Long) {
    val file = File(dir, "signature.properties")
    val props = java.util.Properties().apply {
        if (etag != null) setProperty("etag", etag)
        if (lastModified != null) setProperty("lastModified", lastModified)
        setProperty("length", length.toString())
    }
    runCatching { file.outputStream().use { props.store(it, "Yabai OCR Dictionary Signature") } }
}

private fun formatRemoteLabel(lastModified: String?, etag: String?): String {
    if (!lastModified.isNullOrBlank()) {
        runCatching {
            val parsed = java.time.ZonedDateTime.parse(lastModified, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME)
            return parsed.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy", java.util.Locale.getDefault()))
        }
    }
    if (!etag.isNullOrBlank()) {
        return etag.trim('"')
    }
    return lastModified ?: "Disponível"
}

private fun extractVersionFromUrl(urlStr: String): String? {
    val regex = Regex("/releases/download/([^/]+)/")
    val match = regex.find(urlStr)
    if (match != null) {
        return match.groupValues[1].removePrefix("v").removePrefix("V")
    }
    return null
}
