package dev.yabai.ocr.desktop

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.darkColors
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
import java.awt.image.BufferedImage

enum class MainScreen { SETTINGS, DICTIONARIES }

fun main() = application {
    // Hidden background window to keep application alive in System Tray when all visible windows are closed
    Window(onCloseRequest = {}, visible = false, title = "Yabai OCR Background") {}

    val settings = remember { DesktopSettings() }

    var mainScreen by remember { mutableStateOf(MainScreen.SETTINGS) }
    var mainDictInitialQuery by remember { mutableStateOf("") }
    var showMainWindow by remember { mutableStateOf(true) } // Open main window by default on run

    var showQuickDict by remember { mutableStateOf(false) }

    var hoverMatches by remember { mutableStateOf<List<TokenMatch>>(emptyList()) }
    var hoverPoint by remember { mutableStateOf<java.awt.Point?>(null) }
    var hoverActive by remember { mutableStateOf(false) }
    var hoverStatus by remember {
        mutableStateOf("Modo Hover desativado — ative para traduzir passando o mouse.")
    }

    val hoverOcrManager = remember {
        HoverOcrManager(
            onResultsReady = { matches, pt ->
                hoverMatches = matches
                hoverPoint = pt
            },
            onDismiss = {
                hoverMatches = emptyList()
                hoverPoint = null
            },
            onStatus = { message ->
                hoverStatus = message
            },
        )
    }

    fun applyHover(enabled: Boolean) {
        hoverActive = hoverOcrManager.setEnabled(enabled)
        if (!hoverActive) {
            hoverMatches = emptyList()
            hoverPoint = null
        }
    }

    DisposableEffect(hoverOcrManager) {
        onDispose { hoverOcrManager.close() }
    }

    var screenCaptureForSelection by remember { mutableStateOf<BufferedImage?>(null) }
    var temporaryViewImage by remember { mutableStateOf<BufferedImage?>(null) }

    // Setup System Tray (Steam-like behavior). It is hoisted so the hover status can update its tooltip.
    val trayManager = remember {
        SystemTrayManager(
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
                applyHover(!hoverOcrManager.isRunning())
            },
            isHoverActive = {
                hoverOcrManager.isRunning()
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
    }

    DisposableEffect(trayManager) {
        trayManager.setup()
        onDispose { trayManager.remove() }
    }

    LaunchedEffect(hoverStatus) {
        trayManager.setStatus(hoverStatus)
    }

    // Setup Global Hotkey Listener (JNativeHook)
    val hotkeyManager = remember {
        GlobalHotkeyManager(
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
                applyHover(!hoverOcrManager.isRunning())
            },
        )
    }

    DisposableEffect(hotkeyManager) {
        hotkeyManager.updateHotkeys(settings.globalHotkey, settings.quickDictHotkey, settings.hoverHotkey)
        if (!hotkeyManager.start()) {
            hoverStatus = "Atalhos globais indisponíveis — use o botão «Ativar» nos Ajustes."
        }
        onDispose { hotkeyManager.stop() }
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

    // Hover Tooltip Window (Rikaichan / Yomitan style)
    val hMatches = hoverMatches
    val hPoint = hoverPoint
    if (hMatches.isNotEmpty() && hPoint != null) {
        HoverTooltipWindow(
            matches = hMatches,
            mousePoint = hPoint,
            onDismiss = {
                hoverMatches = emptyList()
                hoverPoint = null
            },
            onBoundsChanged = { hoverOcrManager.setTooltipBounds(it) },
        )
    }

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
                ),
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF12141A),
                ) {
                    if (mainScreen == MainScreen.SETTINGS) {
                        SettingsContent(
                            onManageDictionariesRequested = {
                                mainDictInitialQuery = ""
                                mainScreen = MainScreen.DICTIONARIES
                            },
                            hoverActive = hoverActive,
                            hoverStatus = hoverStatus,
                            onHoverToggle = {
                                applyHover(!hoverOcrManager.isRunning())
                            },
                            onHoverTest = {
                                hoverOcrManager.triggerAtCursor()
                            },
                            onHotkeysChanged = { ocr, quick, hover ->
                                hotkeyManager.updateHotkeys(ocr, quick, hover)
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
