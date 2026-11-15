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
import androidx.compose.ui.window.WindowState
import dev.yabai.ocr.core.dict.*
import dev.yabai.ocr.desktop.i18n.DesktopStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
                        Text(DesktopStrings.btnBackToPrevious, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = DesktopStrings.dictManagerTitle,
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
                text = { Text(DesktopStrings.tabQueryDicts, fontWeight = FontWeight.SemiBold) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text(DesktopStrings.tabInstalledDicts, fontWeight = FontWeight.SemiBold) }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text(DesktopStrings.tabDownloadCatalog, fontWeight = FontWeight.SemiBold) }
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
private fun SearchTab(initialQuery: String, refreshKey: Int) {
    var query by remember { mutableStateOf(initialQuery) }
    var results by remember { mutableStateOf<List<TokenMatch>>(emptyList()) }
    var selectedFilter by remember { mutableStateOf("") }
    val settings = remember { DesktopSettings() }
    var translationMode by remember { mutableStateOf(settings.searchByTranslation) }
    var status by remember { mutableStateOf("") }

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

    val suggestions = remember(query, engine, selectedFilter, translationMode) {
        if (!translationMode && engine != null && query.isNotBlank()) {
            val onlySet = if (selectedFilter.isBlank()) emptySet() else setOf(selectedFilter)
            engine.suggest(query, only = onlySet)
        } else emptyList()
    }

    LaunchedEffect(query, refreshKey, selectedFilter, translationMode) {
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

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // The weight keeps the title from claiming the width the buttons need: without it the
            // four buttons beside it were squeezed into unreadable slivers when the window narrowed.
            Text(
                DesktopStrings.searchDictsHeader,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.subtitle1.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colors.onSurface
            )

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
                    Text(if (translationMode) DesktopStrings.searchTypeTranslation else DesktopStrings.searchTypeWord, fontSize = 11.sp)
                }

                var expandedFilter by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(
                        onClick = { expandedFilter = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colors.onSurface)
                    ) {
                        Text(if (selectedFilter.isBlank()) DesktopStrings.allDictionaries else "📖 $selectedFilter", fontSize = 11.sp)
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

        Spacer(modifier = Modifier.height(10.dp))

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
                        backgroundColor = yabaiPalette().field,
                        contentColor = MaterialTheme.colors.onPrimary
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
                color = yabaiPalette().info,
                style = MaterialTheme.typography.caption
            )
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
                    Text(DesktopStrings.dictSummaryNone, style = MaterialTheme.typography.subtitle1, color = yabaiPalette().danger)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "${DesktopStrings.dictFolderLocation} ${dictDir.absolutePath}",
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
                    ScrollableLazyColumn(
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
            Column(modifier = Modifier.weight(1f)) {
                Text(DesktopStrings.localDictsHeader, style = MaterialTheme.typography.h6.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colors.onSurface)
                Text("${DesktopStrings.dictFolderLocation} ${dictDir.absolutePath}", style = MaterialTheme.typography.caption, color = MaterialTheme.colors.secondary)
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

                                // Compose state is read by the UI thread: writing it from
                                // Dispatchers.IO does not schedule a recomposition, which is why
                                // this button looked dead on Windows.
                                withContext(Dispatchers.Main) {
                                    updateAvailableMap = available
                                    remoteRevisionMap = remotes
                                    val count = available.values.count { it }
                                    checkMessage = DesktopStrings.checkCompletedMessage(count)
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    updateError = e.message ?: DesktopStrings.checkUpdatesError
                                }
                            } finally {
                                withContext(Dispatchers.Main) { checkingUpdates = false }
                            }
                        }
                    },
                    enabled = !checkingUpdates,
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = yabaiPalette().field,
                        contentColor = MaterialTheme.colors.onSurface
                    )
                ) {
                    Text(if (checkingUpdates) DesktopStrings.checkingStatus else DesktopStrings.btnCheckUpdates)
                }

                Button(
                    onClick = {
                        val chooser = JFileChooser()
                        chooser.dialogTitle = DesktopStrings.selectDictFileDialogTitle
                        chooser.fileFilter = FileNameExtensionFilter(DesktopStrings.selectDictFileFilterName, "yabai", "zip")
                        val result = chooser.showOpenDialog(null)
                        if (result == JFileChooser.APPROVE_OPTION) {
                            val selected = chooser.selectedFile
                            if (!dictDir.exists()) dictDir.mkdirs()
                            if (selected.extension.lowercase() == "zip") {
                                val targetDir = File(dictDir, "${selected.nameWithoutExtension}.yabai")
                                try {
                                    val compiler = DictionaryCompiler(selected, targetDir, selected.nameWithoutExtension)
                                    compiler.compile()
                                } catch (e: Exception) {
                                    updateError = e.message ?: DesktopStrings.updateDictError
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
                    Text(DesktopStrings.btnImport)
                }

                OutlinedButton(
                    onClick = {
                        if (!dictDir.exists()) dictDir.mkdirs()
                        runCatching { Desktop.getDesktop().open(dictDir) }
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colors.onSurface)
                ) {
                    Text(DesktopStrings.btnFolder)
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
                    Text(DesktopStrings.verificationStatusHeader, style = MaterialTheme.typography.subtitle2, color = yabaiPalette().success)
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
                    Text(DesktopStrings.updateErrorHeader, style = MaterialTheme.typography.subtitle2, color = yabaiPalette().danger)
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
                                            onUiThread {
                                                updateProgressText = text
                                                updateProgressFraction = fraction
                                            }
                                        }
                                    )
                                }
                            }
                            withContext(Dispatchers.Main) {
                                updateAvailableMap = emptyMap()
                                refreshFiles()
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                updateError = e.message ?: DesktopStrings.updateAllDictsError
                            }
                        } finally {
                            withContext(Dispatchers.Main) { updatingId = null }
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(backgroundColor = yabaiPalette().button, contentColor = yabaiPalette().onButton),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(DesktopStrings.btnUpdateAllDicts)
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        ScrollableLazyColumn(
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
                                DesktopStrings.noDictsInstalled,
                                style = MaterialTheme.typography.subtitle1,
                                color = MaterialTheme.colors.onSurface
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                DesktopStrings.noDictsInstalledDesc,
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
                    val sizeMb = "%.1f MB".format(DesktopDictLoader.sizeBytes(file).toDouble() / (1024 * 1024))
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
                                    val revStr = if (revision.isNotBlank()) DesktopStrings.revisionCurrentLabel(revision) else ""
                                    val remoteRev = remoteRevisionMap[file.name]
                                    val updateText = if (hasUpdate && remoteRev != null && remoteRev != DesktopStrings.available) {
                                        DesktopStrings.revisionNewNotice(remoteRev)
                                    } else if (hasUpdate) {
                                        DesktopStrings.updateAvailableNotice
                                    } else ""
                                    Text(
                                        DesktopStrings.fileInfoLabel(file.name, sizeMb, revStr, updateText),
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
                                    Text(if (isDisabled) DesktopStrings.disabled else DesktopStrings.active, style = MaterialTheme.typography.caption, color = if (isDisabled) yabaiPalette().danger else yabaiPalette().success)

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
                                                                    onUiThread {
                                                                        updateProgressText = text
                                                                        updateProgressFraction = fraction
                                                                    }
                                                                }
                                                            )
                                                            withContext(Dispatchers.Main) {
                                                                val newMap = updateAvailableMap.toMutableMap()
                                                                newMap[file.name] = false
                                                                updateAvailableMap = newMap
                                                                refreshFiles()
                                                            }
                                                        }
                                                    } catch (e: Exception) {
                                                        withContext(Dispatchers.Main) {
                                                            updateError = e.message ?: DesktopStrings.updateDictError
                                                        }
                                                    } finally {
                                                        withContext(Dispatchers.Main) { updatingId = null }
                                                    }
                                                }
                                            },
                                            enabled = !isUpdating,
                                            colors = ButtonDefaults.buttonColors(backgroundColor = yabaiPalette().button, contentColor = yabaiPalette().onButton)
                                        ) {
                                            Text(if (isUpdating) DesktopStrings.updatingStatus else DesktopStrings.btnUpdate)
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
                                        Text(DesktopStrings.remove)
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

    ScrollableLazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                DesktopStrings.recommendedDictsHeader,
                style = MaterialTheme.typography.h6.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colors.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "${DesktopStrings.recommendedDictsSubtitle} ${dictDir.absolutePath}",
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
                        Text(DesktopStrings.downloadErrorHeader, style = MaterialTheme.typography.subtitle2, color = yabaiPalette().danger)
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
                    Text("${DesktopStrings.sourceLabel}${entry.attribution}", style = MaterialTheme.typography.caption, color = MaterialTheme.colors.secondary)

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
                                text = "✓ ${DesktopStrings.statusInstalled}",
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
                                                onUiThread {
                                                    downloadProgressText = text
                                                    downloadProgressFraction = fraction
                                                }
                                            }
                                        )
                                        withContext(Dispatchers.Main) { onInstalledChanged() }
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) {
                                            downloadError = e.message ?: DesktopStrings.downloadDictError
                                        }
                                    } finally {
                                        withContext(Dispatchers.Main) { downloadingId = null }
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(backgroundColor = yabaiPalette().button, contentColor = yabaiPalette().onButton)
                        ) {
                            Text(DesktopStrings.btnDownloadAndInstall)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Writes Compose state from a worker thread safely.
 *
 * The download callbacks are plain lambdas running on `Dispatchers.IO`, so they cannot suspend into
 * the main dispatcher; posting to the Swing event thread is what makes the progress bar and the
 * result messages appear.
 */
internal fun onUiThread(block: () -> Unit) {
    javax.swing.SwingUtilities.invokeLater(block)
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
        onProgress(DesktopStrings.catalogInfoConsulting, 0.05f)
        val req = Request.Builder().url(indexUrl).build()
        client.newCall(req).execute().use { resp ->
            val jsonText = resp.body?.string().orEmpty()
            val obj = com.google.gson.JsonParser.parseString(jsonText).asJsonObject
            obj.get("downloadUrl")?.asString
        }
    } ?: throw Exception(DesktopStrings.downloadUrlNotFound(entry.name))

    // 2. Download zip file to temp
    val tempZip = File(dictDir, "temp_${entry.id}.zip")
    // A broken upstream build (index + tag bank only) would fail at compile time with a message that
    // reads like our bug, so walk back to the previous publication that really has entries.
    var installedBytes = 0L
    try {
        val candidates = dictionaryDownloadUrls(url, runCatching { releaseListJson(url, client) }.getOrNull())
        var usedUrl: String? = null
        for ((index, candidate) in candidates.withIndex()) {
            downloadArchive(client, candidate, tempZip, entry.sizeLabel, onProgress)
            if (yomitanArchiveHasEntries(tempZip) || candidates.size == 1) {
                usedUrl = candidate
                break
            }
            onProgress(DesktopStrings.dictionaryReleaseEmpty(entry.name), 0.8f)
        }
        val acceptedUrl = usedUrl
            ?: throw Exception(DesktopStrings.dictionaryNoUsableRelease(entry.name))

        // 3. Compile zip into .yabai dictionary
        onProgress(DesktopStrings.compilingDictNotice, 0.85f)
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
        installedBytes = tempZip.length()
        runCatching {
            val headReq = Request.Builder().url(acceptedUrl).head().build()
            client.newCall(headReq).execute().use { resp ->
                writeSignature(targetYabaiDir, resp.header("ETag"), resp.header("Last-Modified"), installedBytes)
            }
        }

        onProgress(DesktopStrings.installSuccessNotice, 1.0f)
    } finally {
        // A failed install used to leave temp_<id>.zip behind in the dictionary folder.
        tempZip.delete()
    }
}

