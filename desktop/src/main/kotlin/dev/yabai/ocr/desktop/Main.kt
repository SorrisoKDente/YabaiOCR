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
    /** The chosen dictionary that has no entry for the word under the pointer, if that is the case. */
    var missingEntryIn by mutableStateOf<String?>(null)
}

/** A full-screen capture plus the screen rectangle it came from. */
private data class CapturedScreen(val image: BufferedImage, val screenBounds: Rectangle)

fun main(args: Array<String>) {
    // One instance per user. The D-Bus name `dev.yabai.Ocr`, the tray item and the global shortcuts
    // have a single owner, and closing the window only hides the app, so launching the AppImage
    // again used to start a second process that could not register the name: it showed «não foi
    // possível publicar o serviço D-Bus» and sat there without hover or shortcuts.
    val instanceLock = InstanceLock.default()
    if (!acquireInstanceLockWithRetry { instanceLock.acquire() }) {
        val background = args.contains("--minimized") || args.contains("--tray")
        System.err.println("Yabai OCR: já existe uma instância aberta; esta cópia vai sair.")
        if (!background) {
            runCatching {
                javax.swing.JOptionPane.showMessageDialog(
                    null,
                    DesktopStrings.alreadyRunningBody,
                    DesktopStrings.alreadyRunningTitle,
                    javax.swing.JOptionPane.WARNING_MESSAGE,
                )
            }
        }
        return
    }

    // Before the first JNativeHook class is referenced: the packaged build may be running from a
    // read-only AppImage mount, where the library cannot be written next to the jars.
    val nativeLibraryDir = configureJNativeHookLibraryPathForThisSession()
    if (nativeLibraryDir != null) {
        println("[Native] JNativeHook vai descompactar em ${nativeLibraryDir.absolutePath}")
    }
    // Must be set before the first AWT/Toolkit use: the JDK reads it when Robot is initialized.
    configureScreenshotMethod(
        isLinux = DesktopPlatform.isLinux,
        session = DesktopPlatform.session,
        desktop = DesktopPlatform.desktop,
    )
    // Says which capture path this session should take before the first capture, so the log answers
    // «é o ScreenShot2 ou o portal?» even when no hover happened. The path actually used — which can
    // differ, the AppImage falls back to the portal — is printed by [CaptureDiagnostics].
    println(
        "[Captura] caminho esperado nesta sessão: " +
            captureBackendLabel(
                family = DesktopPlatform.family,
                session = DesktopPlatform.session,
                desktop = DesktopPlatform.desktop,
            ),
    )
    // Before any window: when Windows starts the app with `--minimized` the settings window is
    // never created, so the logon entry has to be repaired from the stored preference here.
    val appSettings = DesktopSettings()
    // The renderer log follows the interface language, so the language has to be known first.
    DesktopStrings.init(appSettings)

    // The renderer has to be chosen before Skiko is loaded — the first window does that — and it is
    // also the only way to survive the native Direct3D crash: the previous start is the witness that
    // this one died. `--renderer=…` and the Settings window are the two ways to change it, since a
    // packaged app has no JVM flags to give.
    val rendererRequest = parseRendererArgument(args.toList())
    val rendererRecovery = RendererRecovery.default()
    // The marker alone says only that the previous run did not close through the app: Ctrl+C under
    // Gradle leaves it behind too. The JVM's own crash report (`hs_err_pid<pid>.log`) is what says
    // the process died in Skiko, and only that is worth changing the renderer for.
    val previousRun = rendererRecovery.previousRun()
    val rendererCrashed = previousRun.skikoCrashReport != null
    val rendererDecision = decideRendererApi(
        previousRunCrashed = rendererCrashed,
        previousRunStoppedFromOutside = previousRun.stoppedFromOutside,
        stored = appSettings.skikoRenderer,
        requested = (rendererRequest as? RendererArgument.Chosen)?.level,
        current = System.getProperty(SKIKO_RENDER_API_PROPERTY),
    )
    rendererDecision.value?.let { System.setProperty(SKIKO_RENDER_API_PROPERTY, it) }
    rendererDecision.persist?.let { appSettings.skikoRenderer = it }
    rendererDecision.message?.let { println(it) }
    if (rendererRequest is RendererArgument.Invalid) {
        println(
            "[Renderer] --renderer=${rendererRequest.value} não é válido. " +
                "Use automatic, opengl ou software.",
        )
    }
    rendererRecovery.markRunning()
    // Runs on every normal exit — window closed, tray quit, SIGTERM — and not when the JVM dies.
    Runtime.getRuntime().addShutdownHook(Thread { rendererRecovery.markCleanExit() })
    StartupManager.reconcileStartOnBoot(appSettings.startOnBoot)
    // KWin refuses `org.kde.KWin.ScreenShot2` to a caller whose desktop entry does not declare the
    // interface, and the portal fallback presents a permission window per capture. Writing the entry
    // before the first capture is what makes hover silent; it must name the running binary, so it is
    // refreshed on every start.
    //
    // The same entry is what the shortcut portal would need as an app id, but exporting
    // `XDG_DESKTOP_FILE` cannot be done from inside the process (the portal reads the environment
    // the process was started with), and a self-restart through the AppImage runtime does not reach
    // `main` on this stack. That is why KDE uses the KWin shortcuts instead of the portal.
    if (DesktopPlatform.isWayland && DesktopPlatform.isKde) {
        LinuxSystemIntegration().ensureScreenshotAuthorization()
    }
    // The tray item advertises the icon by name; without the PNG in the user's hicolor theme the
    // StatusNotifier host draws its generic placeholder instead of the app icon.
    if (DesktopPlatform.isLinux) {
        LinuxSystemIntegration().ensureTrayIcon()
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
    val bridgePointer = remember { BridgePointerSource() }
    // AWT needs no bridge; KWin has one that can fail, and UNSUPPORTED has no pointer at all.
    var hoverBackendReady by remember { mutableStateOf(hoverBackendKind == HoverBackendKind.AWT) }
    var hoverBackendFailure by remember { mutableStateOf<String?>(null) }

    // Turning hover on where the session has no global pointer used to be a silent no-op: the
    // failure is said out loud instead, in the same notice window a failed capture uses. A
    // dictionary without the word is not this: it is said inside the card (see HoverStateHolder).
    var hoverNotice by remember { mutableStateOf<String?>(null) }

    val hoverOcrManager = remember {
        HoverOcrManager(
            onResultsReady = { matches, pt, filter, missingEntryIn ->
                hoverState.matches = matches
                hoverState.point = pt
                hoverState.activeFilter = filter
                hoverState.missingEntryIn = missingEntryIn
            },
            onDismiss = {
                hoverState.matches = emptyList()
                hoverState.point = null
                hoverState.missingEntryIn = null
            },
            onStatus = { message ->
                hoverState.status = message
            },
            backendKind = hoverBackendKind,
            pointerSource = if (hoverBackendKind.usesCompositorBridge) bridgePointer else AwtPointerSource(),
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
                HoverBackendKind.UNSUPPORTED -> DesktopStrings.hoverWaylandUnsupported
                else -> DesktopStrings.hoverKwinBridgeUnavailable
            }
            if (
                hoverBackendKind == HoverBackendKind.UNSUPPORTED ||
                hoverBackendKind == HoverBackendKind.GNOME
            ) {
                hoverNotice = hoverState.status
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
    // «Trechos»: the screenshot and the scan the last result came from, kept while that result is
    // open so the user can go back and click another paragraph without capturing the screen again.
    var snippetsCapture by remember { mutableStateOf<CapturedScreen?>(null) }
    var pickerScanCache by remember { mutableStateOf<PickerScan.Ready?>(null) }

    /**
     * Monitor the capture settings ask for.
     *
     * `MOUSE` needs the pointer: AWT reports it on Windows and X11, while KWin reports it through
     * the script, so both are consulted before giving up on the primary screen.
     */
    fun selectedCaptureDisplay(): CaptureDisplay? =
        ScreenCapture.resolveDisplay(
            settings.captureMonitor,
            pointer = bridgePointer.current() ?: AwtPointerSource().current(),
        )

    // The two captures are two visible actions: the whole screen with clickable paragraphs, and
    // the hand-drawn crop. Neither hides behind a preference the user cannot see.
    fun startFullScreenCapture() {
        try {
            snippetsCapture = null
            pickerScanCache = null
            val display = selectedCaptureDisplay()
            pickerCapture = CapturedScreen(
                image = if (display != null) ScreenCapture.captureDisplay(display) else ScreenCapture.captureScreen(),
                screenBounds = display?.bounds ?: ScreenCapture.screenBounds(),
            )
        } catch (e: ScreenCaptureException) {
            captureNotice = e.message
        } catch (e: Exception) {
            captureNotice = DesktopStrings.captureScreenError(e.message ?: e::class.simpleName.orEmpty())
        }
    }

    fun startRegionCapture() {
        try {
            snippetsCapture = null
            pickerScanCache = null
            resultInitialText = null
            val display = selectedCaptureDisplay()
            regionCapture = CapturedScreen(
                image = if (display != null) ScreenCapture.captureDisplay(display) else ScreenCapture.captureScreen(),
                screenBounds = display?.bounds ?: ScreenCapture.screenBounds(),
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
    // On KDE Wayland the shortcuts and the pointer bridge live in the same KWin script, so the
    // chosen kind decides whether that script also gets the shortcut registrations.
    val hotkeyBackendKind = remember {
        hotkeyBackendKindFor(
            family = DesktopPlatform.family,
            session = DesktopPlatform.session,
            desktop = DesktopPlatform.desktop,
        )
    }
    val hotkeyService = remember { HotkeyService(hotkeyCallbacks, hotkeyBackendKind) }
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
    // Both Wayland bridges deliver the pointer through the same D-Bus object.
    val hoverDbusService = remember {
        if (
            hoverBackendKind == HoverBackendKind.KWIN || hoverBackendKind == HoverBackendKind.GNOME
        ) {
            YabaiDbusService(
                onCursorMoved = { x, y -> hoverOcrManager.onPointerMoved(Point(x, y)) },
                // D-Bus delivers on its own thread; the callbacks touch Compose state.
                onShortcutPressed = { id ->
                    javax.swing.SwingUtilities.invokeLater { hotkeyCallbacks.dispatch(id) }
                },
            )
        } else {
            null
        }
    }

    val kwinIntegration = remember {
        val service = hoverDbusService
        if (hoverBackendKind == HoverBackendKind.KWIN && service != null) {
            KWinIntegration(
                service = service,
                bridge = KWinHoverBridge(),
                fallbackShortcuts = {
                    if (hotkeyBackendKind == HotkeyBackendKind.KWIN ||
                        hotkeyService.activeKind == HotkeyBackendKind.UNAVAILABLE
                    ) {
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

    // GNOME has no scripting API like KWin's; the bundled Shell extension owns the bridge.
    val gnomeBridge = remember {
        if (hoverBackendKind == HoverBackendKind.GNOME) GnomeHoverBridge() else null
    }

    if (kwinIntegration != null) {
        DisposableEffect(kwinIntegration) {
            hoverBackendReady = kwinIntegration.start()
            if (!hoverBackendReady) {
                hoverBackendFailure = kwinIntegration.statusMessage
                hoverState.status = kwinIntegration.statusMessage
            }
            // On KDE the shortcuts live in the same script as the pointer bridge, so a script that
            // does not start also means no shortcuts; the status line must say that instead of
            // claiming an active backend.
            if (hotkeyService.activeKind == HotkeyBackendKind.KWIN) {
                hotkeyStatus = if (hoverBackendReady) {
                    hotkeyService.statusMessage
                } else {
                    hotkeyFailureStatus(
                        HotkeyBackendKind.KWIN,
                        kwinIntegration.statusMessage.takeIf { it.isNotBlank() },
                    )
                }
            }

            onDispose {
                kwinIntegration.stop()
                hoverBackendReady = false
            }
        }
    }

    if (gnomeBridge != null) {
        DisposableEffect(gnomeBridge) {
            val serviceReady = hoverDbusService?.start() == true
            val bridgeReady = serviceReady && gnomeBridge.start()
            hoverBackendReady = bridgeReady
            // The same extension owns the accelerators, so it needs them as soon as it can talk to
            // the app; the Shell re-reads them when the user edits a shortcut.
            if (serviceReady) {
                hoverDbusService?.updateShortcutBindings(gnomeShortcutBindings(currentHotkeyBindings()))
            }
            if (bridgeReady) {
                hoverState.status = DesktopStrings.gnomeIntegrationActive
            } else {
                val message = when {
                    !serviceReady -> DesktopStrings.gnomeBridgeFailed(hoverDbusService?.lastError)
                    gnomeBridge.needsRelogin -> DesktopStrings.gnomeBridgeInstalled
                    else -> DesktopStrings.gnomeBridgeFailed(gnomeBridge.lastError)
                }
                hoverBackendFailure = message
                hoverState.status = message
                // No extension, no shortcuts on GNOME: say that instead of claiming a backend.
                if (hotkeyService.activeKind == HotkeyBackendKind.GNOME) {
                    hotkeyStatus = hotkeyFailureStatus(HotkeyBackendKind.GNOME, null)
                }
            }

            onDispose {
                gnomeBridge.stop()
                hoverDbusService?.stop()
                hoverBackendReady = false
            }
        }
    }

    // KDE Wayland refuses its screenshot interface to an AppImage, so the only way to capture
    // without a permission dialog per hover is KWin's own escape hatch in the session environment.
    var kdeCapturePermission by remember { mutableStateOf(isKdeCapturePermissionEnabled()) }
    var kdeCapturePermissionNote by remember { mutableStateOf("") }
    // Bumped every time the user asks for the main window from the tray. A window created while
    // another app is focused is not raised by KWin and stays in the taskbar; the counter is what
    // makes the app bring it to the screen.
    var mainWindowRequest by remember { mutableStateOf(0) }

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
                mainWindowRequest++
            },
            onSettingsRequested = {
                mainScreen = MainScreen.SETTINGS
                showMainWindow = true
                mainWindowRequest++
            },
            onExitRequested = {
                exitApplication()
            },
        )
    }
    var trayReady by remember { mutableStateOf(false) }
    // GNOME has no tray without the AppIndicator extension, and a silent failure there looks like a
    // broken app; the reason is shown in Settings instead.
    var trayStatus by remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        trayReady = trayManager.setup()
        trayStatus = trayManager.failureMessage().orEmpty()
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

    // «Check for updates when the app starts» answers on the first composition, not only when the
    // settings window happens to be open: a `--minimized` launch lives in the tray, and a version
    // notice must still reach the user there. Finding a newer release opens the notice window with
    // the download inside it, so acting on the news does not require walking to Settings.
    var appUpdateNotice by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var appUpdateNotes by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var appUpdateCheckStarted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!shouldCheckAppUpdateOnStart(settings.checkUpdatesOnStart, appUpdateCheckStarted)) {
            return@LaunchedEffect
        }
        appUpdateCheckStarted = true
        val appUpdate = withContext(Dispatchers.IO) { DesktopAppUpdateChecker.checkForAppUpdate() }
        val notice = startupUpdateNotice(appUpdate)
        if (notice != null) {
            trayManager.setStatus(DesktopStrings.appVersionNotice(notice.latestVersion))
            appUpdateNotice = notice
        }
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
            // Exactly what the button does, in one testable place: write the menu entry, then
            // keep the autostart entry (the one that starts the app at login) on the same binary.
            linuxIntegrationStatus = if (
                integrateLinuxSystem(integration, PlatformServices.autostart, settings.startOnBoot)
            ) {
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
                // The capture is kept for the «Trechos» button of the result window.
                snippetsCapture = picker
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
            initialScan = pickerScanCache,
            // A failure is not cached: reopening the picker after a failed scan should try
            // again instead of showing the same error forever.
            onScanReady = { scanned -> pickerScanCache = reusablePickerScan(scanned) },
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

    // The automatic update check found a release: say so and carry the download itself.
    val updateNotice = appUpdateNotice
    if (updateNotice != null) {
        AppUpdateNoticeWindow(
            info = updateNotice,
            onDismiss = { appUpdateNotice = null },
            onOpenReleaseNotes = { appUpdateNotes = updateNotice },
        )
    }
    // The update notice is always on top, so its release notes must be too; otherwise the notes
    // open behind the notice that asked for them.
    val notesUpdate = appUpdateNotes
    if (notesUpdate != null) {
        ReleaseNotesWindow(
            version = notesUpdate.latestVersion,
            notes = notesUpdate.releaseNotes,
            onClose = { appUpdateNotes = null },
            alwaysOnTop = true,
        )
    }

    val hoverMessage = hoverNotice
    if (hoverMessage != null) {
        CaptureNoticeWindow(
            message = hoverMessage,
            onDismiss = { hoverNotice = null },
            title = DesktopStrings.hoverNoticeTitle,
            heading = DesktopStrings.hoverNoticeHeading,
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
                onBackToPicker = snippetsCapture?.let { captured ->
                    {
                        temporaryViewImage = null
                        resultInitialText = null
                        snippetsCapture = null
                        // Same screenshot, same scan: the picker opens at once with its boxes.
                        pickerCapture = captured
                    }
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
            LaunchedEffect(mainWindowRequest) {
                // Only after the user asked for the window: on a plain start the window already has
                // focus and stealing it would be rude.
                if (mainWindowRequest > 0) activateOnOpen(window)
            }

            LaunchedEffect(Unit) {
                window.isAutoRequestFocus = false
                window.iconImage = AppIconGenerator.createWindowImage(64)
                withContext(Dispatchers.IO) {
                    // 1. The app's own update check now runs once per launch from the application
                    // scope (above), so it also covers a tray-only start. What is left here is the
                    // periodic dictionary check.

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
                            // Shown in the renderer card: the run was moved after a native crash.
                            rendererAfterCrash = if (rendererCrashed) rendererDecision.level else null,
                            hoverActive = hoverState.active,
                            hoverStatus = hoverState.status,
                            hotkeyBackendStatus = hotkeyStatus,
                            trayStatus = trayStatus,
                            linuxIntegrationStatus = linuxIntegrationStatus,
                            onInstallLinuxIntegration = installLinuxIntegration,
                            kdeCapturePermission = kdeCapturePermission,
                            kdeCapturePermissionNote = kdeCapturePermissionNote,
                            onKdeCapturePermissionChange = if (
                                DesktopPlatform.isWayland && DesktopPlatform.isKde
                            ) {
                                { enabled ->
                                    kdeCapturePermissionNote = if (setKdeCapturePermission(enabled)) {
                                        kdeCapturePermission = enabled
                                        if (enabled) DesktopStrings.kdeCapturePermissionNeedsRelogin else ""
                                    } else {
                                        DesktopStrings.kdeCapturePermissionFailed
                                    }
                                }
                            } else {
                                null
                            },
                            onHoverToggle = { applyHover(!hoverState.active) },
                            onHoverRestChanged = { millis ->
                                settings.hoverRestMillis = millis
                                hoverOcrManager.setRestMillis(millis)
                            },
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
                                hoverDbusService?.updateShortcutBindings(
                                    gnomeShortcutBindings(
                                        hotkeyBindings(fullScreen, region, quickDict, hover, hoverCycle),
                                    ),
                                )

                                // The portal and the KWin fallback are mutually exclusive. A failed
                                // portal edit has just closed its old session, so KWin must receive
                                // the new bindings immediately; a successful portal edit removes any
                                // stale fallback left by an earlier failure.
                                kwinIntegration?.let { integration ->
                                    val fallback = if (hotkeyBackendKind == HotkeyBackendKind.KWIN ||
                                        hotkeyService.activeKind == HotkeyBackendKind.UNAVAILABLE
                                    ) {
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
    val hMissing = hoverState.missingEntryIn
    // «Empty» is not the same as «nothing to show»: the card is also the place where the chosen
    // dictionary says it has no entry for the word.
    if (hPoint != null && (hMatches.isNotEmpty() || hMissing != null)) {
        HoverTooltipWindow(
            matches = hMatches,
            mousePoint = hPoint,
            activeFilter = hoverState.activeFilter,
            notice = hMissing?.let { DesktopStrings.hoverDictionaryMissingEntry(it) },
            installedDictTitles = hoverOcrManager.getInstalledDictionaryTitles(),
            onSelectDictionary = { chosen ->
                hoverState.missingEntryIn = null
                hoverOcrManager.setFilterAndRefresh(chosen)
                hoverState.activeFilter = chosen
            },
            onDismiss = {
                hoverState.matches = emptyList()
                hoverState.point = null
                hoverState.missingEntryIn = null
            },
            onBoundsChanged = { hoverOcrManager.setTooltipBounds(it) },
        )
    }
}
