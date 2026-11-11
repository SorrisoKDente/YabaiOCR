package dev.yabai.ocr.desktop

import dev.yabai.ocr.core.dict.CompactDictionary
import dev.yabai.ocr.core.dict.LookupEngine
import dev.yabai.ocr.core.dict.TokenMatch
import dev.yabai.ocr.core.lens.EncodedImage
import dev.yabai.ocr.core.lens.LensClient
import dev.yabai.ocr.core.lens.LensOptions
import dev.yabai.ocr.core.lens.LensResult
import java.awt.GraphicsEnvironment
import java.awt.Point
import java.awt.Rectangle
import java.awt.Robot
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean
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
    private val onResultsReady: (List<TokenMatch>, Point, String) -> Unit,
    private val onDismiss: () -> Unit,
    private val onStatus: (String) -> Unit = {},
    private val backendKind: HoverBackendKind = HoverBackendKind.AWT,
    private val pointerSource: PointerSource = AwtPointerSource(),
    private val captureProvider: ((Rectangle) -> BufferedImage)? = null,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gate = HoverGate()
    private val dismissGate = HoverDismissGate(graceMillis = DISMISS_GRACE_MS, margin = TARGET_MARGIN)
    private val busy = AtomicBoolean(false)

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

    @Volatile
    private var lastLensResult: LensResult? = null

    private val robots = HashMap<Int, Robot>()
    private var lensClient: LensClient? = null

    private var engine: LookupEngine? = null
    private var engineDir: String? = null
    private var engineStamp = 0L

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
            // On KWin the pointer arrives from the D-Bus bridge; there is no global pointer to poll.
            if (backendKind != HoverBackendKind.KWIN) startPolling()
            publishStatus(
                if (backendKind == HoverBackendKind.KWIN) {
                    "Modo Hover ativo (ponteiro do KWin) — pare o mouse sobre um texto japonês."
                } else {
                    "Modo Hover ativo — pare o mouse sobre um texto japonês."
                },
            )
            triggerAtCursor(force = true)
        } else {
            stopPolling()
            dismiss()
            publishStatus("Modo Hover desativado.")
        }
        return isEnabled
    }

    fun setTooltipBounds(bounds: Rectangle?) {
        tooltipBounds = bounds
    }

    fun triggerAtCursor(force: Boolean = false) {
        if (!force && !isEnabled) return
        val point = pointerLocation() ?: return
        lookupAt(point)
    }

    /**
     * Called by the KWin D-Bus bridge on every pointer move.
     *
     * The script already rate-limits and [KwinPointerSource] drops repeated positions, so a resting
     * pointer does not run OCR again. Dismissal still follows movement, like the polled path.
     */
    fun onPointerMoved(point: Point) {
        (pointerSource as? KwinPointerSource)?.update(point)
        if (isEnabled) tick(point)
    }

    fun getInstalledDictionaryTitles(): List<String> {
        val settings = DesktopSettings()
        val dir = settings.dictDirectory()
        return if (dir.isDirectory) {
            dir.listFiles()
                ?.filter { it.extension == "yabai" || it.isDirectory }
                ?.mapNotNull { file -> runCatching { CompactDictionary.open(file)?.use { it.info.title } }.getOrNull() }
                .orEmpty()
        } else emptyList()
    }

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
        val currentSettings = DesktopSettings()
        val lookup = lookupEngine(currentSettings)

        if (result != null && localPoint != null && screenPoint != null && cropRect != null && lookup != null) {
            val only = filter.takeIf { it.isNotBlank() }?.let { setOf(it) } ?: emptySet()
            var hit = HoverTextSelector.select(result, localPoint, lookup, only)

            // Fallback: se o dicionário escolhido não possui a palavra, busca em todos para o usuário não ficar no vácuo
            if (hit == null && only.isNotEmpty()) {
                hit = HoverTextSelector.select(result, localPoint, lookup, emptySet())
            }

            if (hit != null) {
                val tokenRect = HoverGeometry.toScreen(hit.tokenBox, cropRect, cropRect.width, cropRect.height)
                val lineRect = hit.line.box?.let { HoverGeometry.toScreen(it, cropRect, cropRect.width, cropRect.height) }
                targetBox = if (lineRect != null) tokenRect.union(lineRect) else tokenRect
                tooltipVisible = true
                dismissGate.reset()
                publishStatus("${hit.match.entry.term} — ${hit.match.dictionaryTitle}")
                val matches = listOf(hit.match)
                SwingUtilities.invokeLater { onResultsReady(matches, screenPoint, filter) }
                return
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
        scope.cancel()
        runCatching { engine?.close() }
        engine = null
        runCatching { lensClient?.close() }
        lensClient = null
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (isActive && isEnabled) {
                pointerLocation()?.let { tick(it) }
                delay(POLL_MILLIS)
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

        if (busy.get()) return
        if (gate.observe(point, now)) {
            lookupAt(point)
        }
    }

    private fun lookupAt(point: Point, overrideFilter: String? = null) {
        if (!busy.compareAndSet(false, true)) return
        gate.markTriggered(point)

        scope.launch {
            try {
                val screen = HoverGeometry.screenAt(point)
                val cropRect = HoverGeometry.planCrop(point, screen, CROP_WIDTH, CROP_HEIGHT)
                // Windows/X11 keeps the per-device Robot; Wayland uses the screencast backend.
                val image = captureProvider?.invoke(cropRect)
                    ?: robotFor(cropRect).createScreenCapture(cropRect)
                if (image.width <= 0 || image.height <= 0) {
                    fail("Recorte de tela vazio.")
                    return@launch
                }

                val bytes = ByteArrayOutputStream().use { out ->
                    check(ImageIO.write(image, "png", out)) { "PNG encoder indisponível" }
                    out.toByteArray()
                }

                val result = lens().recognize(
                    EncodedImage(bytes, image.width, image.height),
                    options = LensOptions(language = "ja"),
                )
                if (!isEnabled) {
                    dismiss()
                    return@launch
                }

                val currentSettings = DesktopSettings()
                val lookup = lookupEngine(currentSettings)
                if (lookup == null) {
                    fail("Nenhum dicionário instalado — abra «Gerenciar Dicionários».")
                    return@launch
                }

                val localPoint = HoverGeometry.localPoint(point, cropRect, image.width, image.height)
                val filter = overrideFilter ?: currentSettings.hoverDictionaryFilter.ifBlank { currentSettings.dictionaryFilter }
                val only = filter.takeIf { it.isNotBlank() }?.let { setOf(it) } ?: emptySet()

                // Salva o cache da última leitura do Lens
                lastLensResult = result
                lastLocalPoint = localPoint
                lastScreenPoint = point
                lastCropRect = cropRect

                var hit = HoverTextSelector.select(result, localPoint, lookup, only)

                // Fallback: Se o dicionário específico não contém a palavra, busca em todos
                if (hit == null && only.isNotEmpty()) {
                    hit = HoverTextSelector.select(result, localPoint, lookup, emptySet())
                }

                if (hit == null) {
                    val text = result.joinLines().trim()
                    if (text.isBlank()) {
                        publishStatus("Nenhum texto reconhecido perto do cursor.")
                    } else {
                        publishStatus("Texto lido, mas sem verbete no dicionário: ${text.take(40)}")
                    }
                    dismiss()
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

                val tokenRect = HoverGeometry.toScreen(hit.tokenBox, cropRect, image.width, image.height)
                val lineRect = hit.line.box?.let {
                    HoverGeometry.toScreen(it, cropRect, image.width, image.height)
                }
                targetBox = if (lineRect != null) tokenRect.union(lineRect) else tokenRect
                tooltipVisible = true
                dismissGate.reset()
                publishStatus("${hit.match.entry.term} — ${hit.match.dictionaryTitle}")
                val matches = listOf(hit.match)
                SwingUtilities.invokeLater { onResultsReady(matches, point, filter) }
            } catch (e: Exception) {
                fail("Falha no OCR: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                busy.set(false)
            }
        }
    }

    private fun fail(message: String) {
        publishStatus(message)
        dismiss()
    }

    private fun dismiss() {
        val wasVisible = tooltipVisible
        tooltipVisible = false
        targetBox = null
        dismissGate.reset()
        gate.reset() // Reseta o portão para que novos movimentos do mouse voltem a funcionar imediatamente!
        if (wasVisible) {
            SwingUtilities.invokeLater { onDismiss() }
        }
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

    private fun lens(): LensClient {
        lensClient?.let { return it }
        return LensClient().also { lensClient = it }
    }

    private fun lookupEngine(settings: DesktopSettings): LookupEngine? {
        val dir = settings.dictDirectory()
        val path = dir.absolutePath
        val stamp = dir.lastModified()

        if (engineDir == path && engineStamp == stamp) {
            return engine
        }

        runCatching { engine?.close() }
        engine = null
        engineDir = path
        engineStamp = stamp

        engine = DesktopDictLoader.loadEngine(settings)
        return engine
    }

    private companion object {
        const val POLL_MILLIS = 60L
        const val DISMISS_GRACE_MS = 220L
        const val CROP_WIDTH = 640
        const val CROP_HEIGHT = 360
        const val TARGET_MARGIN = 6
        const val STALE_RADIUS_PX = 56.0
    }
}
