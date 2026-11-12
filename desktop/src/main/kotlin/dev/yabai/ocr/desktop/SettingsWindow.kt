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
import dev.yabai.ocr.desktop.i18n.DesktopStrings
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
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = DesktopStrings.appTitle,
                    style = MaterialTheme.typography.h4.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colors.onBackground
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = DesktopStrings.heroSubtitle,
                    style = MaterialTheme.typography.body2.copy(lineHeight = 20.sp),
                    color = MaterialTheme.colors.secondary
                )
            }
        }

        // --- Aparência e Tema ---
        item {
            SettingCard(title = DesktopStrings.sectionAppearance) {
                Text(
                    text = DesktopStrings.chooseThemeDesc,
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.secondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    SettingRadioOption(
                        selected = darkTheme,
                        title = DesktopStrings.themeDark,
                        description = DesktopStrings.darkThemeDesc,
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
                        title = DesktopStrings.themeLight,
                        description = DesktopStrings.lightThemeDesc,
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

        // --- Idioma da Interface ---
        item {
            var appLang by remember { mutableStateOf(settings.appLanguage) }
            SettingCard(title = DesktopStrings.sectionLanguage) {
                Text(
                    text = DesktopStrings.chooseLanguageDesc,
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.secondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    SettingRadioOption(
                        selected = appLang == DesktopLanguageChoice.SYSTEM,
                        title = DesktopStrings.langSystem,
                        description = DesktopStrings.langSystemDesc,
                        onClick = {
                            if (appLang != DesktopLanguageChoice.SYSTEM) {
                                appLang = DesktopLanguageChoice.SYSTEM
                                settings.appLanguage = DesktopLanguageChoice.SYSTEM
                                DesktopStrings.updateLanguage(DesktopLanguageChoice.SYSTEM)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )

                    SettingRadioOption(
                        selected = appLang == DesktopLanguageChoice.PT_BR,
                        title = DesktopStrings.langPtBr,
                        description = DesktopStrings.langPtBrDesc,
                        onClick = {
                            if (appLang != DesktopLanguageChoice.PT_BR) {
                                appLang = DesktopLanguageChoice.PT_BR
                                settings.appLanguage = DesktopLanguageChoice.PT_BR
                                DesktopStrings.updateLanguage(DesktopLanguageChoice.PT_BR)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )

                    SettingRadioOption(
                        selected = appLang == DesktopLanguageChoice.EN_US,
                        title = DesktopStrings.langEnUs,
                        description = DesktopStrings.langEnUsDesc,
                        onClick = {
                            if (appLang != DesktopLanguageChoice.EN_US) {
                                appLang = DesktopLanguageChoice.EN_US
                                settings.appLanguage = DesktopLanguageChoice.EN_US
                                DesktopStrings.updateLanguage(DesktopLanguageChoice.EN_US)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // --- Atalhos Globais ---
        item {
            SettingCard(title = DesktopStrings.sectionHotkeys) {
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
                    text = DesktopStrings.hkFullScreen,
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
                            text = if (isCapturingHotkey) DesktopStrings.btnPressKeys else "Atalho: $globalHotkey",
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
                        Text(if (isCapturingHotkey) DesktopStrings.btnPressKeys else DesktopStrings.btnCapture)
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
                        Text(DesktopStrings.btnDefault)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Region Crop Hotkey
                Text(
                    text = DesktopStrings.hkRegion,
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
                            text = if (isCapturingRegionHotkey) DesktopStrings.btnPressKeys else "Atalho: $regionHotkey",
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
                        Text(if (isCapturingRegionHotkey) DesktopStrings.btnPressKeys else DesktopStrings.btnCapture)
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
                        Text(DesktopStrings.btnDefault)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Quick Dict Hotkey
                Text(
                    text = DesktopStrings.hkQuickDict,
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
                            text = if (isCapturingQuickDictHotkey) DesktopStrings.btnPressKeys else "Atalho: $quickDictHotkey",
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
                        Text(if (isCapturingQuickDictHotkey) DesktopStrings.btnPressKeys else DesktopStrings.btnCapture)
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
                        Text(DesktopStrings.btnDefault)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Hover Hotkey
                Text(
                    text = DesktopStrings.hkHoverToggle,
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
                            text = if (isCapturingHoverHotkey) DesktopStrings.btnPressKeys else "Atalho: $hoverHotkey",
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
                        Text(if (isCapturingHoverHotkey) DesktopStrings.btnPressKeys else DesktopStrings.btnCapture)
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
                        Text(DesktopStrings.btnDefault)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Hover Cycle Hotkey
                Text(
                    text = DesktopStrings.hkHoverCycle,
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
                            text = if (isCapturingHoverCycleHotkey) DesktopStrings.btnPressKeys else "Atalho: $hoverCycleHotkey",
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
                        Text(if (isCapturingHoverCycleHotkey) DesktopStrings.btnPressKeys else DesktopStrings.btnCapture)
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
                        Text(DesktopStrings.btnDefault)
                    }
                }
            }
        }

        // --- Integração com o Linux ---
        if (onInstallLinuxIntegration != null) {
            item {
                SettingCard(title = DesktopStrings.sectionLinuxIntegration) {
                    Text(
                        text = linuxIntegrationStatus.ifBlank {
                            DesktopStrings.linuxIntegrationDesc
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
                        Text(DesktopStrings.btnIntegrateSystem)
                    }
                }
            }
        }

        // --- Inicialização e Comportamento do Sistema ---
        item {
            SettingCard(title = DesktopStrings.sectionStartup) {
                SettingCheckboxItem(
                    checked = startOnBoot,
                    title = DesktopStrings.startOnBoot,
                    description = DesktopStrings.startOnBootDesc,
                    onCheckedChange = {
                        startOnBoot = it
                        settings.startOnBoot = it
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                SettingCheckboxItem(
                    checked = closeToTray,
                    title = DesktopStrings.closeToTray,
                    description = DesktopStrings.closeToTrayDesc,
                    onCheckedChange = {
                        closeToTray = it
                        settings.closeToTray = it
                    }
                )
            }
        }

        // --- 1.1 Modo Hover ---
        item {
            SettingCard(title = DesktopStrings.sectionHover) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (hoverActive) {
                            DesktopStrings.hoverActive
                        } else {
                            DesktopStrings.hoverInactive
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
                        Text(if (hoverActive) DesktopStrings.btnDisable else DesktopStrings.btnEnable)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = DesktopStrings.hoverDefaultDict,
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
                        Text(if (hoverDictionaryFilter.isBlank()) DesktopStrings.hoverAllDicts else "📖 $hoverDictionaryFilter")
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
                            Text(DesktopStrings.hoverAllDicts)
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
                    text = DesktopStrings.hoverExplanation,
                    style = MaterialTheme.typography.caption.copy(lineHeight = 17.sp),
                    color = MaterialTheme.colors.secondary
                )

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = DesktopStrings.hoverShortcutsNotice(hoverHotkey, hoverCycleHotkey),
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
                    Text(DesktopStrings.btnTestHoverCursor)
                }
            }
        }

        // --- Motor de Reconhecimento ---
        item {
            SettingCard(title = DesktopStrings.sectionEngine) {
                Text(
                    text = DesktopStrings.engineSubtitle,
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.secondary
                )
                Spacer(modifier = Modifier.height(10.dp))

                SettingRadioOption(
                    selected = engineChoice == DesktopEngineChoice.AUTO,
                    title = DesktopStrings.engineAuto,
                    description = DesktopStrings.engineAutoDesc,
                    onClick = {
                        engineChoice = DesktopEngineChoice.AUTO
                        settings.engine = DesktopEngineChoice.AUTO
                    }
                )

                SettingRadioOption(
                    selected = engineChoice == DesktopEngineChoice.MANGA_OCR,
                    title = DesktopStrings.engineMangaOcr,
                    description = DesktopStrings.engineMangaOcrDesc,
                    onClick = {
                        engineChoice = DesktopEngineChoice.MANGA_OCR
                        settings.engine = DesktopEngineChoice.MANGA_OCR
                    }
                )

                SettingRadioOption(
                    selected = engineChoice == DesktopEngineChoice.LENS,
                    title = DesktopStrings.engineLens,
                    description = DesktopStrings.engineLensDesc,
                    onClick = {
                        engineChoice = DesktopEngineChoice.LENS
                        settings.engine = DesktopEngineChoice.LENS
                    }
                )

                Spacer(modifier = Modifier.height(14.dp))
                Divider(color = palette.fieldBorder)
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = DesktopStrings.enginePriorityTitle,
                    style = MaterialTheme.typography.subtitle2.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colors.onSurface
                )
                Text(
                    text = DesktopStrings.enginePriorityDesc,
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.secondary
                )
                Spacer(modifier = Modifier.height(8.dp))

                enginePriority.forEachIndexed { index, choice ->
                    val label = when (choice) {
                        DesktopEngineChoice.MANGA_OCR -> if (DesktopStrings.isEnglish) "Manga-OCR (Offline - Manga AI)" else "Manga-OCR (Offline - IA Mangá)"
                        DesktopEngineChoice.LENS -> if (DesktopStrings.isEnglish) "Google Lens (Online)" else "Google Lens (Online)"
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
                                        Text(DesktopStrings.btnMoveUp, fontSize = 11.sp)
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
                                        Text(DesktopStrings.btnMoveDown, fontSize = 11.sp)
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
            SettingCard(title = DesktopStrings.sectionBehaviour) {
                SettingCheckboxItem(
                    checked = autoCopy,
                    title = DesktopStrings.autoCopy,
                    description = DesktopStrings.autoCopyDesc,
                    onCheckedChange = {
                        autoCopy = it
                        settings.autoCopy = it
                    }
                )

                SettingCheckboxItem(
                    checked = showSegments,
                    title = DesktopStrings.showSegments,
                    description = DesktopStrings.showSegmentsDesc,
                    onCheckedChange = {
                        showSegments = it
                        settings.showSegments = it
                    }
                )

                SettingCheckboxItem(
                    checked = upscaleSmall,
                    title = DesktopStrings.upscaleSmall,
                    description = DesktopStrings.upscaleSmallDesc,
                    onCheckedChange = {
                        upscaleSmall = it
                        settings.upscaleSmall = it
                    }
                )

                SettingCheckboxItem(
                    checked = showImage,
                    title = DesktopStrings.showImage,
                    description = DesktopStrings.showImageDesc,
                    onCheckedChange = {
                        showImage = it
                        settings.showImage = it
                    }
                )

                SettingCheckboxItem(
                    checked = furiganaInParentheses,
                    title = DesktopStrings.furiganaParentheses,
                    description = DesktopStrings.furiganaParenthesesDesc,
                    onCheckedChange = {
                        furiganaInParentheses = it
                        settings.furiganaInParentheses = it
                    }
                )

                SettingCheckboxItem(
                    checked = showFurigana,
                    title = DesktopStrings.showFurigana,
                    description = DesktopStrings.showFuriganaDesc,
                    onCheckedChange = {
                        showFurigana = it
                        settings.showFurigana = it
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = DesktopStrings.pickerReadingTitle,
                    style = MaterialTheme.typography.subtitle2.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colors.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))

                SettingRadioOption(
                    selected = pickerReading == DesktopPickerReadingMode.OFF,
                    title = DesktopStrings.pickerReadingOff,
                    description = DesktopStrings.pickerReadingOffDesc,
                    onClick = {
                        pickerReading = DesktopPickerReadingMode.OFF
                        settings.pickerReading = DesktopPickerReadingMode.OFF
                    }
                )

                SettingRadioOption(
                    selected = pickerReading == DesktopPickerReadingMode.RUBY,
                    title = DesktopStrings.pickerReadingRuby,
                    description = DesktopStrings.pickerReadingRubyDesc,
                    onClick = {
                        pickerReading = DesktopPickerReadingMode.RUBY
                        settings.pickerReading = DesktopPickerReadingMode.RUBY
                    }
                )

                SettingRadioOption(
                    selected = pickerReading == DesktopPickerReadingMode.LABEL,
                    title = DesktopStrings.pickerReadingLabel,
                    description = DesktopStrings.pickerReadingLabelDesc,
                    onClick = {
                        pickerReading = DesktopPickerReadingMode.LABEL
                        settings.pickerReading = DesktopPickerReadingMode.LABEL
                    }
                )
            }
        }

        // --- Dicionários e Localização da Pasta ---
        item {
            SettingCard(title = DesktopStrings.sectionDicts) {
                SettingCheckboxItem(
                    checked = dictionaryLookup,
                    title = DesktopStrings.dictionaryLookup,
                    description = DesktopStrings.dictionaryLookupDesc,
                    onCheckedChange = {
                        dictionaryLookup = it
                        settings.dictionaryLookup = it
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = DesktopStrings.defaultDictFilter,
                    style = MaterialTheme.typography.subtitle2.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colors.onSurface
                )
                Text(
                    text = DesktopStrings.defaultDictFilterDesc,
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
                        Text(if (dictionaryFilter.isBlank()) DesktopStrings.allDictionaries else "📖 $dictionaryFilter")
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
                            Text(DesktopStrings.allDictionaries)
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
                    text = DesktopStrings.dictFolderLocation,
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
                                chooser.dialogTitle = DesktopStrings.selectDictFolderDialogTitle
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
                            Text(DesktopStrings.btnChangeFolder)
                        }

                        OutlinedButton(
                            onClick = {
                                val defaultDir = settings.defaultDictDirectory().absolutePath
                                settings.customDictDirectoryPath = defaultDir
                                customDictDir = defaultDir
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colors.onSurface)
                        ) {
                            Text(DesktopStrings.btnRestoreDefaultFolder)
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
                                    DesktopStrings.dictSummaryNone
                                else
                                    DesktopStrings.dictSummaryCount(installedDictCount),
                                style = MaterialTheme.typography.body2,
                                color = if (installedDictCount == 0) palette.danger else MaterialTheme.colors.onSurface
                            )
                            Text(
                                text = DesktopStrings.dictSummaryNote,
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
                                Text(DesktopStrings.btnManageDicts)
                            }
                        }
                    }
                }

                SettingCheckboxItem(
                    checked = dictionaryKeepLastSearch,
                    title = DesktopStrings.keepLastSearch,
                    description = DesktopStrings.keepLastSearchDesc,
                    onCheckedChange = {
                        dictionaryKeepLastSearch = it
                        settings.dictionaryKeepLastSearch = it
                    }
                )

                SettingCheckboxItem(
                    checked = stripSearchReadings,
                    title = DesktopStrings.stripSearchReadings,
                    description = DesktopStrings.stripSearchReadingsDesc,
                    onCheckedChange = {
                        stripSearchReadings = it
                        settings.stripSearchReadings = it
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = DesktopStrings.dictUpdateInterval,
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
                            DesktopUpdateInterval.OFF -> DesktopStrings.updateOff
                            DesktopUpdateInterval.DAILY -> DesktopStrings.updateDaily
                            DesktopUpdateInterval.WEEKLY -> DesktopStrings.updateWeekly
                            DesktopUpdateInterval.MONTHLY -> DesktopStrings.updateMonthly
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
            SettingCard(title = DesktopStrings.sectionTranslation) {
                SettingCheckboxItem(
                    checked = translateSentence,
                    title = DesktopStrings.translateSentence,
                    description = DesktopStrings.translateSentenceDesc,
                    onCheckedChange = {
                        translateSentence = it
                        settings.translateSentence = it
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = DesktopStrings.translateTarget,
                    style = MaterialTheme.typography.subtitle2.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colors.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    SettingRadioOption(
                        selected = translateTarget == "pt",
                        title = DesktopStrings.translateTargetPortuguese,
                        description = "",
                        onClick = {
                            translateTarget = "pt"
                            settings.translateTarget = "pt"
                        },
                        modifier = Modifier.weight(1f)
                    )

                    SettingRadioOption(
                        selected = translateTarget == "en",
                        title = DesktopStrings.translateTargetEnglish,
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
            SettingCard(title = DesktopStrings.sectionAi) {
                SettingCheckboxItem(
                    checked = showAiButton,
                    title = DesktopStrings.showAiButton,
                    description = DesktopStrings.showAiButtonDesc,
                    onCheckedChange = {
                        showAiButton = it
                        settings.showAiButton = it
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = DesktopStrings.aiProvider,
                    style = MaterialTheme.typography.subtitle2.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colors.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))

                SettingRadioOption(
                    selected = aiProvider == DesktopAiProvider.CHATGPT,
                    title = "ChatGPT (OpenAI)",
                    description = DesktopStrings.aiOpensUrl("https://chatgpt.com/?q=..."),
                    onClick = {
                        aiProvider = DesktopAiProvider.CHATGPT
                        settings.aiProvider = DesktopAiProvider.CHATGPT
                    }
                )

                SettingRadioOption(
                    selected = aiProvider == DesktopAiProvider.GEMINI,
                    title = "Google Gemini",
                    description = DesktopStrings.aiOpensUrl("https://gemini.google.com/app?text=..."),
                    onClick = {
                        aiProvider = DesktopAiProvider.GEMINI
                        settings.aiProvider = DesktopAiProvider.GEMINI
                    }
                )

                SettingRadioOption(
                    selected = aiProvider == DesktopAiProvider.DEEPSEEK,
                    title = "DeepSeek",
                    description = DesktopStrings.aiOpensUrl("https://chat.deepseek.com/?q=..."),
                    onClick = {
                        aiProvider = DesktopAiProvider.DEEPSEEK
                        settings.aiProvider = DesktopAiProvider.DEEPSEEK
                    }
                )

                SettingRadioOption(
                    selected = aiProvider == DesktopAiProvider.CLAUDE,
                    title = "Claude (Anthropic)",
                    description = DesktopStrings.aiOpensUrl("https://claude.ai/new?q=..."),
                    onClick = {
                        aiProvider = DesktopAiProvider.CLAUDE
                        settings.aiProvider = DesktopAiProvider.CLAUDE
                    }
                )

                SettingRadioOption(
                    selected = aiProvider == DesktopAiProvider.CUSTOM,
                    title = DesktopStrings.aiCustomTitle,
                    description = DesktopStrings.aiCustomDesc,
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
                        label = { Text(DesktopStrings.aiCustomUrlLabel) },
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
                    text = DesktopStrings.aiPrompt,
                    style = MaterialTheme.typography.subtitle2.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colors.onSurface
                )
                Text(
                    text = DesktopStrings.aiPromptDesc,
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

            SettingCard(title = DesktopStrings.sectionAbout) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${DesktopStrings.installedVersion} v${DesktopAppUpdateChecker.getCurrentVersion()}",
                            style = MaterialTheme.typography.body1.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colors.onSurface
                        )
                        Text(
                            text = DesktopStrings.aboutSubtitle,
                            style = MaterialTheme.typography.caption,
                            color = MaterialTheme.colors.secondary
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Button(
                        enabled = !isCheckingUpdate,
                        onClick = {
                            isCheckingUpdate = true
                            updateStatusText = DesktopStrings.checkingUpdates
                            coroutineScope.launch(Dispatchers.IO) {
                                val info = DesktopAppUpdateChecker.checkForAppUpdate()
                                withContext(Dispatchers.Main) {
                                    isCheckingUpdate = false
                                    updateInfo = info
                                    if (info == null) {
                                        updateStatusText = DesktopStrings.updateConnectError
                                    } else if (!info.hasUpdate) {
                                        updateStatusText = DesktopStrings.upToDateMessage(info.currentVersion)
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
                        Text(if (isCheckingUpdate) "Verificando..." else DesktopStrings.btnCheckUpdates)
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
                                text = DesktopStrings.updateAvailableMessage(updateInfo?.latestVersion.orEmpty()),
                                style = MaterialTheme.typography.subtitle2.copy(fontWeight = FontWeight.Bold),
                                color = palette.success
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = DesktopStrings.updateGeneralNotice,
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
                                Text(if (updateInfo?.downloadUrl != null) DesktopStrings.btnDownloadUpdate else DesktopStrings.btnDownloadOnGithub)
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
        title = DesktopStrings.settingsWindowTitle,
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
