package dev.yabai.ocr.desktop

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
    HotkeyBackendKind.JNATIVEHOOK -> "Atalhos globais ativos (JNativeHook)."
    HotkeyBackendKind.PORTAL -> "Atalhos globais pelo portal do sistema."
    HotkeyBackendKind.UNAVAILABLE -> "Atalhos globais indisponíveis nesta sessão. Use o menu da bandeja."
}

internal fun hotkeyBackendFailureMessage(kind: HotkeyBackendKind): String = when (kind) {
    HotkeyBackendKind.PORTAL ->
        "O portal de atalhos do sistema não respondeu. Use o menu da bandeja ou configure pelo sistema."
    HotkeyBackendKind.JNATIVEHOOK ->
        "Não foi possível instalar o gancho global de teclado. Use o menu da bandeja."
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
        val detail = backend.lastError?.takeIf { it.isNotBlank() }
        statusMessage = if (detail == null) {
            hotkeyBackendFailureMessage(kind)
        } else {
            "${hotkeyBackendFailureMessage(kind)} Detalhe: $detail."
        }
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
        val updated = backend.update(hotkeyBindings(fullScreen, region, quickDict, hover, hoverCycle))
        if (!updated && activeKind != HotkeyBackendKind.UNAVAILABLE) {
            statusMessage = hotkeyBackendFailureMessage(activeKind)
        }
        return updated
    }

    fun stop() = backend.stop()
}

internal fun createHotkeyBackend(kind: HotkeyBackendKind, callbacks: HotkeyCallbacks): HotkeyBackend =
    when (kind) {
        HotkeyBackendKind.JNATIVEHOOK -> JNativeHookHotkeys(callbacks)
        HotkeyBackendKind.PORTAL -> PortalGlobalShortcuts(callbacks)
        HotkeyBackendKind.UNAVAILABLE -> UnavailableHotkeys()
    }
