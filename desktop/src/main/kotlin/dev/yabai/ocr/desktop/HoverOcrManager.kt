package dev.yabai.ocr.desktop

import dev.yabai.ocr.core.dict.CompactDictionary
import dev.yabai.ocr.core.dict.LookupEngine
import dev.yabai.ocr.core.dict.TokenMatch
import dev.yabai.ocr.core.lens.EncodedImage
import dev.yabai.ocr.core.lens.LensClient
import dev.yabai.ocr.core.lens.LensOptions
import java.awt.MouseInfo
import java.awt.Point
import java.awt.Rectangle
import java.awt.Robot
import java.awt.GraphicsEnvironment
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
 *
 * O modo é contínuo: enquanto ativo, um laço observa a posição do mouse e, depois que ele para por
 * alguns décimos de segundo, recorta a tela ao redor do cursor, manda o recorte para o Lens,
 * descobre qual palavra está debaixo do ponteiro e entrega o verbete ao tooltip. A versão anterior
 * só tinha `triggerAtCursor()` chamado pelo atalho — não havia nenhum observador do mouse, então
 * passar o cursor nunca fazia nada — e refazia a captura fixa de 320×120 mesmo com o ponteiro perto
 * da borda, onde o `Robot` lançava exceção.
 *
 * A precisão vem de duas escolhas: o recorte é maior (640×360) e a palavra não é adivinhada a partir
 * da frase inteira — [HoverTextSelector] usa a caixa de cada linha devolvida pelo Lens e a posição
 * do cursor dentro dela para escolher o verbete, inclusive em texto vertical.
 */
class HoverOcrManager(
    private val onResultsReady: (List<TokenMatch>, Point) -> Unit,
    private val onDismiss: () -> Unit,
    private val onStatus: (String) -> Unit = {},
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

    private val robots = HashMap<Int, Robot>()
    private var lensClient: LensClient? = null

    private var engine: LookupEngine? = null
    private var engineDir: String? = null
    private var engineStamp = 0L

    fun isRunning(): Boolean = isEnabled

    /** Liga/desliga o modo contínuo. Ao ligar, já tenta uma leitura no ponto atual. */
    fun toggle(): Boolean {
        setEnabled(!isEnabled)
        return isEnabled
    }

    fun setEnabled(enabled: Boolean): Boolean {
        if (isEnabled == enabled) return isEnabled
        isEnabled = enabled

        if (enabled) {
            gate.reset()
            startPolling()
            publishStatus("Modo Hover ativo — pare o mouse sobre um texto japonês.")
            triggerAtCursor(force = true)
        } else {
            stopPolling()
            dismiss()
            publishStatus("Modo Hover desativado.")
        }
        return isEnabled
    }

    /** Bounds of the visible tooltip, in screen pixels. Used to keep the pointer on it. */
    fun setTooltipBounds(bounds: Rectangle?) {
        tooltipBounds = bounds
    }

    /** Executa uma leitura única; o atalho global usa isto ao ativar o modo. */
    fun triggerAtCursor(force: Boolean = false) {
        if (!force && !isEnabled) return
        val point = pointerLocation() ?: return
        lookupAt(point)
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

        // The tooltip itself is under the pointer while the user reads it; moving over the word or
        // the card must not restart OCR. A short grace period lets the pointer cross the gap
        // between the word and the card without closing it.
        if (tooltipVisible) {
            if (dismissGate.shouldClose(point, targetBox, tooltipBounds, now)) {
                dismiss()
                return
            }
            if (HoverGeometry.contains(targetBox, point, TARGET_MARGIN) ||
                HoverGeometry.contains(tooltipBounds, point)
            ) {
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

    private fun lookupAt(point: Point) {
        if (!busy.compareAndSet(false, true)) return
        gate.markTriggered(point)

        scope.launch {
            try {
                val screen = HoverGeometry.screenBounds()
                val cropRect = HoverGeometry.planCrop(point, screen, CROP_WIDTH, CROP_HEIGHT)
                val image = robotFor(cropRect).createScreenCapture(cropRect)
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
                val filter = currentSettings.dictionaryFilter
                val only = filter.takeIf { it.isNotBlank() }?.let { setOf(it) } ?: emptySet()
                val hit = HoverTextSelector.select(result, localPoint, lookup, only)
                if (hit == null) {
                    val text = result.joinLines().trim()
                    if (text.isBlank()) {
                        publishStatus("Nenhum texto reconhecido perto do cursor.")
                    } else {
                        publishStatus("Texto lido, mas sem verbete: ${text.take(40)}")
                    }
                    dismiss()
                    return@launch
                }

                // If the pointer moved while Lens was working, the card would appear next to a word
                // the user already left. Discard it; the gate starts a fresh request at the new spot.
                val current = pointerLocation()
                if (current == null || current.distance(point) > STALE_RADIUS_PX) {
                    return@launch
                }
                if (!isEnabled) {
                    dismiss()
                    return@launch
                }

                targetBox = HoverGeometry.toScreen(hit.tokenBox, cropRect, image.width, image.height)
                tooltipVisible = true
                dismissGate.reset()
                publishStatus("${hit.match.entry.term} — ${hit.match.dictionaryTitle}")
                val matches = listOf(hit.match)
                SwingUtilities.invokeLater { onResultsReady(matches, point) }
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
        if (wasVisible) {
            // The pointer left a card the user was reading; coming back to the same word should
            // look it up again. A failed lookup must *not* do this, or the still pointer would
            // retry the same empty spot every 60 ms.
            gate.forgetTrigger()
            SwingUtilities.invokeLater { onDismiss() }
        }
    }

    private fun publishStatus(message: String) {
        println("[Hover] $message")
        SwingUtilities.invokeLater { onStatus(message) }
    }

    private fun pointerLocation(): Point? =
        runCatching { MouseInfo.getPointerInfo()?.location }.getOrNull()

    /**
     * A `Robot` bound to the monitor the crop belongs to. One `Robot` per screen is cached: the
     * constructor is cheap but not free, and a `Robot` asked to capture another monitor's pixels
     * comes back black on Windows.
     */
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

    /**
     * Opens the dictionaries once, not on every hover. Opening every archive takes seconds and
     * leaks file handles if it is repeated; the directory stamp lets an import made while the app
     * is running be noticed without rebuilding the index on every mouse stop.
     */
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

        val dictionaries = if (dir.isDirectory) {
            dir.listFiles()
                ?.filter { it.extension == "yabai" || it.isDirectory }
                ?.mapNotNull { file -> runCatching { CompactDictionary.open(file) }.getOrNull() }
                .orEmpty()
        } else {
            emptyList()
        }
        engine = if (dictionaries.isEmpty()) null else LookupEngine(dictionaries)
        return engine
    }

    private companion object {
        const val POLL_MILLIS = 60L
        const val DISMISS_GRACE_MS = 220L

        /** A larger crop gives Lens the rest of the line, which is where conjugation context lives. */
        const val CROP_WIDTH = 640
        const val CROP_HEIGHT = 360

        const val TARGET_MARGIN = 6
        const val STALE_RADIUS_PX = 56.0
    }
}
