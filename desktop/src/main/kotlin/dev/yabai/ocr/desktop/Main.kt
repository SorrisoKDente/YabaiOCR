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
import dev.yabai.ocr.desktop.i18n.DesktopStrings
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
    var status by mutableStateOf(DesktopStrings.hoverStatusInactivePrefix)
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
    DesktopStrings.init(appSettings)
    StartupManager.reconcileStartOnBoot(appSettings.startOnBoot)
    // KWin refuses `org.kde.KWin.ScreenShot2` to a caller whose desktop entry does not declare the
    // interface, and the portal fallback presents a permission window per capture. Writing the entry
    // before the first capture is what makes hover silent; it must name the running binary, so it is
    // refreshed on every start.
    if (DesktopPlatform.isWayland && DesktopPlatform.isKde) {
        LinuxSystemIntegration().ensureScreenshotAuthorization()
    }

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

    val hoverBackendKind = remember {
        hoverBackendKindFor(
            family = DesktopPlatform.family,
            session = DesktopPlatform.session,
            desktop = DesktopPlatform.desktop,
        )
    }
    val kwinPointer = remember { KwinPointerSource() }
    // AWT needs no bridge; KWin has one that can fail, and UNSUPPORTED has no pointer at all.
    var hoverBackendReady by remember { mutableStateOf(hoverBackendKind == HoverBackendKind.AWT) }
    var hoverBackendFailure by remember { mutableStateOf<String?>(null) }

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
            backendKind = hoverBackendKind,
            pointerSource = if (hoverBackendKind == HoverBackendKind.KWIN) kwinPointer else AwtPointerSource(),
            captureProvider = hoverCaptureProvider(hoverBackendKind) { rect ->
                ScreenCapture.captureRegion(rect)
            },
        )
    }

    fun applyHover(enabled: Boolean) {
        if (enabled && !hoverBackendReady) {
            hoverState.active = false
            hoverState.matches = emptyList()
            hoverState.point = null
            hoverState.status = hoverBackendFailure ?: when (hoverBackendKind) {
                HoverBackendKind.UNSUPPORTED -> hoverBackendLabel(HoverBackendKind.UNSUPPORTED)
                else -> DesktopStrings.hoverKwinBridgeUnavailable
            }
            return
        }

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
            captureNotice = DesktopStrings.captureScreenError(e.message ?: e::class.simpleName.orEmpty())
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
            captureNotice = DesktopStrings.captureScreenError(e.message ?: e::class.simpleName.orEmpty())
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

    fun currentHotkeyBindings(): List<HotkeyBinding> = hotkeyBindings(
        fullScreen = settings.globalHotkey,
        region = settings.regionHotkey,
        quickDict = settings.quickDictHotkey,
        hover = settings.hoverHotkey,
        hoverCycle = settings.hoverCycleHotkey,
    )

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

    // KDE Wayland: the pointer and the fallback shortcuts arrive through D-Bus from a KWin script.
    // The effect runs after the hotkey effect, so a portal failure is already known when the script
    // is generated. Starting is all-or-nothing: no script without the service, no hover without the
    // script.
    val kwinIntegration = remember {
        if (hoverBackendKind == HoverBackendKind.KWIN) {
            KWinIntegration(
                service = YabaiDbusService(
                    onCursorMoved = { x, y -> hoverOcrManager.onPointerMoved(Point(x, y)) },
                    onShortcutPressed = { id -> hotkeyCallbacks.dispatch(id) },
                ),
                bridge = KWinHoverBridge(),
                fallbackShortcuts = {
                    if (hotkeyService.activeKind == HotkeyBackendKind.UNAVAILABLE) {
                        currentHotkeyBindings()
                    } else {
                        emptyList()
                    }
                },
            )
        } else {
            null
        }
    }

    if (kwinIntegration != null) {
        DisposableEffect(kwinIntegration) {
            hoverBackendReady = kwinIntegration.start()
            if (!hoverBackendReady) {
                hoverBackendFailure = kwinIntegration.statusMessage
                hoverState.status = kwinIntegration.statusMessage
            }

            onDispose {
                kwinIntegration.stop()
                hoverBackendReady = false
            }
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
    var trayReady by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        trayReady = trayManager.setup()
        // `--minimized` is only honest when the tray icon exists. Without it the only way to reach
        // the app is the window, so open it instead of running invisibly.
        if (shouldShowMainWindowOnStart(startMinimized, trayReady)) {
            showMainWindow = true
        }
        onDispose {
            trayManager.remove()
        }
    }

    LaunchedEffect(hoverState.active) {
        trayManager.updateHover(hoverState.active)
    }

    // Linux: "Integrar ao sistema" writes a per-user desktop entry and icon. The card only exists
    // when the integration can name an executable, so Windows keeps the exact same UI as before.
    val linuxIntegration = remember {
        if (DesktopPlatform.isLinux) LinuxSystemIntegration() else null
    }
    var linuxIntegrationStatus by remember {
        mutableStateOf(
            if (linuxIntegration?.desktopFile?.isFile == true) {
                DesktopStrings.linuxIntegrationAlreadyInstalled
            } else {
                ""
            }
        )
    }
    val installLinuxIntegration: (() -> Unit)? = linuxIntegration?.let { integration ->
        {
            linuxIntegrationStatus = if (integration.install()) {
                DesktopStrings.linuxIntegrationInstalledSuccess
            } else {
                DesktopStrings.linuxIntegrationInstalledError
            }
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
            DesktopStrings.settingsWindowTitle
        } else {
            "Yabai OCR - ${DesktopStrings.dictManagerTitle}"
        }
        Window(
            onCloseRequest = {
                if (settings.closeToTray && trayReady) {
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
                        trayManager.setStatus(DesktopStrings.appVersionNotice(appUpdate.latestVersion))
                    }

                    // 2. Periodic background check for dictionary updates
                    if (DesktopDictionaryUpdateChecker.shouldCheck(settings)) {
                        val dictResult = DesktopDictionaryUpdateChecker.checkUpdates(settings)
                        if (dictResult.updatesAvailableCount > 0) {
                            trayManager.setStatus(DesktopStrings.dictUpdatesCountNotice(dictResult.updatesAvailableCount))
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
                            linuxIntegrationStatus = linuxIntegrationStatus,
                            onInstallLinuxIntegration = installLinuxIntegration,
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

                                // The portal and the KWin fallback are mutually exclusive. A failed
                                // portal edit has just closed its old session, so KWin must receive
                                // the new bindings immediately; a successful portal edit removes any
                                // stale fallback left by an earlier failure.
                                kwinIntegration?.let { integration ->
                                    val fallback = if (hotkeyService.activeKind == HotkeyBackendKind.UNAVAILABLE) {
                                        hotkeyBindings(fullScreen, region, quickDict, hover, hoverCycle)
                                    } else {
                                        emptyList()
                                    }
                                    if (!integration.updateShortcuts(fallback)) {
                                        hoverBackendReady = integration.isRunning
                                        hoverBackendFailure = integration.statusMessage
                                        hoverState.status = integration.statusMessage
                                    }
                                }
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
