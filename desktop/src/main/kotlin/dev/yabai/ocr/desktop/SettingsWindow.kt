package dev.yabai.ocr.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
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
import dev.yabai.ocr.core.DonationInfo
import dev.yabai.ocr.core.ppocr.PPOcrModelDownloader
import dev.yabai.ocr.desktop.i18n.DesktopStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.EventQueue
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.nio.file.Path
import javax.swing.JFileChooser
import kotlin.system.exitProcess

@Composable
fun SettingsContent(
    settings: DesktopSettings = remember { DesktopSettings() },
    onManageDictionariesRequested: (() -> Unit)? = null,
    /** The renderer this run was moved to after a native crash, or null when it was not. */
    rendererAfterCrash: SkikoRenderer? = null,
    hoverActive: Boolean = false,
    hoverStatus: String = "",
    hotkeyBackendStatus: String = "",
    trayStatus: String = "",
    linuxIntegrationStatus: String = "",
    onInstallLinuxIntegration: (() -> Unit)? = null,
    kdeCapturePermission: Boolean = false,
    kdeCapturePermissionNote: String = "",
    onKdeCapturePermissionChange: ((Boolean) -> Unit)? = null,
    onHoverToggle: () -> Unit = {},
    /** Called with the new rest time, so the running hover takes it without a restart. */
    onHoverRestChanged: (Long) -> Unit = {},
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
    // Skiko picks the renderer while the process loads, so this choice is for the next start.
    var rendererChoice by remember { mutableStateOf(settings.skikoRenderer) }
    // On Linux the card offers one switch instead of a radio list: software on, graphics card off.
    // The stored `opengl`/`automatic` both mean «graphics card» there, so both leave it off (D83).
    var softwareRendering by remember { mutableStateOf(softwareSwitchOn(settings.skikoRenderer)) }

    // PP-OCR install state: the models are ~23 MB and are not shipped with the app.
    val ppOcrScope = rememberCoroutineScope()
    var ppOcrInstalled by remember { mutableStateOf(DesktopPPOcr.isInstalled(settings)) }
    var ppOcrDownloading by remember { mutableStateOf(false) }
    var ppOcrStatus by remember {
        mutableStateOf(
            if (DesktopPPOcr.isInstalled(settings)) DesktopStrings.ppOcrInstalled
            else DesktopStrings.ppOcrNotInstalled,
        )
    }
    var autoCopy by remember { mutableStateOf(settings.autoCopy) }
    var checkUpdatesOnStart by remember { mutableStateOf(settings.checkUpdatesOnStart) }
    // Version + notes of the release the user asked to read; null closes the preview window.
    var releaseNotesPreview by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showLicense by remember { mutableStateOf(false) }
    var startOnBoot by remember { mutableStateOf(settings.startOnBoot) }
    var closeToTray by remember { mutableStateOf(settings.closeToTray) }
    var showSegments by remember { mutableStateOf(settings.showSegments) }
    var upscaleSmall by remember { mutableStateOf(settings.upscaleSmall) }
    var showImage by remember { mutableStateOf(settings.showImage) }
    var furiganaInParentheses by remember { mutableStateOf(settings.furiganaInParentheses) }
    var showFurigana by remember { mutableStateOf(settings.showFurigana) }
    var pickerReading by remember { mutableStateOf(settings.pickerReading) }

    var captureMonitor by remember { mutableStateOf(settings.captureMonitor) }
    var expandedCaptureMonitor by remember { mutableStateOf(false) }
    // Read once: the settings window is not the place to poll the compositor, and the list only
    // changes when a monitor is plugged or unplugged.
    val captureDisplayList = remember { ScreenCapture.displays() }

    var dictionaryLookup by remember { mutableStateOf(settings.dictionaryLookup) }
    var dictionaryFilter by remember { mutableStateOf(settings.dictionaryFilter) }
    var hoverDictionaryFilter by remember { mutableStateOf(settings.hoverDictionaryFilter) }
    var hoverRestMillis by remember { mutableStateOf(settings.hoverRestMillis) }
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
        DesktopDictLoader.installedTitles(settings.dictDirectory())
    }

    val installedDictCount = installedDictTitles.size

    ScrollableLazyColumn(
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

        // --- Captura silenciosa no KDE Wayland ---
        // KWin refuses ScreenShot2 to a caller installed under ~/.local or /tmp, and an AppImage
        // always runs from /tmp/.mount_*: this switch is the only way for hover to capture without
        // a permission dialog on every read.
        if (onKdeCapturePermissionChange != null) {
            item {
                SettingCard(title = DesktopStrings.kdeCapturePermissionTitle) {
                    Text(
                        text = DesktopStrings.kdeCapturePermissionDesc,
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.secondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingCheckboxItem(
                        checked = kdeCapturePermission,
                        title = DesktopStrings.kdeCapturePermissionToggle,
                        description = DesktopStrings.kdeCapturePermissionHint,
                        onCheckedChange = onKdeCapturePermissionChange
                    )
                    val note = kdeCapturePermissionNote
                    if (note.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = note,
                            style = MaterialTheme.typography.caption,
                            color = palette.info
                        )
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

                if (trayStatus.isNotBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = trayStatus,
                        style = MaterialTheme.typography.caption.copy(lineHeight = 17.sp),
                        color = palette.warning
                    )
                }
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
                        // Without the weight the status text claims its whole natural width and the
                        // button is the one that shrinks when the window narrows.
                        modifier = Modifier.weight(1f),
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

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = DesktopStrings.hoverRestTitle,
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.secondary
                )
                Spacer(modifier = Modifier.height(4.dp))

                var expandedHoverRest by remember { mutableStateOf(false) }
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { expandedHoverRest = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colors.onSurface)
                    ) {
                        Text(DesktopStrings.hoverRestOption(hoverRestMillis))
                    }
                    DropdownMenu(
                        expanded = expandedHoverRest,
                        onDismissRequest = { expandedHoverRest = false }
                    ) {
                        HOVER_REST_PRESETS.forEach { preset ->
                            DropdownMenuItem(onClick = {
                                hoverRestMillis = preset
                                expandedHoverRest = false
                                // Persists the preference and retunes the gate that is already running.
                                onHoverRestChanged(preset)
                            }) {
                                Text(DesktopStrings.hoverRestOption(preset))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = DesktopStrings.hoverRestDesc,
                    style = MaterialTheme.typography.caption.copy(lineHeight = 17.sp),
                    color = MaterialTheme.colors.secondary
                )

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
            }
        }

        // --- Captura de Tela: qual monitor ---
        item {
            SettingCard(title = DesktopStrings.sectionCapture) {
                Text(
                    text = DesktopStrings.captureMonitorDesc,
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.secondary
                )
                Spacer(modifier = Modifier.height(8.dp))

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { expandedCaptureMonitor = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colors.onSurface)
                    ) {
                        Text(
                            captureMonitorLabel(
                                preference = captureMonitor,
                                displays = captureDisplayList,
                                primaryLabel = DesktopStrings.captureMonitorPrimary,
                                mouseLabel = DesktopStrings.captureMonitorMouse,
                            )
                        )
                    }
                    DropdownMenu(
                        expanded = expandedCaptureMonitor,
                        onDismissRequest = { expandedCaptureMonitor = false }
                    ) {
                        DropdownMenuItem(onClick = {
                            captureMonitor = CAPTURE_MONITOR_PRIMARY
                            settings.captureMonitor = CAPTURE_MONITOR_PRIMARY
                            expandedCaptureMonitor = false
                        }) {
                            Text(DesktopStrings.captureMonitorPrimary)
                        }
                        DropdownMenuItem(onClick = {
                            captureMonitor = CAPTURE_MONITOR_MOUSE
                            settings.captureMonitor = CAPTURE_MONITOR_MOUSE
                            expandedCaptureMonitor = false
                        }) {
                            Text(DesktopStrings.captureMonitorMouse)
                        }
                        captureDisplayList.forEach { display ->
                            val value = captureMonitorDisplayPreference(display.index)
                            DropdownMenuItem(onClick = {
                                captureMonitor = value
                                settings.captureMonitor = value
                                expandedCaptureMonitor = false
                            }) {
                                Text(
                                    DesktopStrings.captureMonitorDisplay(
                                        display.label,
                                        display.bounds.width,
                                        display.bounds.height,
                                    )
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = DesktopStrings.captureMonitorHint,
                    style = MaterialTheme.typography.caption.copy(lineHeight = 17.sp),
                    color = MaterialTheme.colors.secondary
                )
            }
        }

        // --- Renderizador gráfico ---
        item {
            SettingCard(title = DesktopStrings.sectionRenderer) {
                Text(
                    text = DesktopStrings.rendererSubtitle,
                    style = MaterialTheme.typography.caption.copy(lineHeight = 17.sp),
                    color = MaterialTheme.colors.secondary
                )
                Spacer(modifier = Modifier.height(10.dp))

                if (DesktopPlatform.isLinux) {
                    // «Automatic» and «OpenGL» are the same renderer on Linux, so a radio list was
                    // a choice that does not exist — and one loose radio showed up unselected while
                    // the stored default was Automatic. A switch states the one real decision.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val on = !softwareRendering
                                softwareRendering = on
                                val level = rendererAfterSoftwareSwitch(on, settings.skikoRenderer)
                                rendererChoice = level
                                settings.skikoRenderer = level
                            }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Switch(
                            checked = softwareRendering,
                            onCheckedChange = { on ->
                                softwareRendering = on
                                val level = rendererAfterSoftwareSwitch(on, settings.skikoRenderer)
                                rendererChoice = level
                                settings.skikoRenderer = level
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colors.onPrimary,
                                checkedTrackColor = MaterialTheme.colors.primary,
                                uncheckedThumbColor = if (MaterialTheme.colors.isLight) Color(0xFFB0B5C0) else Color(0xFF43485A),
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = DesktopStrings.rendererSoftwareToggle,
                                style = MaterialTheme.typography.body1.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colors.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = DesktopStrings.rendererSoftwareToggleDesc,
                                style = MaterialTheme.typography.caption,
                                color = MaterialTheme.colors.secondary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = DesktopStrings.rendererLinuxNote,
                        style = MaterialTheme.typography.caption.copy(lineHeight = 17.sp),
                        color = MaterialTheme.colors.secondary
                    )
                } else {
                    SkikoRenderer.entries.forEach { level ->
                        SettingRadioOption(
                            selected = rendererChoice == level,
                            title = DesktopStrings.rendererName(level),
                            description = DesktopStrings.rendererDescription(level),
                            onClick = {
                                rendererChoice = level
                                settings.skikoRenderer = level
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = DesktopStrings.rendererRestartNote,
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.secondary
                )
                if (rendererAfterCrash != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = DesktopStrings.rendererAfterCrash(
                            DesktopStrings.rendererName(rendererAfterCrash),
                        ),
                        style = MaterialTheme.typography.caption.copy(lineHeight = 17.sp),
                        color = yabaiPalette().danger
                    )
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
                    selected = engineChoice == DesktopEngineChoice.PP_OCR,
                    title = DesktopStrings.enginePpOcr,
                    description = DesktopStrings.enginePpOcrDesc,
                    onClick = {
                        engineChoice = DesktopEngineChoice.PP_OCR
                        settings.engine = DesktopEngineChoice.PP_OCR
                    }
                )

                // The offline engine needs 23 MB of models that are not shipped with the app; the
                // choice is only usable once they are on disk, so the download lives right here.
                Row(
                    modifier = Modifier.padding(start = 34.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (!ppOcrInstalled) {
                        OutlinedButton(
                            enabled = !ppOcrDownloading,
                            onClick = {
                                ppOcrDownloading = true
                                ppOcrStatus = DesktopStrings.ppOcrDownloading(0)
                                ppOcrScope.launch {
                                    try {
                                        PPOcrModelDownloader(DesktopPPOcr.store(settings)).downloadModels { _, read, total ->
                                            val percent = if (total > 0) ((read * 100) / total).toInt() else 0
                                            ppOcrStatus = DesktopStrings.ppOcrDownloading(percent)
                                        }
                                        // The files on disk changed: an engine built from a missing
                                        // or partial set must not answer for them.
                                        DesktopPPOcr.close()
                                        ppOcrInstalled = DesktopPPOcr.isInstalled(settings)
                                        ppOcrStatus = if (ppOcrInstalled) {
                                            DesktopStrings.ppOcrInstalled
                                        } else {
                                            DesktopStrings.ppOcrNotInstalled
                                        }
                                    } catch (t: Throwable) {
                                        ppOcrStatus = DesktopStrings.ppOcrDownloadFailed +
                                            (t.message ?: t.javaClass.simpleName)
                                    } finally {
                                        ppOcrDownloading = false
                                    }
                                }
                            }
                        ) {
                            Text(DesktopStrings.ppOcrDownload)
                        }
                    }
                    Text(
                        text = ppOcrStatus,
                        style = MaterialTheme.typography.caption,
                        color = if (ppOcrInstalled) MaterialTheme.colors.secondary else MaterialTheme.colors.error
                    )
                }

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
                        DesktopEngineChoice.PP_OCR -> if (DesktopStrings.isEnglish) "PP-OCRv6 (Offline - Manga AI)" else "PP-OCRv6 (Offline - IA Mangá)"
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
                                modifier = Modifier.weight(1f),
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

                Spacer(modifier = Modifier.height(8.dp))
                SettingCheckboxItem(
                    checked = checkUpdatesOnStart,
                    title = DesktopStrings.checkUpdatesOnStart,
                    description = DesktopStrings.checkUpdatesOnStartDesc,
                    onCheckedChange = {
                        checkUpdatesOnStart = it
                        settings.checkUpdatesOnStart = it
                    }
                )

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
                            val downloadScope = rememberCoroutineScope()
                            var downloadError by remember { mutableStateOf<String?>(null) }
                            var isInstallingUpdate by remember { mutableStateOf(false) }
                            var installProgress by remember { mutableStateOf<Int?>(null) }
                            var installMessage by remember { mutableStateOf<String?>(null) }
                            // Set once the AppImage on disk is the new build: only a restart is left.
                            var readyToRestart by remember { mutableStateOf<Path?>(null) }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        val info = updateInfo
                                        if (info != null) {
                                            releaseNotesPreview = info.latestVersion to info.releaseNotes
                                        }
                                    },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.success)
                                ) {
                                    Text(DesktopStrings.btnViewReleaseNotes)
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                enabled = !isInstallingUpdate && readyToRestart == null,
                                onClick = {
                                    val info = updateInfo ?: return@OutlinedButton
                                    val url = info.downloadUrl
                                    if (url == null) {
                                        // No asset for this platform: the release page is all there is.
                                        downloadScope.launch {
                                            val opened = withContext(Dispatchers.IO) {
                                                DesktopUrlOpener().open(info.releaseUrl)
                                            }
                                            downloadError =
                                                if (opened) null else DesktopStrings.updateLinkOpenFailed
                                        }
                                        return@OutlinedButton
                                    }

                                    isInstallingUpdate = true
                                    installProgress = null
                                    installMessage = null
                                    downloadError = null
                                    downloadScope.launch {
                                        val attempt = withContext(Dispatchers.IO) {
                                            runCatching {
                                                UpdateInstaller.install(
                                                    url = url,
                                                    family = DesktopPlatform.family,
                                                    environment = System.getenv(),
                                                ) { percent ->
                                                    // Called from the download thread; Compose state
                                                    // belongs to the UI thread.
                                                    EventQueue.invokeLater { installProgress = percent }
                                                }
                                            }
                                        }
                                        isInstallingUpdate = false
                                        installProgress = null

                                        val outcome = attempt.getOrNull()
                                        when {
                                            outcome is UpdateOutcome.Replaced -> {
                                                readyToRestart = outcome.appImage
                                                installMessage = DesktopStrings.updateReadyRestart(info.latestVersion)
                                            }

                                            outcome is UpdateOutcome.InstallerLaunched -> {
                                                installMessage = DesktopStrings.updateInstallerLaunched(info.latestVersion)
                                            }

                                            attempt.isFailure -> {
                                                downloadError = DesktopStrings.updateDownloadFailed
                                            }

                                            else -> {
                                                // An unpacked build cannot replace itself; open the
                                                // release page instead of failing silently.
                                                installMessage = DesktopStrings.updateManualFallback
                                                withContext(Dispatchers.IO) {
                                                    DesktopUrlOpener().open(info.releaseUrl)
                                                }
                                            }
                                        }
                                    }
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.success)
                            ) {
                                Text(
                                    when {
                                        isInstallingUpdate -> DesktopStrings.updateDownloading(installProgress)
                                        readyToRestart != null -> DesktopStrings.updateInstalled
                                        updateInfo?.downloadUrl != null -> DesktopStrings.btnInstallUpdate
                                        else -> DesktopStrings.btnDownloadOnGithub
                                    }
                                )
                            }
                            if (readyToRestart != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        val appImage = readyToRestart ?: return@Button
                                        if (UpdateInstaller.restart(appImage)) {
                                            // The new build is already running; this one must go.
                                            exitProcess(0)
                                        } else {
                                            installMessage = DesktopStrings.updateRestartFailed
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        backgroundColor = palette.success,
                                        // The success fill differs per theme, so the label follows it.
                                        contentColor = if (MaterialTheme.colors.isLight) Color.White else Color.Black
                                    )
                                ) {
                                    Text(DesktopStrings.btnRestartNow)
                                }
                            }
                            val installNote = installMessage
                            if (installNote != null) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = installNote,
                                    style = MaterialTheme.typography.caption,
                                    color = MaterialTheme.colors.onSurface
                                )
                            }
                            val linkError = downloadError
                            if (linkError != null) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = linkError,
                                    style = MaterialTheme.typography.caption,
                                    color = palette.warning
                                )
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

                Spacer(modifier = Modifier.height(16.dp))
                DonationSection(onShowLicense = { showLicense = true })
            }
        }
    }

    if (showLicense) {
        LicenseWindow(onClose = { showLicense = false })
    }

    // Outside the lazy list so scrolling the card away cannot close the preview.
    val notes = releaseNotesPreview
    if (notes != null) {
        ReleaseNotesWindow(
            version = notes.first,
            notes = notes.second,
            onClose = { releaseNotesPreview = null },
        )
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

    /**
     * Where the money question is answered once, in the About card.
     *
     * The app is free and non-commercial, so this is an offer, not a nag: the Ko-fi page for
     * international support, the Pix key for Brazil, and the license text that governs the build.
     */
    @Composable
    private fun DonationSection(onShowLicense: () -> Unit) {
        val palette = yabaiPalette()
        val scope = rememberCoroutineScope()
        var copied by remember { mutableStateOf(false) }
        var copyFailed by remember { mutableStateOf(false) }

        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = DesktopStrings.donationTitle,
                style = MaterialTheme.typography.subtitle2.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colors.onSurface,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = DesktopStrings.donationBody,
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.onSurface,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                DesktopUrlOpener().open(DonationInfo.KOFI_URL)
                            }
                        }
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.success),
                ) {
                    Text(DesktopStrings.btnKofi)
                }
                OutlinedButton(
                    onClick = {
                        val ok = runCatching {
                            val selection = StringSelection(DonationInfo.PIX_KEY)
                            Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
                        }.isSuccess
                        copied = ok
                        copyFailed = !ok
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.success),
                ) {
                    Text(DesktopStrings.btnCopyPix)
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = DesktopStrings.pixLabel + " " + DonationInfo.PIX_KEY,
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.onSurface,
            )
            if (copied) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = DesktopStrings.pixCopied,
                    style = MaterialTheme.typography.caption,
                    color = palette.success,
                )
            }
            if (copyFailed) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = DesktopStrings.updateLinkOpenFailed,
                    style = MaterialTheme.typography.caption,
                    color = palette.warning,
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(
                onClick = onShowLicense,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.success),
            ) {
                Text(DesktopStrings.btnShowLicense)
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
