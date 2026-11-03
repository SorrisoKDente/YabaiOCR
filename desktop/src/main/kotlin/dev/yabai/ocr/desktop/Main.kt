package dev.yabai.ocr.desktop

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.darkColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import dev.yabai.ocr.core.dict.TokenMatch
import java.awt.Point
import java.awt.image.BufferedImage

enum class MainScreen { SETTINGS, DICTIONARIES }

class HoverStateHolder(defaultFilter: String = "") {
    var matches by mutableStateOf<List<TokenMatch>>(emptyList())
    var point by mutableStateOf<Point?>(null)
    var activeFilter by mutableStateOf(defaultFilter)
    var active by mutableStateOf(false)
    var status by mutableStateOf("Modo Hover desativado — ative para traduzir passando o mouse.")
}

fun main() = application {
    // Hidden background window to keep application alive in System Tray when all visible windows are closed
    Window(onCloseRequest = {}, visible = false, title = "Yabai OCR Background") {}

    val settings = remember { DesktopSettings() }

    var mainScreen by remember { mutableStateOf(MainScreen.SETTINGS) }
    var mainDictInitialQuery by remember { mutableStateOf("") }
    var showMainWindow by remember { mutableStateOf(true) } // Open main window by default on run

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

    var screenCaptureForSelection by remember { mutableStateOf<BufferedImage?>(null) }
    var temporaryViewImage by remember { mutableStateOf<BufferedImage?>(null) }

    // Setup Global Hotkey Listener (JNativeHook)
    DisposableEffect(Unit) {
        val hotkeyManager = GlobalHotkeyManager(
            onOcrTriggered = {
                try {
                    val fullScreen = ScreenCapture.captureScreen()
                    screenCaptureForSelection = fullScreen
                } catch (e: Exception) {
                    e.printStackTrace()
                }
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
            }
        )
        hotkeyManager.updateHotkeys(
            settings.globalHotkey,
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
            onCaptureRequested = {
                try {
                    val fullScreen = ScreenCapture.captureScreen()
                    screenCaptureForSelection = fullScreen
                } catch (e: Exception) {
                    e.printStackTrace()
                }
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

    // Region Selection Overlay Window
    val capture = screenCaptureForSelection
    if (capture != null) {
        RegionSelectionWindow(
            screenImage = capture,
            onRegionSelected = { cropped ->
                screenCaptureForSelection = null
                temporaryViewImage = cropped
            },
            onDismiss = {
                screenCaptureForSelection = null
            },
        )
    }

    // Result Window (OCR, furigana, token lookup, translation & AI)
    val tempImage = temporaryViewImage
    if (tempImage != null) {
        ResultWindow(
            croppedImage = tempImage,
            onClose = {
                temporaryViewImage = null
            },
            onNewSelectionRequested = {
                try {
                    val fullScreen = ScreenCapture.captureScreen()
                    screenCaptureForSelection = fullScreen
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            },
            onOpenFullDictRequested = { query ->
                mainDictInitialQuery = query
                mainScreen = MainScreen.DICTIONARIES
                showMainWindow = true
            },
        )
    }

    // Quick Search Window
    if (showQuickDict) {
        QuickDictWindow(onClose = { showQuickDict = false })
    }

    // Hover Tooltip Window (isolated composable)
    HoverTooltipOverlay(hoverState = hoverState, hoverOcrManager = hoverOcrManager)

    // Main Window (Settings or Integrated Dictionary Manager)
    if (showMainWindow) {
        val title = if (mainScreen == MainScreen.SETTINGS) {
            "Yabai OCR - Ajustes e Configurações"
        } else {
            "Yabai OCR - Gerenciador de Dicionários"
        }
        Window(
            onCloseRequest = { showMainWindow = false },
            title = title,
            state = WindowState(width = 820.dp, height = 750.dp),
        ) {
            LaunchedEffect(Unit) {
                window.isAutoRequestFocus = false
                window.iconImage = AppIconGenerator.createWindowImage(64)
            }

            MaterialTheme(
                colors = darkColors(
                    background = Color(0xFF12141A),
                    surface = Color(0xFF1B1E28),
                    primary = Color(0xFF4C78FF),
                    onPrimary = Color.White,
                    onBackground = Color.White,
                    onSurface = Color.White,
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF12141A),
                ) {
                    if (mainScreen == MainScreen.SETTINGS) {
                        SettingsContent(
                            hoverActive = hoverState.active,
                            hoverStatus = hoverState.status,
                            onHoverToggle = { applyHover(!hoverState.active) },
                            onHoverTest = { hoverOcrManager.triggerAtCursor(force = true) },
                            onHotkeysChanged = { ocr, quickDict, hover, hoverCycle ->
                                settings.globalHotkey = ocr
                                settings.quickDictHotkey = quickDict
                                settings.hoverHotkey = hover
                                settings.hoverCycleHotkey = hoverCycle
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
    if (hMatches.isNotEmpty() && hPoint != null) {
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