/** Streams [url] into [target], reporting the progress bar the settings window shows. */
private fun downloadArchive(
    client: OkHttpClient,
    url: String,
    target: File,
    sizeLabel: String,
    onProgress: (String, Float) -> Unit,
) {
    onProgress(DesktopStrings.downloadStartNotice(sizeLabel), 0.1f)
    val request = Request.Builder().url(url).build()

    client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) throw Exception(DesktopStrings.serverHttpResponseError(response.code))
        val body = response.body ?: throw Exception(DesktopStrings.emptyDownloadError)
        val totalBytes = body.contentLength()

        body.byteStream().use { inputStream ->
            target.outputStream().use { outputStream ->
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
                        onProgress(DesktopStrings.downloadingProgress(mbRead, mbTotal, (fraction * 100).toInt()), fraction)
                    } else {
                        val mbRead = "%.1f".format(bytesRead.toDouble() / (1024 * 1024))
                        onProgress(DesktopStrings.downloadingProgressNoTotal(mbRead), 0.5f)
                    }
                    bytesCount = inputStream.read(buffer)
                }
            }
        }
    }
}

/** Release list of the repository behind a GitHub release URL, or null when it is not one. */
private fun releaseListJson(url: String, client: OkHttpClient): String? {
    val (repo, _) = releaseDownloadTarget(url) ?: return null
    val request = Request.Builder()
        .url(releasesApiUrl(repo))
        .header("Accept", "application/vnd.github+json")
        .build()
    client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) return null
        return response.body?.string()
    }
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
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
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
                    text = if (expanded) DesktopStrings.legalCreditsToggleOpen else DesktopStrings.legalCreditsToggleClosed,
                    style = MaterialTheme.typography.subtitle1.copy(fontWeight = FontWeight.Bold),
                    color = yabaiPalette().chipText
                )
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(12.dp))
                if (installedFiles.isEmpty()) {
                    Text(
                        DesktopStrings.noDictsForCredits,
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
                                Text(DesktopStrings.creditAuthor(author), style = MaterialTheme.typography.caption, color = MaterialTheme.colors.secondary)
                            }
                            if (!description.isNullOrBlank()) {
                                Text(DesktopStrings.creditDescription(description), style = MaterialTheme.typography.caption, color = MaterialTheme.colors.secondary)
                            }
                            Text(DesktopStrings.creditAttribution(attribution), style = MaterialTheme.typography.caption, color = yabaiPalette().success)
                            if (!revision.isNullOrBlank()) {
                                Text(DesktopStrings.creditRevision(revision), style = MaterialTheme.typography.caption, color = MaterialTheme.colors.secondary)
                            }
                            if (termCount > 0 || kanjiCount > 0) {
                                val countStr = buildList {
                                    if (termCount > 0) add(DesktopStrings.termsCount(termCount))
                                    if (kanjiCount > 0) add(DesktopStrings.kanjiCount(kanjiCount))
                                }.joinToString(" • ")
                                Text(DesktopStrings.creditEntries(countStr), style = MaterialTheme.typography.caption, color = MaterialTheme.colors.secondary)
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        dict?.close()
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    DesktopStrings.generalLicenseNotice,
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
    return lastModified ?: DesktopStrings.available
}

private fun extractVersionFromUrl(urlStr: String): String? {
    val regex = Regex("/releases/download/([^/]+)/")
    val match = regex.find(urlStr)
    if (match != null) {
        return match.groupValues[1].removePrefix("v").removePrefix("V")
    }
    return null
}
