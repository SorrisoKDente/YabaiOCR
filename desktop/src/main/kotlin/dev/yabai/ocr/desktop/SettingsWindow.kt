package dev.yabai.ocr.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import dev.yabai.ocr.core.dict.CompactDictionary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.swing.JFileChooser

@Composable
fun SettingsContent(
    settings: DesktopSettings = remember { DesktopSettings() },
    onManageDictionariesRequested: (() -> Unit)? = null,
    hoverActive: Boolean = false,
    hoverStatus: String = "",
    hotkeyBackendStatus: String = "",
    linuxIntegrationStatus: String = "",
    onInstallLinuxIntegration: (() -> Unit)? = null,
    onHoverToggle: () -> Unit = {},
    onHoverTest: () -> Unit = {},
    onThemeChanged: (Boolean) -> Unit = {},
    onHotkeysChanged: (
        fullScreen: String,
        region: String,
        quickDict: String,
        hover: String,
        hoverCycle: String,
    ) -> Unit = { _, _, _, _, _ -> },
) {
    val palette = yabaiPalette()
    var darkTheme by remember { mutableStateOf(settings.darkTheme) }

    var globalHotkey by remember { mutableStateOf(settings.globalHotkey) }
    var regionHotkey by remember { mutableStateOf(settings.regionHotkey) }
    var quickDictHotkey by remember { mutableStateOf(settings.quickDictHotkey) }
    var hoverHotkey by remember { mutableStateOf(settings.hoverHotkey) }
    var hoverCycleHotkey by remember { mutableStateOf(settings.hoverCycleHotkey) }

    var isCapturingHotkey by remember { mutableStateOf(false) }
    var isCapturingRegionHotkey by remember { mutableStateOf(false) }
    var isCapturingQuickDictHotkey by remember { mutableStateOf(false) }
    var isCapturingHoverHotkey by remember { mutableStateOf(false) }
    var isCapturingHoverCycleHotkey by remember { mutableStateOf(false) }

    val hotkeyFocusRequester = remember { FocusRequester() }
    val regionHotkeyFocusRequester = remember { FocusRequester() }
    val quickDictFocusRequester = remember { FocusRequester() }
    val hoverHotkeyFocusRequester = remember { FocusRequester() }
    val hoverCycleHotkeyFocusRequester = remember { FocusRequester() }

    fun notifyHotkeysChanged() {
        onHotkeysChanged(globalHotkey, regionHotkey, quickDictHotkey, hoverHotkey, hoverCycleHotkey)
    }

    var engineChoice by remember { mutableStateOf(settings.engine) }
    var enginePriority by remember { mutableStateOf(settings.enginePriorityList) }
    var autoCopy by remember { mutableStateOf(settings.autoCopy) }
    var startOnBoot by remember { mutableStateOf(settings.startOnBoot) }
    var closeToTray by remember { mutableStateOf(settings.closeToTray) }
    var showSegments by remember { mutableStateOf(settings.showSegments) }
    var upscaleSmall by remember { mutableStateOf(settings.upscaleSmall) }
    var showImage by remember { mutableStateOf(settings.showImage) }
    var furiganaInParentheses by remember { mutableStateOf(settings.furiganaInParentheses) }
    var showFurigana by remember { mutableStateOf(settings.showFurigana) }
    var pickerReading by remember { mutableStateOf(settings.pickerReading) }

    var dictionaryLookup by remember { mutableStateOf(settings.dictionaryLookup) }
    var dictionaryFilter by remember { mutableStateOf(settings.dictionaryFilter) }
    var hoverDictionaryFilter by remember { mutableStateOf(settings.hoverDictionaryFilter) }
    var dictionaryKeepLastSearch by remember { mutableStateOf(settings.dictionaryKeepLastSearch) }
    var stripSearchReadings by remember { mutableStateOf(settings.stripSearchReadings) }
    var dictionaryUpdateInterval by remember { mutableStateOf(settings.dictionaryUpdateInterval) }
    var customDictDir by remember { mutableStateOf(settings.dictDirectory().absolutePath) }

    var translateSentence by remember { mutableStateOf(settings.translateSentence) }
    var translateTarget by remember { mutableStateOf(settings.translateTarget) }

    var showAiButton by remember { mutableStateOf(settings.showAiButton) }
    var aiPrompt by remember { mutableStateOf(settings.aiPrompt) }
    var aiProvider by remember { mutableStateOf(settings.aiProvider) }
    var aiCustomUrl by remember { mutableStateOf(settings.aiCustomUrl) }

    val installedDictTitles = remember(customDictDir) {
        val dictDir = settings.dictDirectory()
        val list = mutableListOf<String>()
        try {
            if (dictDir.exists() && dictDir.isDirectory) {
                dictDir.listFiles()?.forEach { file ->
                    if (file.extension == "yabai" || file.isDirectory) {
                        val dict = runCatching { CompactDictionary.open(file) }.getOrNull()
                        if (dict != null) {
                            list.add(dict.info.title)
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        list.toList()
    }

    val installedDictCount = installedDictTitles.size

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // --- Header ---
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Yabai OCR Desktop",
                        style = MaterialTheme.typography.h4.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colors.onBackground
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Leia japonês em qualquer aplicativo no seu PC — navegador, leitores de mangá, jogos ou PDFs. Capture a área da tela e obtenha leituras, dicionários e tradução instantânea.",
                        style = MaterialTheme.typography.body2.copy(lineHeight = 20.sp),
                        color = MaterialTheme.colors.secondary
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Button(
                    onClick = {
                        val newDark = !darkTheme
                        darkTheme = newDark
                        settings.darkTheme = newDark
                        onThemeChanged(newDark)
                    },
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = palette.field,
                        contentColor = MaterialTheme.colors.onSurface
                    )
                ) {
                    Text(if (darkTheme) "🌙 Escuro" else "☀️ Claro", fontWeight = FontWeight.Bold)
                }
            }
        }

        // --- Aparência e Tema ---
        item {
            SettingCard(title = "Aparência e Tema") {
                Text(
                    text = "Escolha o tema de exibição do aplicativo:",
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.secondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    SettingRadioOption(
                        selected = darkTheme,
                        title = "Tema Escuro 🌙",
                        description = "Ideal para leitura noturna e contraste alto em telas escuras.",
                        onClick = {
                            if (!darkTheme) {
                                darkTheme = true
                                settings.darkTheme = true
                                onThemeChanged(true)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )

                    SettingRadioOption(
                        selected = !darkTheme,
                        title = "Tema Claro ☀️",
                        description = "Ideal para ambientes iluminados e documentos em fundo claro.",
                        onClick = {
                            if (darkTheme) {
                                darkTheme = false
                                settings.darkTheme = false
                                onThemeChanged(false)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // --- Atalhos Globais ---
        item {
            SettingCard(title = "Atalhos Globais de Teclado") {
                if (hotkeyBackendStatus.isNotBlank()) {
                    Text(
                        text = hotkeyBackendStatus,
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.secondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // OCR Hotkey
                Text(
                    text = "Atalho da Captura da Tela Inteira (OCR):",
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.secondary
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(hotkeyFocusRequester)
                            .focusable()
                            .onKeyEvent { keyEvent ->
                                if (isCapturingHotkey && keyEvent.type == KeyEventType.KeyDown) {
                                    val key = keyEvent.key
                                    if (key != Key.CtrlLeft && key != Key.CtrlRight &&
                                        key != Key.ShiftLeft && key != Key.ShiftRight &&
                                        key != Key.AltLeft && key != Key.AltRight &&
                                        key != Key.MetaLeft && key != Key.MetaRight
                                    ) {
                                        val parts = mutableListOf<String>()
                                        if (keyEvent.isCtrlPressed) parts.add("Ctrl")
                                        if (keyEvent.isAltPressed) parts.add("Alt")
                                        if (keyEvent.isShiftPressed) parts.add("Shift")
                                        if (keyEvent.isMetaPressed) parts.add("Meta")

                                        val keyName = when (key) {
                                            Key.Spacebar -> "Space"
                                            Key.Enter -> "Enter"
                                            Key.Escape -> "Esc"
                                            Key.Tab -> "Tab"
                                            Key.Backspace -> "Backspace"
                                            else -> java.awt.event.KeyEvent.getKeyText(key.nativeKeyCode)
                                        }
                                        parts.add(keyName)
                                        val formatted = parts.joinToString(" + ")

                                        globalHotkey = formatted
                                        settings.globalHotkey = formatted
                                        isCapturingHotkey = false
                                        notifyHotkeysChanged()
                                        true
                                    } else false
                                } else false
                            }
                            .background(
                                color = if (isCapturingHotkey) MaterialTheme.colors.primary.copy(alpha = 0.15f) else palette.field,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = if (isCapturingHotkey) MaterialTheme.colors.primary else palette.fieldBorder,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = if (isCapturingHotkey) "Pressione as teclas..." else "Atalho: $globalHotkey",
                            style = MaterialTheme.typography.body1.copy(fontWeight = FontWeight.SemiBold),
                            color = if (isCapturingHotkey) palette.success else MaterialTheme.colors.onSurface
                        )
                    }

                    Button(
                        onClick = {
                            isCapturingHotkey = true
                            hotkeyFocusRequester.requestFocus()
                        },
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = if (isCapturingHotkey) palette.field else palette.button,
                            contentColor = if (isCapturingHotkey) MaterialTheme.colors.onSurface else MaterialTheme.colors.onPrimary
                        )
                    ) {
                        Text(if (isCapturingHotkey) "Aguardando..." else "Capturar")
                    }

                    OutlinedButton(
                        onClick = {
                            globalHotkey = "Ctrl + Shift + O"
                            settings.globalHotkey = "Ctrl + Shift + O"
                            isCapturingHotkey = false
                            notifyHotkeysChanged()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colors.onSurface)
                    ) {
                        Text("Padrão")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Region Crop Hotkey
                Text(
                    text = "Atalho do Recorte de Área (OCR):",
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.secondary
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(regionHotkeyFocusRequester)
                            .focusable()
                            .onKeyEvent { keyEvent ->
                                if (isCapturingRegionHotkey && keyEvent.type == KeyEventType.KeyDown) {
                                    val key = keyEvent.key
                                    if (key != Key.CtrlLeft && key != Key.CtrlRight &&
                                        key != Key.ShiftLeft && key != Key.ShiftRight &&
                                        key != Key.AltLeft && key != Key.AltRight &&
                                        key != Key.MetaLeft && key != Key.MetaRight
                                    ) {
                                        val parts = mutableListOf<String>()
                                        if (keyEvent.isCtrlPressed) parts.add("Ctrl")
                                        if (keyEvent.isAltPressed) parts.add("Alt")
                                        if (keyEvent.isShiftPressed) parts.add("Shift")
                                        if (keyEvent.isMetaPressed) parts.add("Meta")

                                        val keyName = when (key) {
                                            Key.Spacebar -> "Space"
                                            Key.Enter -> "Enter"
                                            Key.Escape -> "Esc"
                                            Key.Tab -> "Tab"
                                            Key.Backspace -> "Backspace"
                                            else -> java.awt.event.KeyEvent.getKeyText(key.nativeKeyCode)
                                        }
                                        parts.add(keyName)
                                        val formatted = parts.joinToString(" + ")

                                        regionHotkey = formatted
                                        settings.regionHotkey = formatted
                                        isCapturingRegionHotkey = false
                                        notifyHotkeysChanged()
                                        true
                                    } else false
                                } else false
                            }
                            .background(
                                color = if (isCapturingRegionHotkey) MaterialTheme.colors.primary.copy(alpha = 0.15f) else palette.field,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = if (isCapturingRegionHotkey) MaterialTheme.colors.primary else palette.fieldBorder,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = if (isCapturingRegionHotkey) "Pressione as teclas..." else "Atalho: $regionHotkey",
                            style = MaterialTheme.typography.body1.copy(fontWeight = FontWeight.SemiBold),
                            color = if (isCapturingRegionHotkey) palette.success else MaterialTheme.colors.onSurface
                        )
                    }

                    Button(
                        onClick = {
                            isCapturingRegionHotkey = true
                            regionHotkeyFocusRequester.requestFocus()
                        },
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = if (isCapturingRegionHotkey) palette.field else palette.button,
                            contentColor = if (isCapturingRegionHotkey) MaterialTheme.colors.onSurface else MaterialTheme.colors.onPrimary
                        )
                    ) {
                        Text(if (isCapturingRegionHotkey) "Aguardando..." else "Capturar")
                    }

                    OutlinedButton(
                        onClick = {
                            regionHotkey = "Ctrl + Shift + A"
                            settings.regionHotkey = "Ctrl + Shift + A"
                            isCapturingRegionHotkey = false
                            notifyHotkeysChanged()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colors.onSurface)
                    ) {
                        Text("Padrão")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Quick Dict Hotkey
                Text(
                    text = "Atalho de Busca Rápida de Dicionário:",
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.secondary
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(quickDictFocusRequester)
                            .focusable()
                            .onKeyEvent { keyEvent ->
                                if (isCapturingQuickDictHotkey && keyEvent.type == KeyEventType.KeyDown) {
                                    val key = keyEvent.key
                                    if (key != Key.CtrlLeft && key != Key.CtrlRight &&
                                        key != Key.ShiftLeft && key != Key.ShiftRight &&
                                        key != Key.AltLeft && key != Key.AltRight &&
                                        key != Key.MetaLeft && key != Key.MetaRight
                                    ) {
                                        val parts = mutableListOf<String>()
                                        if (keyEvent.isCtrlPressed) parts.add("Ctrl")
                                        if (keyEvent.isAltPressed) parts.add("Alt")
                                        if (keyEvent.isShiftPressed) parts.add("Shift")
                                        if (keyEvent.isMetaPressed) parts.add("Meta")

                                        val keyName = when (key) {
                                            Key.Spacebar -> "Space"
                                            Key.Enter -> "Enter"
                                            Key.Escape -> "Esc"
                                            Key.Tab -> "Tab"
                                            Key.Backspace -> "Backspace"
                                            else -> java.awt.event.KeyEvent.getKeyText(key.nativeKeyCode)
                                        }
                                        parts.add(keyName)
                                        val formatted = parts.joinToString(" + ")

                                        quickDictHotkey = formatted
                                        settings.quickDictHotkey = formatted
                                        isCapturingQuickDictHotkey = false
                                        notifyHotkeysChanged()
                                        true
                                    } else false
                                } else false
                            }
                            .background(
                                color = if (isCapturingQuickDictHotkey) MaterialTheme.colors.primary.copy(alpha = 0.15f) else palette.field,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = if (isCapturingQuickDictHotkey) MaterialTheme.colors.primary else palette.fieldBorder,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = if (isCapturingQuickDictHotkey) "Pressione as teclas..." else "Atalho: $quickDictHotkey",
                            style = MaterialTheme.typography.body1.copy(fontWeight = FontWeight.SemiBold),
                            color = if (isCapturingQuickDictHotkey) palette.success else MaterialTheme.colors.onSurface
                        )
                    }

                    Button(
                        onClick = {
                            isCapturingQuickDictHotkey = true
                            quickDictFocusRequester.requestFocus()
                        },
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = if (isCapturingQuickDictHotkey) palette.field else palette.button,
                            contentColor = if (isCapturingQuickDictHotkey) MaterialTheme.colors.onSurface else MaterialTheme.colors.onPrimary
                        )
                    ) {
                        Text(if (isCapturingQuickDictHotkey) "Aguardando..." else "Capturar")
                    }

                    OutlinedButton(
                        onClick = {
                            quickDictHotkey = "Ctrl + Shift + D"
                            settings.quickDictHotkey = "Ctrl + Shift + D"
                            isCapturingQuickDictHotkey = false
                            notifyHotkeysChanged()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colors.onSurface)
                    ) {
                        Text("Padrão")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Hover Hotkey
                Text(
                    text = "Atalho de Ligar/Desligar Modo Hover:",
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.secondary
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(hoverHotkeyFocusRequester)
                            .focusable()
                            .onKeyEvent { keyEvent ->
                                if (isCapturingHoverHotkey && keyEvent.type == KeyEventType.KeyDown) {
                                    val key = keyEvent.key
                                    if (key != Key.CtrlLeft && key != Key.CtrlRight &&
                                        key != Key.ShiftLeft && key != Key.ShiftRight &&
                                        key != Key.AltLeft && key != Key.AltRight &&
                                        key != Key.MetaLeft && key != Key.MetaRight
                                    ) {
                                        val parts = mutableListOf<String>()
                                        if (keyEvent.isCtrlPressed) parts.add("Ctrl")
                                        if (keyEvent.isAltPressed) parts.add("Alt")
                                        if (keyEvent.isShiftPressed) parts.add("Shift")
                                        if (keyEvent.isMetaPressed) parts.add("Meta")

                                        val keyName = when (key) {
                                            Key.Spacebar -> "Space"
                                            Key.Enter -> "Enter"
                                            Key.Escape -> "Esc"
                                            Key.Tab -> "Tab"
                                            Key.Backspace -> "Backspace"
                                            else -> java.awt.event.KeyEvent.getKeyText(key.nativeKeyCode)
                                        }
                                        parts.add(keyName)
                                        val formatted = parts.joinToString(" + ")

                                        hoverHotkey = formatted
                                        settings.hoverHotkey = formatted
                                        isCapturingHoverHotkey = false
                                        notifyHotkeysChanged()
                                        true
                                    } else false
                                } else false
                            }
                            .background(
                                color = if (isCapturingHoverHotkey) MaterialTheme.colors.primary.copy(alpha = 0.15f) else palette.field,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = if (isCapturingHoverHotkey) MaterialTheme.colors.primary else palette.fieldBorder,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = if (isCapturingHoverHotkey) "Pressione as teclas..." else "Atalho: $hoverHotkey",
                            style = MaterialTheme.typography.body1.copy(fontWeight = FontWeight.SemiBold),
                            color = if (isCapturingHoverHotkey) palette.success else MaterialTheme.colors.onSurface
                        )
                    }

                    Button(
                        onClick = {
                            isCapturingHoverHotkey = true
                            hoverHotkeyFocusRequester.requestFocus()
                        },
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = if (isCapturingHoverHotkey) palette.field else palette.button,
                            contentColor = if (isCapturingHoverHotkey) MaterialTheme.colors.onSurface else MaterialTheme.colors.onPrimary
                        )
                    ) {
                        Text(if (isCapturingHoverHotkey) "Aguardando..." else "Capturar")
                    }

                    OutlinedButton(
                        onClick = {
                            hoverHotkey = "Ctrl + Shift + H"
                            settings.hoverHotkey = "Ctrl + Shift + H"
                            isCapturingHoverHotkey = false
                            notifyHotkeysChanged()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colors.onSurface)
                    ) {
                        Text("Padrão")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Hover Cycle Hotkey
                Text(
                    text = "Atalho para Alternar Dicionário no Hover:",
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.secondary
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(hoverCycleHotkeyFocusRequester)
                            .focusable()
                            .onKeyEvent { keyEvent ->
                                if (isCapturingHoverCycleHotkey && keyEvent.type == KeyEventType.KeyDown) {
                                    val key = keyEvent.key
                                    if (key != Key.CtrlLeft && key != Key.CtrlRight &&
                                        key != Key.ShiftLeft && key != Key.ShiftRight &&
                                        key != Key.AltLeft && key != Key.AltRight &&
                                        key != Key.MetaLeft && key != Key.MetaRight
                                    ) {
                                        val parts = mutableListOf<String>()
                                        if (keyEvent.isCtrlPressed) parts.add("Ctrl")
                                        if (keyEvent.isAltPressed) parts.add("Alt")
                                        if (keyEvent.isShiftPressed) parts.add("Shift")
                                        if (keyEvent.isMetaPressed) parts.add("Meta")

                                        val keyName = when (key) {
                                            Key.Spacebar -> "Space"
                                            Key.Enter -> "Enter"
                                            Key.Escape -> "Esc"
                                            Key.Tab -> "Tab"
                                            Key.Backspace -> "Backspace"
                                            else -> java.awt.event.KeyEvent.getKeyText(key.nativeKeyCode)
                                        }
                                        parts.add(keyName)
                                        val formatted = parts.joinToString(" + ")

                                        hoverCycleHotkey = formatted
                                        settings.hoverCycleHotkey = formatted
                                        isCapturingHoverCycleHotkey = false
                                        notifyHotkeysChanged()
                                        true
                                    } else false
                                } else false
                            }
                            .background(
                                color = if (isCapturingHoverCycleHotkey) MaterialTheme.colors.primary.copy(alpha = 0.15f) else palette.field,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = if (isCapturingHoverCycleHotkey) MaterialTheme.colors.primary else palette.fieldBorder,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = if (isCapturingHoverCycleHotkey) "Pressione as teclas..." else "Atalho: $hoverCycleHotkey",
                            style = MaterialTheme.typography.body1.copy(fontWeight = FontWeight.SemiBold),
                            color = if (isCapturingHoverCycleHotkey) palette.success else MaterialTheme.colors.onSurface
                        )
                    }

                    Button(
                        onClick = {
                            isCapturingHoverCycleHotkey = true
                            hoverCycleHotkeyFocusRequester.requestFocus()
                        },
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = if (isCapturingHoverCycleHotkey) palette.field else palette.button,
                            contentColor = if (isCapturingHoverCycleHotkey) MaterialTheme.colors.onSurface else MaterialTheme.colors.onPrimary
                        )
                    ) {
                        Text(if (isCapturingHoverCycleHotkey) "Aguardando..." else "Capturar")
                    }

                    OutlinedButton(
                        onClick = {
                            hoverCycleHotkey = "Ctrl + Shift + X"
                            settings.hoverCycleHotkey = "Ctrl + Shift + X"
                            isCapturingHoverCycleHotkey = false
                            notifyHotkeysChanged()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colors.onSurface)
                    ) {
                        Text("Padrão")
                    }
                }
            }
        }

        // --- Integração com o Linux ---
        if (onInstallLinuxIntegration != null) {
            item {
                SettingCard(title = "Integração com o Linux") {
                    Text(
                        text = linuxIntegrationStatus.ifBlank {
                            "Cria o atalho no menu de aplicativos e instala o ícone do Yabai OCR para o seu usuário."
                        },
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.secondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = onInstallLinuxIntegration,
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = palette.button,
                            contentColor = MaterialTheme.colors.onPrimary
                        )
                    ) {
                        Text("Integrar ao sistema")
                    }
                }
            }
        }

        // --- Inicialização e Comportamento do Sistema ---
        item {
            SettingCard(title = "Inicialização e Comportamento da Janela") {
                SettingCheckboxItem(
                    checked = startOnBoot,
                    title = "Iniciar na bandeja ao ligar o computador",
                    description = "Abre o aplicativo minimizado na bandeja do sistema automaticamente quando o sistema operacional inicia.",
                    onCheckedChange = {
                        startOnBoot = it
                        settings.startOnBoot = it
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                SettingCheckboxItem(
                    checked = closeToTray,
                    title = "Manter na bandeja ao fechar a janela",
                    description = "Ao fechar a janela principal, o programa continua rodando em segundo plano na bandeja para responder aos atalhos de teclado. Se desativado, o aplicativo será encerrado por completo.",
                    onCheckedChange = {
                        closeToTray = it
                        settings.closeToTray = it
                    }
                )
            }
        }

        // --- 1.1 Modo Hover ---
        item {
            SettingCard(title = "Modo Hover — leitura em tempo real (Estilo Yomitan)") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (hoverActive) {
                            "🟢 Ativo — pare o mouse sobre um texto japonês."
                        } else {
                            "⚪ Inativo — ative para traduzir só passando o mouse."
                        },
                        style = MaterialTheme.typography.body2.copy(fontWeight = FontWeight.SemiBold),
                        color = if (hoverActive) palette.success else MaterialTheme.colors.secondary
                    )

                    Button(
                        onClick = onHoverToggle,
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = if (hoverActive) palette.dangerContainer else palette.button,
                            contentColor = if (hoverActive) palette.danger else MaterialTheme.colors.onPrimary
                        )
                    ) {
                        Text(if (hoverActive) "Desativar" else "Ativar")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Dicionário Padrão do Modo Hover:",
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.secondary
                )
                Spacer(modifier = Modifier.height(4.dp))

                var expandedHoverDictFilter by remember { mutableStateOf(false) }
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { expandedHoverDictFilter = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colors.onSurface)
                    ) {
                        Text(if (hoverDictionaryFilter.isBlank()) "🌐 Todos os Dicionários (Padrão)" else "📖 $hoverDictionaryFilter")
                    }
                    DropdownMenu(
                        expanded = expandedHoverDictFilter,
                        onDismissRequest = { expandedHoverDictFilter = false }
                    ) {
                        DropdownMenuItem(onClick = {
                            hoverDictionaryFilter = ""
                            settings.hoverDictionaryFilter = ""
                            expandedHoverDictFilter = false
                        }) {
                            Text("🌐 Todos os Dicionários")
                        }
                        installedDictTitles.forEach { title ->
                            DropdownMenuItem(onClick = {
                                hoverDictionaryFilter = title
                                settings.hoverDictionaryFilter = title
                                expandedHoverDictFilter = false
                            }) {
                                Text("📖 $title")
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "O modo funciona em qualquer aplicativo: navegador, leitor de mangá, PDF ou jogo. " +
                        "Depois de ativado, basta deixar o ponteiro parado por um instante sobre a palavra; " +
                        "o cartão com leitura e definição aparece ao lado do cursor. Mover o mouse para fora " +
                        "fecha o cartão.",
                    style = MaterialTheme.typography.caption.copy(lineHeight = 17.sp),
                    color = MaterialTheme.colors.secondary
                )

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Atalho para ligar/desligar: $hoverHotkey • Alternar dicionário: $hoverCycleHotkey",
                    style = MaterialTheme.typography.caption,
                    color = palette.info
                )

                if (hoverStatus.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Status: $hoverStatus",
                        style = MaterialTheme.typography.caption,
                        color = palette.warning
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onHoverTest,
                    enabled = hoverActive,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colors.onSurface)
                ) {
                    Text("Testar no cursor atual")
                }
            }
        }

        // --- Motor de Reconhecimento ---
        item {
            SettingCard(title = "Motor de reconhecimento") {
                Text(
                    text = "Escolha como o texto em japonês será reconhecido da imagem.",
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.secondary
                )
                Spacer(modifier = Modifier.height(10.dp))

                SettingRadioOption(
                    selected = engineChoice == DesktopEngineChoice.AUTO,
                    title = "Automático (Usa Ordem de Prioridades)",
                    description = "Tenta motores em ordem de prioridade (Google Lens, Manga-OCR offline).",
                    onClick = {
                        engineChoice = DesktopEngineChoice.AUTO
                        settings.engine = DesktopEngineChoice.AUTO
                    }
                )

                SettingRadioOption(
                    selected = engineChoice == DesktopEngineChoice.MANGA_OCR,
                    title = "Somente Manga-OCR (offline)",
                    description = "Usa o motor de inteligência artificial offline especializado em texto vertical e mangás.",
                    onClick = {
                        engineChoice = DesktopEngineChoice.MANGA_OCR
                        settings.engine = DesktopEngineChoice.MANGA_OCR
                    }
                )

                SettingRadioOption(
                    selected = engineChoice == DesktopEngineChoice.LENS,
                    title = "Somente Google Lens (online)",
                    description = "Usa o motor online do Google Lens. Exige conexão ativa com a internet.",
                    onClick = {
                        engineChoice = DesktopEngineChoice.LENS
                        settings.engine = DesktopEngineChoice.LENS
                    }
                )

                Spacer(modifier = Modifier.height(14.dp))
                Divider(color = palette.fieldBorder)
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Prioridade dos Motores no modo Automático / Fallback:",
                    style = MaterialTheme.typography.subtitle2.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colors.onSurface
                )
                Text(
                    text = "Defina a ordem de preferência dos motores de reconhecimento.",
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.secondary
                )
                Spacer(modifier = Modifier.height(8.dp))

                enginePriority.forEachIndexed { index, choice ->
                    val label = when (choice) {
                        DesktopEngineChoice.MANGA_OCR -> "Manga-OCR (Offline - IA Mangá)"
                        DesktopEngineChoice.LENS -> "Google Lens (Online)"
                        else -> choice.name
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        backgroundColor = palette.field,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${index + 1}º  $label",
                                style = MaterialTheme.typography.body2.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colors.onSurface
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (index > 0) {
                                    OutlinedButton(
                                        onClick = {
                                            val list = enginePriority.toMutableList()
                                            val tmp = list[index]
                                            list[index] = list[index - 1]
                                            list[index - 1] = tmp
                                            enginePriority = list
                                            settings.enginePriorityList = list
                                        },
                                        modifier = Modifier.height(30.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                    ) {
                                        Text("▲ Subir", fontSize = 11.sp)
                                    }
                                }
                                if (index < enginePriority.size - 1) {
                                    OutlinedButton(
                                        onClick = {
                                            val list = enginePriority.toMutableList()
                                            val tmp = list[index]
                                            list[index] = list[index + 1]
                                            list[index + 1] = tmp
                                            enginePriority = list
                                            settings.enginePriorityList = list
                                        },
                                        modifier = Modifier.height(30.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                    ) {
                                        Text("▼ Descer", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- Comportamento do OCR e Resultado ---
        item {
            SettingCard(title = "Comportamento do OCR e resultado") {
                SettingCheckboxItem(
                    checked = autoCopy,
                    title = "Copiar automaticamente ao reconhecer",
                    description = "Envia o texto reconhecido diretamente para a área de transferência do sistema.",
                    onCheckedChange = {
                        autoCopy = it
                        settings.autoCopy = it
                    }
                )

                SettingCheckboxItem(
                    checked = showSegments,
                    title = "Listar o texto linha por linha",
                    description = "Exibe os segmentos e blocos de texto reconhecidos separadamente no painel.",
                    onCheckedChange = {
                        showSegments = it
                        settings.showSegments = it
                    }
                )

                SettingCheckboxItem(
                    checked = upscaleSmall,
                    title = "Ampliar recortes pequenos",
                    description = "Redimensiona automaticamente imagens recortadas pequenas para melhorar o reconhecimento de letras miúdas.",
                    onCheckedChange = {
                        upscaleSmall = it
                        settings.upscaleSmall = it
                    }
                )

                SettingCheckboxItem(
                    checked = showImage,
                    title = "Mostrar imagem capturada ao lado do texto",
                    description = "Exibe o recorte da imagem original no painel de resultado para comparação visual.",
                    onCheckedChange = {
                        showImage = it
                        settings.showImage = it
                    }
                )

                SettingCheckboxItem(
                    checked = furiganaInParentheses,
                    title = "Manter furigana original entre parênteses",
                    description = "Preserva a leitura que o mangá imprime ao lado do kanji no formato 職員（しょくいん）.",
                    onCheckedChange = {
                        furiganaInParentheses = it
                        settings.furiganaInParentheses = it
                    }
                )

                SettingCheckboxItem(
                    checked = showFurigana,
                    title = "Gerar furigana do dicionário no painel",
                    description = "Exibe acima ou ao lado dos kanjis reconhecidos as leituras fornecidas pelos dicionários.",
                    onCheckedChange = {
                        showFurigana = it
                        settings.showFurigana = it
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Leituras dos kanjis no seletor da página:",
                    style = MaterialTheme.typography.subtitle2.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colors.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))

                SettingRadioOption(
                    selected = pickerReading == DesktopPickerReadingMode.OFF,
                    title = "Desativado",
                    description = "Não exibe leituras sobre a imagem na tela de seleção.",
                    onClick = {
                        pickerReading = DesktopPickerReadingMode.OFF
                        settings.pickerReading = DesktopPickerReadingMode.OFF
                    }
                )

                SettingRadioOption(
                    selected = pickerReading == DesktopPickerReadingMode.RUBY,
                    title = "Rubi sobre o texto",
                    description = "Desenha o furigana em fonte pequena diretamente acima de cada kanji.",
                    onClick = {
                        pickerReading = DesktopPickerReadingMode.RUBY
                        settings.pickerReading = DesktopPickerReadingMode.RUBY
                    }
                )

                SettingRadioOption(
                    selected = pickerReading == DesktopPickerReadingMode.LABEL,
                    title = "Faixa indicativa legível",
                    description = "Exibe uma faixa de texto destacada ao lado dos balões de fala.",
                    onClick = {
                        pickerReading = DesktopPickerReadingMode.LABEL
                        settings.pickerReading = DesktopPickerReadingMode.LABEL
                    }
                )
            }
        }

        // --- Dicionários e Localização da Pasta ---
        item {
            SettingCard(title = "Dicionários de Japonês e Pasta de Dados") {
                SettingCheckboxItem(
                    checked = dictionaryLookup,
                    title = "Consultar dicionários instalados",
                    description = "Busca automaticamente o texto reconhecido nos dicionários ativados.",
                    onCheckedChange = {
                        dictionaryLookup = it
                        settings.dictionaryLookup = it
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Dicionário Padrão de Filtro:",
                    style = MaterialTheme.typography.subtitle2.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colors.onSurface
                )
                Text(
                    text = "Selecione um dicionário específico para limitar as buscas ou escolha 'Todos'.",
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.secondary
                )
                Spacer(modifier = Modifier.height(6.dp))

                var expandedFilter by remember { mutableStateOf(false) }
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { expandedFilter = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colors.onSurface)
                    ) {
                        Text(if (dictionaryFilter.isBlank()) "🌐 Todos os Dicionários" else "📖 $dictionaryFilter")
                    }
                    DropdownMenu(
                        expanded = expandedFilter,
                        onDismissRequest = { expandedFilter = false }
                    ) {
                        DropdownMenuItem(onClick = {
                            dictionaryFilter = ""
                            settings.dictionaryFilter = ""
                            expandedFilter = false
                        }) {
                            Text("🌐 Todos os Dicionários")
                        }
                        installedDictTitles.forEach { title ->
                            DropdownMenuItem(onClick = {
                                dictionaryFilter = title
                                settings.dictionaryFilter = title
                                expandedFilter = false
                            }) {
                                Text("📖 $title")
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Localização da Pasta de Dicionários:",
                    style = MaterialTheme.typography.subtitle2.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colors.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))

                Column(
                    modifier = Modifier.fillMaxWidth()
                        .background(palette.field, RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = customDictDir,
                        style = MaterialTheme.typography.body2,
                        color = MaterialTheme.colors.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val chooser = JFileChooser()
                                chooser.dialogTitle = "Selecionar pasta para salvar dicionários"
                                chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                                val result = chooser.showOpenDialog(null)
                                if (result == JFileChooser.APPROVE_OPTION) {
                                    val selected = chooser.selectedFile
                                    settings.customDictDirectoryPath = selected.absolutePath
                                    customDictDir = selected.absolutePath
                                }
                            },
                            colors = ButtonDefaults.buttonColors(backgroundColor = palette.button, contentColor = palette.onButton)
                        ) {
                            Text("Alterar Pasta...")
                        }

                        OutlinedButton(
                            onClick = {
                                val defaultDir = settings.defaultDictDirectory().absolutePath
                                settings.customDictDirectoryPath = defaultDir
                                customDictDir = defaultDir
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colors.onSurface)
                        ) {
                            Text("Restaurar Padrão")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = palette.field,
                    shape = RoundedCornerShape(8.dp),
                    elevation = 0.dp
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (installedDictCount == 0)
                                    "Nenhum dicionário encontrado na pasta atual"
                                else
                                    "$installedDictCount dicionário(s) encontrado(s) na pasta atual",
                                style = MaterialTheme.typography.body2,
                                color = if (installedDictCount == 0) palette.danger else MaterialTheme.colors.onSurface
                            )
                            Text(
                                text = "Arquivos .yabai ou subpastas de dicionário.",
                                style = MaterialTheme.typography.caption,
                                color = MaterialTheme.colors.secondary
                            )
                        }
                        if (onManageDictionariesRequested != null) {
                            Spacer(modifier = Modifier.width(12.dp))
                            Button(
                                onClick = onManageDictionariesRequested,
                                colors = ButtonDefaults.buttonColors(
                                    backgroundColor = palette.field,
                                    contentColor = MaterialTheme.colors.onSurface
                                )
                            ) {
                                Text("Gerenciar")
                            }
                        }
                    }
                }

                SettingCheckboxItem(
                    checked = dictionaryKeepLastSearch,
                    title = "Manter última busca no dicionário",
                    description = "Ao abrir a janela de busca rápida, preenche o campo com o último termo pesquisado.",
                    onCheckedChange = {
                        dictionaryKeepLastSearch = it
                        settings.dictionaryKeepLastSearch = it
                    }
                )

                SettingCheckboxItem(
                    checked = stripSearchReadings,
                    title = "Ignorar leituras entre parênteses na consulta",
                    description = "Remove trechos em kana entre parênteses ao pesquisar (de 「職員（しょくいん）」 busca 「職員」).",
                    onCheckedChange = {
                        stripSearchReadings = it
                        settings.stripSearchReadings = it
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Verificação de atualizações dos dicionários:",
                    style = MaterialTheme.typography.subtitle2.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colors.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DesktopUpdateInterval.entries.forEach { interval ->
                        val isSelected = dictionaryUpdateInterval == interval
                        val label = when (interval) {
                            DesktopUpdateInterval.OFF -> "Desativado"
                            DesktopUpdateInterval.DAILY -> "Diariamente"
                            DesktopUpdateInterval.WEEKLY -> "Semanalmente"
                            DesktopUpdateInterval.MONTHLY -> "Mensalmente"
                        }
                        OutlinedButton(
                            onClick = {
                                dictionaryUpdateInterval = interval
                                settings.dictionaryUpdateInterval = interval
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                backgroundColor = if (isSelected) MaterialTheme.colors.primary.copy(alpha = 0.2f) else Color.Transparent,
                                contentColor = if (isSelected) MaterialTheme.colors.primary else MaterialTheme.colors.secondary
                            ),
                            border = ButtonDefaults.outlinedBorder.copy(
                                brush = androidx.compose.ui.graphics.SolidColor(
                                    if (isSelected) MaterialTheme.colors.primary else palette.fieldBorder
                                )
                            )
                        ) {
                            Text(label, fontSize = 11.sp, maxLines = 1)
                        }
                    }
                }
            }
        }

        // --- Tradução ---
        item {
            SettingCard(title = "Tradução de Frases") {
                SettingCheckboxItem(
                    checked = translateSentence,
                    title = "Traduzir a frase reconhecida",
                    description = "Traduz automaticamente o texto completo reconhecido usando o modelo offline.",
                    onCheckedChange = {
                        translateSentence = it
                        settings.translateSentence = it
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Idioma de destino da tradução:",
                    style = MaterialTheme.typography.subtitle2.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colors.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    SettingRadioOption(
                        selected = translateTarget == "pt",
                        title = "Português (pt)",
                        description = "",
                        onClick = {
                            translateTarget = "pt"
                            settings.translateTarget = "pt"
                        },
                        modifier = Modifier.weight(1f)
                    )

                    SettingRadioOption(
                        selected = translateTarget == "en",
                        title = "Inglês (en)",
                        description = "",
                        onClick = {
                            translateTarget = "en"
                            settings.translateTarget = "en"
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // --- Perguntar a uma IA ---
        item {
            SettingCard(title = "Perguntar a uma IA") {
                SettingCheckboxItem(
                    checked = showAiButton,
                    title = "Exibir o botão «Perguntar à IA» no painel de resultado",
                    description = "Disponibiliza um atalho no resultado do OCR para enviar a frase diretamente a um serviço de Inteligência Artificial no navegador.",
                    onCheckedChange = {
                        showAiButton = it
                        settings.showAiButton = it
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Provedor / Serviço de IA:",
                    style = MaterialTheme.typography.subtitle2.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colors.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))

                SettingRadioOption(
                    selected = aiProvider == DesktopAiProvider.CHATGPT,
                    title = "ChatGPT (OpenAI)",
                    description = "Abre https://chatgpt.com/?q=...",
                    onClick = {
                        aiProvider = DesktopAiProvider.CHATGPT
                        settings.aiProvider = DesktopAiProvider.CHATGPT
                    }
                )

                SettingRadioOption(
                    selected = aiProvider == DesktopAiProvider.GEMINI,
                    title = "Google Gemini",
                    description = "Abre https://gemini.google.com/app?text=...",
                    onClick = {
                        aiProvider = DesktopAiProvider.GEMINI
                        settings.aiProvider = DesktopAiProvider.GEMINI
                    }
                )

                SettingRadioOption(
                    selected = aiProvider == DesktopAiProvider.DEEPSEEK,
                    title = "DeepSeek",
                    description = "Abre https://chat.deepseek.com/?q=...",
                    onClick = {
                        aiProvider = DesktopAiProvider.DEEPSEEK
                        settings.aiProvider = DesktopAiProvider.DEEPSEEK
                    }
                )

                SettingRadioOption(
                    selected = aiProvider == DesktopAiProvider.CLAUDE,
                    title = "Claude (Anthropic)",
                    description = "Abre https://claude.ai/new?q=...",
                    onClick = {
                        aiProvider = DesktopAiProvider.CLAUDE
                        settings.aiProvider = DesktopAiProvider.CLAUDE
                    }
                )

                SettingRadioOption(
                    selected = aiProvider == DesktopAiProvider.CUSTOM,
                    title = "Personalizado (URL com {text})",
                    description = "Digite manualmente o endereço do tradutor ou IA com o marcador {text}.",
                    onClick = {
                        aiProvider = DesktopAiProvider.CUSTOM
                        settings.aiProvider = DesktopAiProvider.CUSTOM
                    }
                )

                if (aiProvider == DesktopAiProvider.CUSTOM) {
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = aiCustomUrl,
                        onValueChange = {
                            aiCustomUrl = it
                            settings.aiCustomUrl = it
                        },
                        label = { Text("URL Personalizada (ex: https://meutradutor.com/?q={text})") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            textColor = MaterialTheme.colors.onSurface,
                            focusedBorderColor = MaterialTheme.colors.primary,
                            unfocusedBorderColor = palette.fieldBorder
                        )
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Prompt / Pergunta inicial enviada à IA:",
                    style = MaterialTheme.typography.subtitle2.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colors.onSurface
                )
                Text(
                    text = "A instrução colocada antes do texto em japonês recortado.",
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.secondary
                )
                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = aiPrompt,
                    onValueChange = {
                        aiPrompt = it
                        settings.aiPrompt = it
                    },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        textColor = MaterialTheme.colors.onSurface,
                        focusedBorderColor = MaterialTheme.colors.primary,
                        unfocusedBorderColor = palette.fieldBorder
                    )
                )
            }
        }

        // --- 7. Sobre e Atualizações do Aplicativo ---
        item {
            var isCheckingUpdate by remember { mutableStateOf(false) }
            var updateInfo by remember { mutableStateOf<AppUpdateInfo?>(null) }
            var updateStatusText by remember { mutableStateOf<String?>(null) }
            val coroutineScope = rememberCoroutineScope()

            SettingCard(title = "Sobre o Yabai OCR e Atualizações do Aplicativo") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Versão Instalada: v${DesktopAppUpdateChecker.getCurrentVersion()}",
                            style = MaterialTheme.typography.body1.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colors.onSurface
                        )
                        Text(
                            text = "Yabai OCR Desktop — Leitor OCR de Japonês com Dicionários Offline",
                            style = MaterialTheme.typography.caption,
                            color = MaterialTheme.colors.secondary
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Button(
                        enabled = !isCheckingUpdate,
                        onClick = {
                            isCheckingUpdate = true
                            updateStatusText = "Verificando lançamentos no GitHub..."
                            coroutineScope.launch(Dispatchers.IO) {
                                val info = DesktopAppUpdateChecker.checkForAppUpdate()
                                withContext(Dispatchers.Main) {
                                    isCheckingUpdate = false
                                    updateInfo = info
                                    if (info == null) {
                                        updateStatusText = "Não foi possível conectar ao GitHub para verificar atualizações."
                                    } else if (!info.hasUpdate) {
                                        updateStatusText = "Você já está usando a versão mais recente (${info.currentVersion})."
                                    } else {
                                        updateStatusText = null
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = MaterialTheme.colors.primary,
                            contentColor = MaterialTheme.colors.onPrimary
                        )
                    ) {
                        Text(if (isCheckingUpdate) "Verificando..." else "🔄 Verificar Atualizações")
                    }
                }

                if (updateInfo?.hasUpdate == true) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = palette.success.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, palette.success),
                        shape = RoundedCornerShape(8.dp),
                        elevation = 0.dp
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "✨ Nova versão do aplicativo disponível: v${updateInfo?.latestVersion}",
                                style = MaterialTheme.typography.subtitle2.copy(fontWeight = FontWeight.Bold),
                                color = palette.success
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Uma versão mais recente do Yabai OCR foi lançada no GitHub.",
                                style = MaterialTheme.typography.caption,
                                color = MaterialTheme.colors.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = {
                                    val url = updateInfo?.downloadUrl ?: updateInfo?.releaseUrl
                                        ?: "https://github.com/SorrisoKDente/yabai_ocr/releases/latest"
                                    runCatching {
                                        java.awt.Desktop.getDesktop().browse(java.net.URI(url))
                                    }
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.success)
                            ) {
                                Text(if (updateInfo?.downloadUrl != null) "⬇️ Baixar Atualização" else "🌐 Baixar Atualização no GitHub")
                            }
                        }
                    }
                } else if (updateStatusText != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = updateStatusText.orEmpty(),
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.secondary
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsWindow(
    onClose: () -> Unit,
    onManageDictionariesRequested: (() -> Unit)? = null
) {
    Window(
        onCloseRequest = onClose,
        title = "Yabai OCR - Ajustes e Configurações",
        state = WindowState(width = 680.dp, height = 820.dp)
    ) {
        LaunchedEffect(Unit) {
            window.iconImage = AppIconGenerator.createWindowImage(64)
        }

        YabaiDesktopTheme(darkTheme = remember { DesktopSettings().darkTheme }) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colors.background
            ) {
                SettingsContent(onManageDictionariesRequested = onManageDictionariesRequested)
            }
        }
    }
}

@Composable
private fun SettingCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = MaterialTheme.colors.surface,
        shape = RoundedCornerShape(12.dp),
        elevation = if (MaterialTheme.colors.isLight) 1.dp else 0.dp
    ) {
        Column(
            modifier = Modifier.padding(18.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.h6.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                ),
                color = if (MaterialTheme.colors.isLight) Color(0xFF2B4FC4) else Color(0xFF5D84FF)
            )
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun SettingCheckboxItem(
    checked: Boolean,
    title: String,
    description: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(
                checkedColor = MaterialTheme.colors.primary,
                uncheckedColor = if (MaterialTheme.colors.isLight) Color(0xFFB0B5C0) else Color(0xFF43485A)
            )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.body1.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colors.onSurface
            )
            if (description.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.secondary
                )
            }
        }
    }
}

@Composable
private fun SettingRadioOption(
    selected: Boolean,
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = MaterialTheme.colors.primary,
                unselectedColor = if (MaterialTheme.colors.isLight) Color(0xFFB0B5C0) else Color(0xFF43485A)
            )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.body1.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colors.onSurface
            )
            if (description.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.secondary
                )
            }
        }
    }
}
