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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

fun main(args: Array<String>) {
    // Must be set before the first AWT/Toolkit use: the JDK reads it when Robot is initialized.
    configureScreenshotMethod(
        isLinux = DesktopPlatform.isLinux,
        session = DesktopPlatform.session,
        desktop = DesktopPlatform.desktop,
    )
    // Before any window: when Windows starts the app with `--minimized` the settings window is
    // never created, so the logon entry has to be repaired from the stored preference here.
    val appSettings = DesktopSettings()
    StartupManager.reconcileStartOnBoot(appSettings.startOnBoot)

    application {
    val startMinimized = args.contains("--minimized") || args.contains("--tray")
    // Hidden background window to keep application alive in System Tray when all visible windows are closed
    Window(onCloseRequest = {}, visible = false, title = "Yabai OCR Background") {}

    val settings = remember { appSettings }
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
    var captureNotice by remember { mutableStateOf<String?>(null) }

    // The two captures are two visible actions: the whole screen with clickable paragraphs, and
    // the hand-drawn crop. Neither hides behind a preference the user cannot see.
    fun startFullScreenCapture() {
        try {
            pickerCapture = CapturedScreen(
                image = ScreenCapture.captureScreen(),
                screenBounds = ScreenCapture.screenBounds(),
            )
        } catch (e: ScreenCaptureException) {
            captureNotice = e.message
        } catch (e: Exception) {
            captureNotice = "Não foi possível capturar a tela: ${e.message ?: e::class.simpleName}"
        }
    }

    fun startRegionCapture() {
        try {
            resultInitialText = null
            regionCapture = CapturedScreen(
                image = ScreenCapture.captureScreen(),
                screenBounds = ScreenCapture.screenBounds(),
            )
        } catch (e: ScreenCaptureException) {
            captureNotice = e.message
        } catch (e: Exception) {
            captureNotice = "Não foi possível capturar a tela: ${e.message ?: e::class.simpleName}"
        }
    }

    // Global shortcuts go through the backend the platform chose: JNativeHook on Windows/X11 and the
    // XDG portal on Wayland. The service is remembered so a shortcut changed in the Settings window
    // takes effect immediately; a DisposableEffect(Unit) would keep the old bindings until restart.
    val hotkeyCallbacks = remember {
        HotkeyCallbacks(
            onFullScreen = { startFullScreenCapture() },
            onRegion = { startRegionCapture() },
            onQuickDict = { showQuickDict = true },
            onHoverToggle = { applyHover(!hoverState.active) },
            onHoverCycle = {
                val newFilter = hoverOcrManager.cycleDictionaryAndRefresh()
                hoverState.activeFilter = newFilter
            },
        )
    }
    val hotkeyService = remember { HotkeyService(hotkeyCallbacks) }
    var hotkeyStatus by remember { mutableStateOf(hotkeyService.statusMessage) }

    DisposableEffect(hotkeyService) {
        hotkeyService.start(
            settings.globalHotkey,
            settings.regionHotkey,
            settings.quickDictHotkey,
            settings.hoverHotkey,
            settings.hoverCycleHotkey
        )
        hotkeyStatus = hotkeyService.statusMessage

        onDispose {
            hotkeyService.stop()
        }
    }

    // Setup System Tray (Steam-like behavior)
    val trayManager = remember {
        SystemTrayManager(
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
    }

    DisposableEffect(Unit) {
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

    val notice = captureNotice
    if (notice != null) {
        CaptureNoticeWindow(
            message = notice,
            onDismiss = { captureNotice = null },
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
            onCloseRequest = {
                if (settings.closeToTray) {
                    showMainWindow = false
                } else {
                    exitApplication()
                }
            },
            title = title,
            state = windowState,
        ) {
            LaunchedEffect(Unit) {
                window.isAutoRequestFocus = false
                window.iconImage = AppIconGenerator.createWindowImage(64)
                withContext(Dispatchers.IO) {
                    // 1. Check for Desktop App updates on GitHub
                    val appUpdate = DesktopAppUpdateChecker.checkForAppUpdate()
                    if (appUpdate?.hasUpdate == true) {
                        trayManager.setStatus("✨ Nova versão v${appUpdate.latestVersion} disponível!")
                    }

                    // 2. Periodic background check for dictionary updates
                    if (DesktopDictionaryUpdateChecker.shouldCheck(settings)) {
                        val dictResult = DesktopDictionaryUpdateChecker.checkUpdates(settings)
                        if (dictResult.updatesAvailableCount > 0) {
                            trayManager.setStatus("📚 ${dictResult.updatesAvailableCount} atualização(ões) de dicionário disponível(is)")
                        }
                    }
                }
            }

            YabaiDesktopTheme(darkTheme = isDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background,
                ) {
                    if (mainScreen == MainScreen.SETTINGS) {
                        SettingsContent(
                            settings = settings,
                            hoverActive = hoverState.active,
                            hoverStatus = hoverState.status,
                            hotkeyBackendStatus = hotkeyStatus,
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
                                hotkeyService.updateHotkeys(
                                    fullScreen,
                                    region,
                                    quickDict,
                                    hover,
                                    hoverCycle,
                                )
                                hotkeyStatus = hotkeyService.statusMessage
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
