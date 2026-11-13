package dev.yabai.ocr.desktop

import dev.yabai.ocr.desktop.i18n.DesktopStrings

/**
 * Where the global shortcuts come from.
 *
 * JNativeHook reads the keyboard directly, which is what Windows and X11 do today. On Wayland that
 * is impossible for an ordinary client, so the shortcuts go through the XDG portal.
 */
internal enum class HotkeyBackendKind { JNATIVEHOOK, PORTAL, UNAVAILABLE }

internal fun hotkeyBackendKindFor(
    family: OsFamily,
    session: SessionType,
    @Suppress("UNUSED_PARAMETER") desktop: DesktopFamily,
): HotkeyBackendKind = when {
    family == OsFamily.LINUX && session == SessionType.WAYLAND -> HotkeyBackendKind.PORTAL
    else -> HotkeyBackendKind.JNATIVEHOOK
}

internal fun hotkeyBackendLabel(kind: HotkeyBackendKind): String = when (kind) {
    HotkeyBackendKind.JNATIVEHOOK -> DesktopStrings.hotkeyBackendActiveStatus
    HotkeyBackendKind.PORTAL -> DesktopStrings.hotkeyBackendPortalStatus
    HotkeyBackendKind.UNAVAILABLE -> DesktopStrings.hotkeyBackendUnavailableStatus
}

internal fun hotkeyBackendFailureMessage(kind: HotkeyBackendKind): String = when (kind) {
    HotkeyBackendKind.PORTAL ->
        DesktopStrings.hotkeyPortalFailureMessage
    HotkeyBackendKind.JNATIVEHOOK ->
        DesktopStrings.hotkeyJNativeHookFailureMessage
    HotkeyBackendKind.UNAVAILABLE -> hotkeyBackendLabel(HotkeyBackendKind.UNAVAILABLE)
}

internal interface HotkeyBackend {
    val kind: HotkeyBackendKind

    /** Why [start]/[update] failed, when the transport knows. Shown after the generic message. */
    val lastError: String? get() = null

    /** Installs the backend and binds [bindings]; false means the caller should explain the failure. */
    fun start(bindings: List<HotkeyBinding>): Boolean

    /** Rebinds after the user edits a shortcut. */
    fun update(bindings: List<HotkeyBinding>): Boolean

    fun stop()
}

/**
 * The Windows/X11 path: the same [GlobalHotkeyManager] as before, only behind the backend interface
 * so [HotkeyService] can choose it without the call sites reading `os.name`.
 */
internal class JNativeHookHotkeys(private val callbacks: HotkeyCallbacks) : HotkeyBackend {
    override val kind = HotkeyBackendKind.JNATIVEHOOK

    private val manager = GlobalHotkeyManager(
        onFullScreenTriggered = callbacks.onFullScreen,
        onRegionTriggered = callbacks.onRegion,
        onQuickDictTriggered = callbacks.onQuickDict,
        onHoverTriggered = callbacks.onHoverToggle,
        onHoverCycleTriggered = callbacks.onHoverCycle,
    )

    override fun start(bindings: List<HotkeyBinding>): Boolean {
        apply(bindings)
        return manager.start()
    }

    override fun update(bindings: List<HotkeyBinding>): Boolean {
        apply(bindings)
        return true
    }

    override fun stop() = manager.stop()

    private fun apply(bindings: List<HotkeyBinding>) {
        fun value(id: String, fallback: String) =
            bindings.firstOrNull { it.id == id }?.configured ?: fallback

        manager.updateHotkeys(
            fullScreen = value(HotkeyIds.FULL_SCREEN, "Ctrl + Shift + O"),
            region = value(HotkeyIds.REGION, "Ctrl + Shift + A"),
            quickDict = value(HotkeyIds.QUICK_DICT, "Ctrl + Shift + D"),
            hover = value(HotkeyIds.HOVER_TOGGLE, "Ctrl + Shift + H"),
            hoverCycle = value(HotkeyIds.HOVER_CYCLE, "Ctrl + Shift + X"),
        )
    }
}

internal class UnavailableHotkeys : HotkeyBackend {
    override val kind = HotkeyBackendKind.UNAVAILABLE
    override fun start(bindings: List<HotkeyBinding>): Boolean = false
    override fun update(bindings: List<HotkeyBinding>): Boolean = false
    override fun stop() = Unit
}

/**
 * Chooses the backend once and keeps the status the Settings window shows.
 *
 * A failed start (portal missing, JNativeHook denied by the session) is not fatal: the app keeps
 * running and the tray menu still performs every action.
 */
internal class HotkeyService(
    private val callbacks: HotkeyCallbacks,
    private val kind: HotkeyBackendKind = hotkeyBackendKindFor(
        family = DesktopPlatform.family,
        session = DesktopPlatform.session,
        desktop = DesktopPlatform.desktop,
    ),
    private val backend: HotkeyBackend = createHotkeyBackend(kind, callbacks),
) {
    var activeKind: HotkeyBackendKind = kind
        private set

    var statusMessage: String = hotkeyBackendLabel(kind)
        private set

    /** Starts the backend with the current preferences and reports the resulting status. */
    fun start(
        fullScreen: String,
        region: String,
        quickDict: String,
        hover: String,
        hoverCycle: String,
    ): Boolean {
        val started = backend.start(hotkeyBindings(fullScreen, region, quickDict, hover, hoverCycle))
        if (started) {
            activeKind = backend.kind
            statusMessage = hotkeyBackendLabel(backend.kind)
            return true
        }

        runCatching { backend.stop() }
        activeKind = HotkeyBackendKind.UNAVAILABLE
        statusMessage = hotkeyFailureStatus(kind, backend.lastError)
        return false
    }

    /** Applies a shortcut edited in the Settings window without restarting the app. */
    fun updateHotkeys(
        fullScreen: String,
        region: String,
        quickDict: String,
        hover: String,
        hoverCycle: String,
    ): Boolean {
        val bindings = hotkeyBindings(fullScreen, region, quickDict, hover, hoverCycle)
        // After a failed start there is no live session to update. Retrying start is what lets an
        // edited shortcut recover the portal without restarting the app.
        val updated = if (activeKind == HotkeyBackendKind.UNAVAILABLE) {
            backend.start(bindings)
        } else {
            backend.update(bindings)
        }
        if (updated) {
            activeKind = backend.kind
            statusMessage = hotkeyBackendLabel(backend.kind)
            return true
        }

        runCatching { backend.stop() }
        activeKind = HotkeyBackendKind.UNAVAILABLE
        statusMessage = hotkeyFailureStatus(backend.kind, backend.lastError)
        return false
    }

    fun stop() = backend.stop()
}

private fun hotkeyFailureStatus(kind: HotkeyBackendKind, error: String?): String {
    val detail = error?.takeIf { it.isNotBlank() } ?: return hotkeyBackendFailureMessage(kind)
    val label = if (DesktopStrings.isEnglish) "Detail:" else "Detalhe:"
    return "${hotkeyBackendFailureMessage(kind)} $label $detail."
}

internal fun createHotkeyBackend(kind: HotkeyBackendKind, callbacks: HotkeyCallbacks): HotkeyBackend =
    when (kind) {
        HotkeyBackendKind.JNATIVEHOOK -> JNativeHookHotkeys(callbacks)
        HotkeyBackendKind.PORTAL -> PortalGlobalShortcuts(callbacks)
        HotkeyBackendKind.UNAVAILABLE -> UnavailableHotkeys()
    }
