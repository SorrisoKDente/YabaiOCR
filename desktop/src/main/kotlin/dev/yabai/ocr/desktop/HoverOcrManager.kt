package dev.yabai.ocr.desktop

import dev.yabai.ocr.core.dict.TokenMatch
import dev.yabai.ocr.core.lens.LensResult
import dev.yabai.ocr.desktop.i18n.DesktopStrings
import java.awt.Dimension
import java.awt.GraphicsEnvironment
import java.awt.Point
import java.awt.Rectangle
import java.awt.Robot
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import javax.swing.SwingUtilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * OCR em tempo real sob o ponteiro — o «hover» no estilo Yomitan/Rikaichan.
 */
internal class HoverOcrManager(
    /**
     * The card's content: the matches, where it goes, the filter in use, and — when empty matches
     * are because the chosen dictionary has no entry — that dictionary's name.
     */
    private val onResultsReady: (List<TokenMatch>, Point, String, String?) -> Unit,
    private val onDismiss: () -> Unit,
    private val onStatus: (String) -> Unit = {},
    private val backendKind: HoverBackendKind = HoverBackendKind.AWT,
    private val pointerSource: PointerSource = AwtPointerSource(),
    private val captureProvider: ((Rectangle) -> BufferedImage)? = null,
    private val dictionaryCache: HoverDictionaryCache = HoverDictionaryCache(),
    /**
     * Lets the offline reader's ONNX sessions go when the hover is turned off: one recognition
     * allocates ~245 MB of native memory that stays with the process until the sessions are closed
     * (measured; see [DesktopPPOcr]). Injected so the lifecycle can be tested without models.
     */
    private val releaseOcrSessions: () -> Unit = { DesktopPPOcr.close() },
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    /**
     * How often the gate is fed. Every backend is polled, including the compositor bridges: their
     * events only arrive when the pointer *moves*, so a gate waiting to retry a failed read, a
     * dismissal grace period or a lookup dropped because both slots were busy could never advance
     * while the hand rested — the card stayed away until the pointer was moved off the word and
     * back. On a bridge the poll only feeds the gate the last known position (no capture, no OCR),
     * so it can run slower than the AWT poll that is the pointer source itself.
     */
    private val pollMillis = if (backendKind.usesCompositorBridge) BRIDGE_PUMP_MILLIS else POLL_MILLIS
    private val gate = HoverGate(stableMillis = DesktopSettings().hoverRestMillis)
    private val dismissGate = HoverDismissGate(graceMillis = DISMISS_GRACE_MS, margin = TARGET_MARGIN)
    /** Keeps the newest read and drops the older answers instead of locking the hover (see D83). */
    private val lookups = HoverLookupTracker()

    @Volatile
    private var isEnabled = false

    @Volatile
    private var pollJob: Job? = null

    @Volatile
    private var tooltipBounds: Rectangle? = null

    @Volatile
    private var targetBox: Rectangle? = null

    @Volatile
    private var tooltipVisible = false

    @Volatile
    private var lastScreenPoint: Point? = null

    @Volatile
    private var lastLocalPoint: Point? = null

    @Volatile
    private var lastCropRect: Rectangle? = null

    /** Size of the image Lens received; a scaled display makes it larger than [lastCropRect]. */
    @Volatile
    private var lastImageSize: Dimension? = null

    /**
     * Engine and time of the last recognition («Google Lens 1 180 ms»), carried by every status
     * line. This is what tells a slow Lens from a Lens that fell back to the offline reader, and
     * from a Lens whose upload grew because the page is artwork (D83).
     */
    @Volatile
    private var lastAnswer: String? = null

    @Volatile
    private var lastLensResult: LensResult? = null

    private val robots = HashMap<Int, Robot>()

    fun isRunning(): Boolean = isEnabled

    fun toggle(): Boolean {
        setEnabled(!isEnabled)
        return isEnabled
    }

    fun setEnabled(enabled: Boolean): Boolean {
        if (enabled && backendKind == HoverBackendKind.UNSUPPORTED) {
            publishStatus(hoverBackendLabel(HoverBackendKind.UNSUPPORTED))
            return false
        }
        if (isEnabled == enabled) return isEnabled
        isEnabled = enabled

        if (enabled) {
            gate.reset()
            // Read again on every start: the Ajustes window may have changed it while the hover was off.
            gate.stableMillis = DesktopSettings().hoverRestMillis
            startPolling()
            publishStatus(
                when (backendKind) {
                    HoverBackendKind.KWIN ->
                        "Modo Hover ativo (ponteiro do KWin) — pare o mouse sobre um texto japonês."
                    HoverBackendKind.GNOME ->
                        "Modo Hover ativo (ponteiro do GNOME Shell) — pare o mouse sobre um texto japonês."
                    else -> "Modo Hover ativo — pare o mouse sobre um texto japonês."
                },
            )
            triggerAtCursor(force = true)
        } else {
            stopPolling()
            dismiss()
            // Off means off: the dictionaries are ~123 MB of key index on a real install and the
            // offline reader's sessions are ~245 MB of native memory, and the hover is using
            // neither. The next hover opens them again.
            dictionaryCache.release()
            // Closing the ONNX sessions waits for a recognition in flight, and this runs on the UI
            // thread (tray, shortcut, Settings); the memory is given back either way.
            scope.launch { releaseOcrSessions() }
            publishStatus("Modo Hover desativado.")
        }
        return isEnabled
    }

    fun setTooltipBounds(bounds: Rectangle?) {
        tooltipBounds = bounds
    }

    /**
     * How long the pointer must rest before a read. Changing it applies to the next movement, without
     * restarting the app or the hover.
     */
    fun setRestMillis(millis: Long) {
        gate.stableMillis = millis
    }

    fun triggerAtCursor(force: Boolean = false) {
        if (!force && !isEnabled) return
        val point = pointerLocation() ?: return
        lookupAt(point)
    }

    /**
     * Called by the compositor bridge (KWin script or GNOME extension) on every pointer move.
     *
     * The bridge already rate-limits and [BridgePointerSource] drops repeated positions, so a
     * resting pointer does not run OCR again. Dismissal still follows movement, like the polled
     * path.
     */
    fun onPointerMoved(point: Point) {
        (pointerSource as? BridgePointerSource)?.update(point)
        if (isEnabled) tick(point)
    }

    /**
     * Titles the card lets the user switch between.
     *
     * From each dictionary's `meta.json`, never by opening it: the card asks for this list on every
     * composition, and opening the compiled dictionaries reads their whole key index (D81).
     */
    fun getInstalledDictionaryTitles(): List<String> =
        DesktopDictLoader.installedTitles(DesktopSettings().dictDirectory())

    /** Define um filtro de dicionário específico para o Hover e atualiza instantaneamente. */
    fun setFilterAndRefresh(newFilter: String) {
        val settings = DesktopSettings()
        settings.hoverDictionaryFilter = newFilter
        refreshWithCurrentFilter(newFilter)
    }

    /** Alterna para o próximo dicionário instalado e atualiza instantaneamente sem mover a janela. */
    fun cycleDictionaryAndRefresh(): String {
        val settings = DesktopSettings()
        val titles = getInstalledDictionaryTitles()
        val currentFilter = settings.hoverDictionaryFilter
        val nextFilter = when {
            titles.isEmpty() -> ""
            currentFilter.isBlank() -> titles.first()
            else -> {
                val idx = titles.indexOf(currentFilter)
                if (idx == -1 || idx == titles.lastIndex) "" else titles[idx + 1]
            }
        }
        settings.hoverDictionaryFilter = nextFilter
        refreshWithCurrentFilter(nextFilter)
        return nextFilter
    }

    private fun refreshWithCurrentFilter(filter: String) {
        val displayFilter = if (filter.isBlank()) "Todos os Dicionários" else filter
        publishStatus("Dicionário do Hover alterado para: $displayFilter")

        val result = lastLensResult
        val localPoint = lastLocalPoint
        val screenPoint = lastScreenPoint
        val cropRect = lastCropRect
        // The kept boxes are in the pixels of the image Lens received, which a scaled display makes
        // larger than the crop: converting them with the crop's size put the card's anchor away
        // from the word and it blinked on this path too.
        val imageSize = lastImageSize
        val currentSettings = DesktopSettings()
        val lookup = dictionaryCache.get(currentSettings)

        if (result != null && localPoint != null && screenPoint != null && cropRect != null) {
            // The chosen dictionary is the answer. Switching to another one behind the user's back
            // showed a definition from a source they had not picked; a miss is said in the card —
            // both cases answered from the text already read, never by capturing the screen again.
            val answer = cachedHoverAnswer(
                result = result,
                localPoint = localPoint,
                lookup = lookup?.let(::hoverQuery),
                filter = filter,
            )

            when (answer) {
                is CachedHoverAnswer.Hit -> {
                    val hit = answer.hit
                    val tokenRect = HoverGeometry.toScreen(hit.tokenBox, cropRect, imageSize)
                    val lineRect = hit.line.box?.let { HoverGeometry.toScreen(it, cropRect, imageSize) }
                    targetBox = if (lineRect != null) tokenRect.union(lineRect) else tokenRect
                    tooltipVisible = true
                    dismissGate.reset()
                    publishStatus(withCapture("${hit.match.entry.term} — ${hit.match.dictionaryTitle}"))
                    val matches = listOf(hit.match)
                    SwingUtilities.invokeLater { onResultsReady(matches, screenPoint, filter, null) }
                    return
                }

                is CachedHoverAnswer.MissingEntry -> {
                    showMissingEntry(
                        answer.filter,
                        screenPoint,
                        HoverGeometry.toScreen(answer.word, cropRect, imageSize),
                    )
                    return
                }

                CachedHoverAnswer.NeedsLookup -> Unit
            }
        }

        // Se não havia cache ou não encontrou, dispara no cursor atual
        val pt = screenPoint ?: pointerLocation()
        if (pt != null) {
            lookupAt(pt, overrideFilter = filter)
        }
    }

    fun close() {
        isEnabled = false
        pollJob?.cancel()
        pollJob = null
        dictionaryCache.release()
        scope.cancel()
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (isActive && isEnabled) {
                pointerLocation()?.let { tick(it) }
                delay(pollMillis)
            }
        }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
        gate.reset()
    }

    private fun tick(point: Point) {
        val now = System.currentTimeMillis()

        if (tooltipVisible && HoverGeometry.contains(tooltipBounds, point)) {
            dismissGate.reset()
            return
        }

        if (tooltipVisible) {
            if (dismissGate.shouldClose(point, targetBox, null, now)) {
                dismiss()
                return
            }
        } else {
            dismissGate.reset()
        }

        if (gate.observe(point, now)) {
            lookupAt(point)
        }
    }

    private fun lookupAt(point: Point, overrideFilter: String? = null) {
        // A read already running used to swallow every word the pointer reached while it lasted.
        // Now the newest lookup wins and the older answer is dropped (HoverLookupTracker).
        val token = lookups.begin()
        if (token == null) {
            // Both slots are busy: let the gate ask again as soon as one of them finishes.
            gate.reset()
            return
        }
        gate.markTriggered(point)

        scope.launch {
            try {
                val screen = HoverGeometry.screenAt(point)
                // Full monitor height: a word split between two columns has half of itself at the
                // bottom of one column and half at the top of the next, far outside a small crop.
                val cropRect = HoverGeometry.hoverCrop(point, screen)
                // Windows/X11 keeps the per-device Robot; Wayland uses the screencast backend.
                val image = captureProvider?.invoke(cropRect)
                    ?: robotFor(cropRect).createScreenCapture(cropRect)
                if (image.width <= 0 || image.height <= 0) {
                    fail("Recorte de tela vazio.", point)
                    return@launch
                }

                // The point the answer is about, in the pixels the recogniser received: only the
                // lines around it are read, which is what keeps a hover on a full page from
                // costing a full-page recognition (see PPOcrEngine.recognizePixels).
                val localPoint = HoverGeometry.localPoint(point, cropRect, image.width, image.height)
                val answer = DesktopOcr.recognize(image, DesktopSettings(), focus = localPoint)
                // The pointer is already reading another word: this answer is history.
                if (!lookups.isCurrent(token)) return@launch
                val result = answer.result
                lastAnswer = "${DesktopOcr.label(answer.engine)} ${result.elapsedMs} ms"
                if (!isEnabled) {
                    dismiss()
                    return@launch
                }

                val currentSettings = DesktopSettings()
                val lookup = dictionaryCache.get(currentSettings)
                if (lookup == null) {
                    fail(DesktopStrings.hoverNoDictsInstalled)
                    return@launch
                }

                val filter = overrideFilter ?: currentSettings.hoverDictionaryFilter.ifBlank { currentSettings.dictionaryFilter }

                // Salva o cache da última leitura do Lens
                val imageSize = Dimension(image.width, image.height)
                lastLensResult = result
                lastLocalPoint = localPoint
                lastScreenPoint = point
                lastCropRect = cropRect
                lastImageSize = imageSize

                val query = hoverQuery(lookup)
                val hit = scopedHoverHit(result, localPoint, query, filter)

                if (hit == null) {
                    // «No entry in this dictionary» only when there is a Japanese word under the
                    // pointer to be missing; anywhere else the card simply goes away.
                    when (val miss = classifyHoverMiss(result, localPoint, query, filter)) {
                        HoverMiss.NoText -> {
                            publishStatus(DesktopStrings.hoverNoTextNearCursor)
                            dismiss()
                        }
                        is HoverMiss.NoMatch -> {
                            publishStatus(DesktopStrings.hoverNoMatchForText(miss.text.take(40)))
                            dismiss()
                        }
                        is HoverMiss.MissingEntry -> {
                            // The pointer may have walked away while the OCR ran.
                            val current = pointerLocation()
                            if (current == null || current.distance(point) > STALE_RADIUS_PX || !isEnabled) {
                                return@launch
                            }
                            showMissingEntry(
                                miss.filter,
                                point,
                                HoverGeometry.toScreen(miss.word, cropRect, imageSize),
                            )
                        }
                    }
                    return@launch
                }

                val current = pointerLocation()
                if (current == null || current.distance(point) > STALE_RADIUS_PX) {
                    return@launch
                }
                if (!isEnabled) {
                    dismiss()
                    return@launch
                }

                val tokenRect = HoverGeometry.toScreen(hit.tokenBox, cropRect, imageSize)
                val lineRect = hit.line.box?.let { HoverGeometry.toScreen(it, cropRect, imageSize) }
                targetBox = if (lineRect != null) tokenRect.union(lineRect) else tokenRect
                tooltipVisible = true
                dismissGate.reset()
                publishStatus(withCapture("${hit.match.entry.term} — ${hit.match.dictionaryTitle}"))
                val matches = listOf(hit.match)
                SwingUtilities.invokeLater { onResultsReady(matches, point, filter, null) }
            } catch (e: Exception) {
                fail(DesktopStrings.hoverOcrFailure(e.message ?: e.javaClass.simpleName), point)
            } finally {
                lookups.finish(token)
            }
        }
    }

    /**
     * Shows the card saying the chosen dictionary has no entry for the word under the pointer.
     *
     * The card, not a window of its own: a notice window would open on every hover, on top of the
     * very text being read. [wordBox] is the word's own rectangle on screen and becomes the anchor
     * the card is held by — the same role the token box plays for a hit. With no anchor the card
     * counted as «the pointer left» once the grace period ran out, closed, and the gate opened it
     * again on the same spot: the card blinked instead of staying put.
     */
    private fun showMissingEntry(filter: String, point: Point, wordBox: Rectangle) {
        publishStatus(DesktopStrings.hoverDictionaryMissingEntry(filter))
        targetBox = wordBox
        tooltipVisible = true
        dismissGate.reset()
        SwingUtilities.invokeLater { onResultsReady(emptyList(), point, filter, filter) }
    }

    /**
     * A read that broke on the way, with no answer to be quiet about.
     *
     * [retryAt] is the spot to try again: an empty capture or a failed OCR is usually transient, and
     * [dismiss] alone would keep that spot quiet until the pointer walked away — leaving the word the
     * user was resting on unreadable. A read that simply found no text keeps the spot quiet instead;
     * only a failure asks for another attempt, paced and capped by [HoverGate.retryLater].
     */
    private fun fail(message: String, retryAt: Point? = null) {
        publishStatus(message)
        dismiss()
        if (retryAt != null) gate.retryLater(retryAt, System.currentTimeMillis())
    }

    private fun dismiss() {
        val wasVisible = tooltipVisible
        tooltipVisible = false
        targetBox = null
        dismissGate.reset()
        // The spot that just answered stays quiet until the pointer walks away from it. Resetting
        // the gate here re-armed it, and a pointer parked on background re-captured the screen —
        // sound and all — at every rest interval, forever (see HoverGate.keepQuiet).
        gate.keepQuiet()
        if (wasVisible) {
            SwingUtilities.invokeLater { onDismiss() }
        }
    }

    /**
     * The status line with the capture that produced it: which transport served the screen and how
     * long it took. The card is only as fast as the capture and the OCR behind it, and on Wayland
     * the same app can be taking a direct `ScreenShot2` frame (tens of milliseconds) or falling back
     * to the portal (a permission dialog, seconds) — the line is what tells the two apart.
     */
    private fun withCapture(message: String): String {
        val engine = lastAnswer?.let { " · $it" }.orEmpty()
        val capture = CaptureDiagnostics.lastSummary()?.let { " · $it" }.orEmpty()
        return "$message$engine$capture"
    }

    private fun publishStatus(message: String) {
        println("[Hover] $message")
        SwingUtilities.invokeLater { onStatus(message) }
    }

    private fun pointerLocation(): Point? = pointerSource.current()

    @Synchronized
    private fun robotFor(crop: Rectangle): Robot {
        val devices = GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices
        val index = HoverGeometry.monitorIndex(
            devices.map { runCatching { it.defaultConfiguration.bounds }.getOrDefault(Rectangle()) },
            crop,
        )
        robots[index]?.let { return it }

        val created = runCatching { Robot(devices.getOrNull(index)) }
            .recoverCatching { Robot() }
            .getOrThrow()
        robots[index] = created
        return created
    }

    private companion object {
        const val POLL_MILLIS = 60L
        /** Resting pointer on a bridge backend: no capture, only gate bookkeeping. */
        const val BRIDGE_PUMP_MILLIS = 120L
        const val DISMISS_GRACE_MS = 220L
        const val TARGET_MARGIN = 6
        const val STALE_RADIUS_PX = 56.0
    }
}
