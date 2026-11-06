package dev.yabai.ocr.desktop

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.window.application
import dev.yabai.ocr.core.dict.TokenMatch
import java.awt.Point
import java.awt.Rectangle
import java.awt.image.BufferedImage

enum class MainScreen { SETTINGS, DICTIONARIES }

class HoverStateHolder(defaultFilter: String = "") {
    var matches by mutableStateOf<List<TokenMatch>>(emptyList())
    var point by mutableStateOf<Point?>(null)
    var activeFilter by mutableStateOf(defaultFilter)
    var active by mutableStateOf(false)
    var status by mutableStateOf("Modo Hover desativado — ative para traduzir passando o mouse.")
}

/** A full-screen capture plus the screen rectangle it came from. */
private data class CapturedScreen(val image: BufferedImage, val screenBounds: Rectangle)

fun main(args: Array<String>) = application {
    val startMinimized = args.contains("--minimized") || args.contains("--tray")
    // Hidden background window to keep application alive in System Tray when all visible windows are closed
    Window(onCloseRequest = {}, visible = false, title = "Yabai OCR Background") {}

    val settings = remember { DesktopSettings() }
    var isDarkTheme by remember { mutableStateOf(settings.darkTheme) }

    var mainScreen by remember { mutableStateOf(MainScreen.SETTINGS) }
    var mainDictInitialQuery by remember { mutableStateOf("") }
    var showMainWindow by remember { mutableStateOf(!startMinimized) } // Open main window by default on run

    var showQuickDict by remember { mutableStateOf(false) }

    val hoverState = remember { HoverStateHolder(settings.hoverDictionaryFilter) }

    val hoverOcrManager = remember {
        HoverOcrManager(
            onResultsReady = { matches, pt, filter ->
                hoverState.matches = matches
                hoverState.point = pt
                hoverState.activeFilter = filter
            },
            onDismiss = {
                hoverState.matches = emptyList()
                hoverState.point = null
            },
            onStatus = { message ->
                hoverState.status = message
            },
        )
    }

    fun applyHover(enabled: Boolean) {
        hoverState.active = hoverOcrManager.setEnabled(enabled)
        if (!hoverState.active) {
            hoverState.matches = emptyList()
            hoverState.point = null
        }
    }

    DisposableEffect(hoverOcrManager) {
        onDispose { hoverOcrManager.close() }
    }

    var regionCapture by remember { mutableStateOf<CapturedScreen?>(null) }
    var pickerCapture by remember { mutableStateOf<CapturedScreen?>(null) }
    var temporaryViewImage by remember { mutableStateOf<BufferedImage?>(null) }
    var resultInitialText by remember { mutableStateOf<String?>(null) }
    var captureGeneration by remember { mutableStateOf(0L) }

    // The two captures are two visible actions: the whole screen with clickable paragraphs, and
    // the hand-drawn crop. Neither hides behind a preference the user cannot see.
    fun startFullScreenCapture() {
        try {
            pickerCapture = CapturedScreen(
                image = ScreenCapture.captureScreen(),
                screenBounds = ScreenCapture.screenBounds(),
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun startRegionCapture() {
        try {
            resultInitialText = null
            regionCapture = CapturedScreen(
                image = ScreenCapture.captureScreen(),
                screenBounds = ScreenCapture.screenBounds(),
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Setup Global Hotkey Listener (JNativeHook). The manager is remembered so a shortcut changed
    // in the Settings window takes effect immediately; a DisposableEffect(Unit) would keep the
    // old specs until the app restarted.
    val hotkeyManager = remember {
        GlobalHotkeyManager(
            onFullScreenTriggered = {
                startFullScreenCapture()
            },
            onRegionTriggered = {
                startRegionCapture()
            },
            onQuickDictTriggered = {
                showQuickDict = true
            },
            onHoverTriggered = {
                applyHover(!hoverState.active)
            },
            onHoverCycleTriggered = {
                val newFilter = hoverOcrManager.cycleDictionaryAndRefresh()
                hoverState.activeFilter = newFilter
            },
        )
    }

    DisposableEffect(hotkeyManager) {
        hotkeyManager.updateHotkeys(
            settings.globalHotkey,
            settings.regionHotkey,
            settings.quickDictHotkey,
            settings.hoverHotkey,
            settings.hoverCycleHotkey
        )
        hotkeyManager.start()

        onDispose {
            hotkeyManager.stop()
        }
    }

    // Setup System Tray (Steam-like behavior)
    DisposableEffect(Unit) {
        val trayManager = SystemTrayManager(
            onFullScreenCaptureRequested = {
                startFullScreenCapture()
            },
            onRegionCaptureRequested = {
                startRegionCapture()
            },
            onQuickDictRequested = {
                showQuickDict = true
            },
            onHoverRequested = {
                applyHover(!hoverState.active)
            },
            isHoverActive = {
                hoverState.active
            },
            onFullDictRequested = {
                mainDictInitialQuery = ""
                mainScreen = MainScreen.DICTIONARIES
                showMainWindow = true
            },
            onSettingsRequested = {
                mainScreen = MainScreen.SETTINGS
                showMainWindow = true
            },
            onExitRequested = {
                exitApplication()
            },
        )
        trayManager.setup()
        onDispose {
            trayManager.remove()
        }
    }

    // Full-screen picker: the screen as it was, with a clickable box over each paragraph
    val picker = pickerCapture
    if (picker != null) {
        SegmentPickerWindow(
            screenImage = picker.image,
            screenBounds = picker.screenBounds,
            readingMode = settings.pickerReading,
            dictionaryFilter = settings.dictionaryFilter,
            engineProvider = { DesktopDictLoader.cachedEngine(settings) },
            onBlockSelected = { cropped, text ->
                pickerCapture = null
                resultInitialText = text
                temporaryViewImage = cropped
                captureGeneration++
            },
            onManualCrop = {
                pickerCapture = null
                regionCapture = picker
            },
            onDismiss = {
                pickerCapture = null
            },
        )
    }

    // Region Selection Overlay Window
    val capture = regionCapture
    if (capture != null) {
        RegionSelectionWindow(
            screenImage = capture.image,
            screenBounds = capture.screenBounds,
            onRegionSelected = { cropped ->
                regionCapture = null
                resultInitialText = null
                temporaryViewImage = cropped
                captureGeneration++
            },
            onDismiss = {
                regionCapture = null
            },
        )
    }

    // Result Window (OCR, furigana, token lookup, translation & AI)
    val tempImage = temporaryViewImage
    if (tempImage != null) {
        key(captureGeneration) {
            ResultWindow(
                croppedImage = tempImage,
                initialText = resultInitialText,
                onClose = {
                    temporaryViewImage = null
                    resultInitialText = null
                },
                onNewSelectionRequested = {
                    startRegionCapture()
                },
                onOpenFullDictRequested = { query ->
                    mainDictInitialQuery = query
                    mainScreen = MainScreen.DICTIONARIES
                    showMainWindow = true
                },
            )
        }
    }

    // Quick Search Window
    if (showQuickDict) {
        QuickDictWindow(onClose = { showQuickDict = false })
    }

    // Hover Tooltip Window (isolated composable)
    HoverTooltipOverlay(hoverState = hoverState, hoverOcrManager = hoverOcrManager)

    // Main Window (Settings or Integrated Dictionary Manager)
    if (showMainWindow) {
        val windowState = rememberWindowState(width = 820.dp, height = 750.dp)
        val title = if (mainScreen == MainScreen.SETTINGS) {
            "Yabai OCR - Ajustes e Configurações"
        } else {
            "Yabai OCR - Gerenciador de Dicionários"
        }
        Window(
            onCloseRequest = { showMainWindow = false },
            title = title,
            state = windowState,
        ) {
            LaunchedEffect(Unit) {
                window.isAutoRequestFocus = false
                window.iconImage = AppIconGenerator.createWindowImage(64)
            }

            YabaiDesktopTheme(darkTheme = isDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background,
                ) {
                    if (mainScreen == MainScreen.SETTINGS) {
                        SettingsContent(
                            hoverActive = hoverState.active,
                            hoverStatus = hoverState.status,
                            onHoverToggle = { applyHover(!hoverState.active) },
                            onHoverTest = { hoverOcrManager.triggerAtCursor(force = true) },
                            onThemeChanged = { isDark ->
                                settings.darkTheme = isDark
                                isDarkTheme = isDark
                            },
                            onHotkeysChanged = { fullScreen, region, quickDict, hover, hoverCycle ->
                                settings.globalHotkey = fullScreen
                                settings.regionHotkey = region
                                settings.quickDictHotkey = quickDict
                                settings.hoverHotkey = hover
                                settings.hoverCycleHotkey = hoverCycle
                                hotkeyManager.updateHotkeys(
                                    fullScreen,
                                    region,
                                    quickDict,
                                    hover,
                                    hoverCycle,
                                )
                            },
                            onManageDictionariesRequested = {
                                mainDictInitialQuery = ""
                                mainScreen = MainScreen.DICTIONARIES
                            },
                        )
                    } else {
                        DictionaryManagerContent(
                            initialQuery = mainDictInitialQuery,
                            onBackRequested = {
                                mainScreen = MainScreen.SETTINGS
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HoverTooltipOverlay(
    hoverState: HoverStateHolder,
    hoverOcrManager: HoverOcrManager
) {
    val hMatches = hoverState.matches
    val hPoint = hoverState.point
    if ((hMatches.isNotEmpty()) && (hPoint != null)) {
        HoverTooltipWindow(
            matches = hMatches,
            mousePoint = hPoint,
            activeFilter = hoverState.activeFilter,
            installedDictTitles = hoverOcrManager.getInstalledDictionaryTitles(),
            onSelectDictionary = { chosen ->
                hoverOcrManager.setFilterAndRefresh(chosen)
                hoverState.activeFilter = chosen
            },
            onDismiss = {
                hoverState.matches = emptyList()
                hoverState.point = null
            },
            onBoundsChanged = { hoverOcrManager.setTooltipBounds(it) },
        )
    }
}
