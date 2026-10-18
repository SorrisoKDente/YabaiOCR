package dev.yabai.ocr.service

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import dev.yabai.ocr.AppSettings
import dev.yabai.ocr.MainActivity
import dev.yabai.ocr.R
import dev.yabai.ocr.UsageTracker
import dev.yabai.ocr.capture.ScreenCaptureSession
import dev.yabai.ocr.dict.DictionaryRepository
import dev.yabai.ocr.ocr.OcrDispatcher
import dev.yabai.ocr.ocr.OcrOutcome
import dev.yabai.ocr.overlay.OverlayController
import dev.yabai.ocr.overlay.ResultOverlay
import dev.yabai.ocr.translate.OfflineTranslator
import dev.yabai.ocr.util.BlockImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * The foreground service that owns every piece of the floating experience: the screen-capture
 * session, the bubble and the recognition pipeline.
 *
 * Flow of a capture:
 *  1. hide all overlay windows (so they never appear in the screenshot),
 *  2. wait a beat for the window manager to actually apply that,
 *  3. grab one fresh frame, crop it to the selection,
 *  4. OCR it and show the result panel (plus copy to the clipboard, if enabled).
 */
class FloatingOcrService : Service(), OverlayController.Callbacks {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var settings: AppSettings
    private lateinit var overlays: OverlayController
    private lateinit var dispatcher: OcrDispatcher

    private var capture: ScreenCaptureSession? = null
    private var projection: MediaProjection? = null
    private var projectionCallback: MediaProjection.Callback? = null

    private var lastRegion: Rect? = null
    private var busy = false

    /** Sentence translation and dictionary lookup run after the panel is already on screen. */
    private var translator: OfflineTranslator? = null
    private var analysisJob: Job? = null
    private var lastAnalysedText: String? = null

    /**
     * The text boxes of the last whole-screen capture.
     *
     * Kept after the picker closes so the result panel can offer "trechos" again without paying for
     * another Lens call — those are rate limited, and re-running OCR would also just reproduce the
     * same segmentation.
     */
    private var pendingBlocks: List<PendingBlock> = emptyList()
    private var lastOutcome: OcrOutcome? = null
    private var lastForceOffline = false

    /**
     * The frame the last result came from, kept only while that result is on screen so the image can
     * be shown beside the text.
     *
     * Dropped — not recycled — when the panel closes: a recycled bitmap that a view still points at
     * crashes on the next draw, and dropping the reference is all it takes for the collector to take
     * a full screen's worth of pixels back.
     */
    private var lastCrop: Bitmap? = null

    /**
     * One selectable piece of a whole-screen capture.
     *
     * [rect] is where the box goes on screen, which is what the picker draws and hit-tests; [cropRect]
     * is the same box in the pixels of the captured frame, which is what the panel shows as the image
     * beside the text. Both come from the engine's own box, so neither is guessed from the other.
     */
    private data class PendingBlock(
        val text: String,
        val rect: Rect,
        val cropRect: Rect,
        val lines: List<String>,
    )

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        settings = AppSettings(this)
        dispatcher = OcrDispatcher(settings, UsageTracker(this))
        overlays = OverlayController(this).apply {
            callbacks = this@FloatingOcrService
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val data = intent.projectionData()
                if (data == null || resultCode != Activity.RESULT_OK) {
                    toast(getString(R.string.projection_denied))
                    stopSelf()
                    return START_NOT_STICKY
                }
                startCapture(resultCode, data)
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isRunning = false
        analysisJob?.cancel()
        scope.cancel()
        handler.removeCallbacksAndMessages(null)
        overlays.destroy()
        teardownCapture()
        dispatcher.close()
        // Dictionaries are a few megabytes of index each; drop them with the service.
        DictionaryRepository.get(this).release()
        translator?.close()
        translator = null
        lastCrop = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    // --- startup -------------------------------------------------------------------------------

    private fun startCapture(resultCode: Int, data: Intent) {
        if (!overlays.canDrawOverlays()) {
            toast(getString(R.string.need_overlay_permission))
            stopSelf()
            return
        }

        // Must be a foreground service *before* the projection is used on Android 14+.
        startForegroundWithNotification()

        // Re-arming: the user may be re-granting capture after a rotation or a system revoke.
        teardownCapture()

        val manager = getSystemService(MediaProjectionManager::class.java)
        val newProjection = try {
            manager?.getMediaProjection(resultCode, data)
        } catch (t: Throwable) {
            Log.e(TAG, "getMediaProjection failed", t)
            null
        }

        if (newProjection == null) {
            toast(getString(R.string.projection_denied))
            stopSelf()
            return
        }

        projection = newProjection
        val callback = ScreenCaptureSession.stopCallback { onProjectionStopped() }
        projectionCallback = callback
        newProjection.registerCallback(callback, handler)

        val session = ScreenCaptureSession(this, newProjection)
        if (!session.start()) {
            toast(getString(R.string.capture_failed))
            teardownCapture()
            stopSelf()
            return
        }

        capture = session
        overlays.showBubble()
    }

    private fun teardownCapture() {
        capture?.stop()
        capture = null

        val callback = projectionCallback
        val mp = projection
        projectionCallback = null
        projection = null
        if (mp != null) {
            if (callback != null) runCatching { mp.unregisterCallback(callback) }
            runCatching { mp.stop() }
        }
    }

    private fun onProjectionStopped() {
        if (!isRunning) return
        Log.i(TAG, "screen capture stopped by the system")
        toast(getString(R.string.capture_restart_needed))
        stopSelf()
    }

    // --- OverlayController.Callbacks -----------------------------------------------------------

    override fun onFullScreenRequested() = requestCapture(null)

    override fun onRegionRequested() {
        if (busy) return
        if (capture == null) {
            toast(getString(R.string.capture_failed))
            return
        }
        overlays.showRegionSelector()
    }

    override fun onRegionConfirmed(rect: Rect?) = requestCapture(rect)

    override fun onRegionCancelled() = Unit

    override fun onStopRequested() = stopSelf()

    override fun onOpenSettingsRequested() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        runCatching { startActivity(intent) }
    }

    override fun onCopyRequested(text: String, closeAfter: Boolean) {
        copyToClipboard(text)
        if (closeAfter) overlays.hideResult()
    }

    /**
     * The user corrected the recognised text and asked for another pass. Only the analysis is redone:
     * the screenshot is gone by now, and re-running OCR would just reproduce the same mistake.
     */
    override fun onAnalyseRequested(text: String) {
        if (text.isBlank()) return
        analyse(text)
    }

    override fun onBlockPicked(index: Int) {
        val block = pendingBlocks.getOrNull(index) ?: return
        val outcome = lastOutcome ?: return
        overlays.hideTextPicker()
        // The image beside the text is the box the user picked, not the whole screen: at panel size a
        // full page is a thumbnail, and the point of the image is checking one kanji.
        val crop = lastCrop
        showResultFor(
            outcome,
            block.text,
            block.lines,
            canRepick = true,
            image = if (crop == null) null else BlockImage.from(crop, block.cropRect),
        )
    }

    override fun onPickAllRequested() {
        val outcome = lastOutcome ?: return
        showResultFor(outcome, outcome.text, outcome.lines, canRepick = pendingBlocks.isNotEmpty(), image = lastCrop)
    }

    override fun onRepickRequested() {
        val outcome = lastOutcome ?: return
        if (!overlays.showTextPicker(pickerRects())) {
            // The boxes are gone (the screen changed, or the service was restarted): fall back to the
            // whole capture, which is still in memory.
            showResultFor(outcome, outcome.text, outcome.lines, canRepick = false, image = lastCrop)
        }
    }

    override fun onRetryOfflineRequested() = requestCapture(lastRegion, forceOffline = true)

    override fun onResultDismissed() {
        analysisJob?.cancel()
        analysisJob = null
        // The panel is gone, and with it the only reason to hold a full screen of pixels.
        lastCrop = null
    }

    /**
     * The user turned the captured image on or off from the panel. It is a setting rather than a
     * per-panel choice — whoever wants the comparison wants it every time — so the panel that is
     * open follows the flag the next one will be built from.
     */
    override fun onImageToggleRequested() {
        settings.showImage = !settings.showImage
        overlays.setResultImage(settings.showImage)
    }

    /**
     * The user narrowed the lookup to one dictionary, or asked for all of them again.
     *
     * The choice is a setting, so the next capture starts the same way, and the answer on screen is
     * recomputed at once. That is the whole analysis, the same path an edit takes: the text did not
     * change, but the answer did, and one path for «the answer is stale» is worth a translation that
     * blinks «Traduzindo…» for the moment a local model call takes.
     */
    override fun onDictionaryPicked(title: String?) {
        settings.dictionaryFilter = title.orEmpty()
        val text = lastAnalysedText ?: return
        analyse(text)
    }

    private fun pickerRects(): List<Rect> = pendingBlocks.map { it.rect }

    // --- capture pipeline ----------------------------------------------------------------------

    private fun requestCapture(region: Rect?, forceOffline: Boolean = false) {
        if (busy) return
        val session = capture
        if (session == null) {
            toast(getString(R.string.capture_failed))
            return
        }

        busy = true
        lastRegion = region
        // A new capture supersedes the boxes of the previous one.
        pendingBlocks = emptyList()

        scope.launch {
            var armed = false
            try {
                // Arm before hiding: the frame our own windows disappearing produces is the one
                // we want, and arming first guarantees it is the frame that gets kept.
                session.armCapture()
                armed = true
                overlays.hideAllForCapture()
                // The window manager applies removals asynchronously; without this pause the
                // bubble or the region border could still be inside the screenshot.
                delay(CAPTURE_SETTLE_MS)

                val shot = withContext(Dispatchers.Default) {
                    session.latestFrame(CAPTURE_TIMEOUT_MS)
                }
                session.disarmCapture()
                armed = false
                overlays.restoreAfterCapture()

                if (shot == null) {
                    showError(getString(R.string.capture_failed))
                    return@launch
                }

                val crop = withContext(Dispatchers.Default) { cropToRegion(shot, region) }
                // Kept for the panel's image, and replaced — dropping the reference to the previous
                // one — on every capture.
                lastCrop = crop

                overlays.showResultLoading()
                val outcome = dispatcher.recognize(crop, forceOffline)
                lastOutcome = outcome
                lastForceOffline = forceOffline

                // A whole-screen capture gets the Lens treatment: the recognised paragraphs are drawn
                // as boxes over the live screen and the user picks one. A region capture already is a
                // selection, and a screen with a single paragraph has nothing to pick between, so
                // both of those go straight to the panel.
                if (region == null && settings.pickText && outcome.blocks.size > 1) {
                    pendingBlocks = placeBlocks(outcome, shot)
                    if (pendingBlocks.isNotEmpty() && overlays.showTextPicker(pickerRects())) {
                        overlays.hideResult()
                        return@launch
                    }
                    pendingBlocks = emptyList()
                }

                // Panel first, analysis after: the text is readable immediately and the dictionary
                // and translation drop in when they are ready.
                showResultFor(outcome, outcome.text, outcome.lines, canRepick = false, image = crop)
            } catch (t: Throwable) {
                Log.e(TAG, "capture pipeline failed", t)
                showError(t.message ?: getString(R.string.result_failed))
            } finally {
                if (armed) session.disarmCapture()
                overlays.restoreAfterCapture()
                busy = false
            }
        }
    }

    /**
     * Pushes [text] into the result panel and kicks off the dictionary and translation work.
     *
     * The metadata, the warning notes and the engine still come from the capture that produced
     * [text] — including when the user picked a single box out of a whole-screen capture.
     *
     * The clipboard is written here rather than at capture time so that, when the text comes from a
     * pick, what gets copied is the piece the user chose and not the whole screenshot.
     *
     * [image] is the frame the text came from, which is not always the whole crop: a picked box gets
     * its own piece of it.
     */
    private fun showResultFor(
        outcome: OcrOutcome,
        text: String,
        lines: List<String>,
        canRepick: Boolean,
        image: Bitmap?,
    ) {
        if (settings.autoCopy && text.isNotBlank()) {
            copyToClipboard(text)
            toast(getString(R.string.result_copied))
        }
        overlays.showResult(
            text = text,
            meta = buildMeta(outcome),
            notes = buildNotes(outcome),
            offlineAvailable = dispatcher.offlineAvailable(lastForceOffline),
            showLines = settings.showSegments,
            lines = lines,
            canRepick = canRepick,
            image = image,
            showImage = settings.showImage,
        )
        analyse(text)
    }

    /**
     * Maps the boxes an engine reported onto the screen.
     *
     * Only called for a whole-screen capture, so the crop the engine saw *is* the captured frame and
     * the only correction needed is undoing the scaling between the frame and the display.
     */
    private fun placeBlocks(outcome: OcrOutcome, shot: Bitmap): List<PendingBlock> {
        val metrics = realDisplayMetrics()
        val scaleX = metrics.widthPixels.toFloat() / shot.width.coerceAtLeast(1)
        val scaleY = metrics.heightPixels.toFloat() / shot.height.coerceAtLeast(1)
        val screen = Rect(0, 0, metrics.widthPixels, metrics.heightPixels)
        // A line of text is around 16 dp tall on a phone, so anything much under that is a fragment
        // rather than something worth offering. The picker itself inflates every box by a few dp when
        // hit-testing, which is what makes a box this small comfortable to tap.
        val minTapSize = (12 * resources.displayMetrics.density).roundToInt()

        return outcome.blocks.mapNotNull { block ->
            val rect = block.rect ?: return@mapNotNull null
            val onScreen = Rect(
                (rect.left * scaleX).roundToInt(),
                (rect.top * scaleY).roundToInt(),
                (rect.right * scaleX).roundToInt(),
                (rect.bottom * scaleY).roundToInt(),
            )
            // Boxes Lens pushed off the edge of the picture, or that are too small to hit with a
            // finger, are not offered as tap targets. Their text is still part of "tudo".
            if (!onScreen.intersect(screen)) return@mapNotNull null
            if (onScreen.width() < minTapSize || onScreen.height() < minTapSize) return@mapNotNull null
            PendingBlock(
                text = block.text,
                rect = onScreen,
                cropRect = rect,
                lines = block.lines,
            )
        }
    }

    private fun buildMeta(outcome: OcrOutcome): String {
        val language = outcome.language?.takeIf { it.isNotBlank() }
        return buildList {
            add(outcome.engineLabel)
            add(getString(R.string.result_meta_ms, outcome.elapsedMs))
            if (language != null) add(language)
        }.joinToString(" · ")
    }

    private fun buildNotes(outcome: OcrOutcome): List<String> = buildList {
        if (outcome.fallback) add(getString(R.string.result_note_fallback))
        outcome.warning?.takeIf { it.isNotBlank() }?.let { add(it) }
    }

    /**
     * Runs the dictionary lookup and the sentence translation for [text], one after the other in
     * whichever order they finish, and pushes each result into the panel as it lands.
     */
    private fun analyse(text: String) {
        analysisJob?.cancel()
        if (text.isBlank()) {
            lastAnalysedText = null
            return
        }
        lastAnalysedText = text

        val wantDictionary = settings.dictionaryLookup
        val wantTranslation = settings.translateSentence
        val target = settings.translateTarget

        overlays.showResultWords(if (wantDictionary) ResultOverlay.WordsState.Working else ResultOverlay.WordsState.Hidden)
        overlays.showResultTranslation(
            if (wantTranslation) ResultOverlay.TranslationState.Working else ResultOverlay.TranslationState.Hidden,
        )

        analysisJob = scope.launch {
            if (wantDictionary) {
                launch {
                    val state = runDictionary(text)
                    if (isActive && lastAnalysedText == text) overlays.showResultWords(state)
                }
            }
            if (wantTranslation) {
                launch {
                    val state = runTranslation(text, target)
                    if (isActive && lastAnalysedText == text) overlays.showResultTranslation(state)
                }
            }
        }
    }

    private suspend fun runDictionary(text: String): ResultOverlay.WordsState = try {
        val repository = DictionaryRepository.get(this)
        withContext(Dispatchers.Default) {
            val engine = repository.lookupEngine()
            val titles = repository.enabledTitles()
            if (engine == null) {
                ResultOverlay.WordsState.Done(emptyList(), emptyList(), 0, titles = titles)
            } else {
                // The filter is a dictionary title. One that no enabled dictionary answers to is a
                // leftover — the dictionary was disabled or removed — and honouring it would return
                // no words at all for every capture from now on, which looks like a broken
                // dictionary. Dropping it is what brings the panel back to «todos».
                val filter = repository.resolveFilter(settings.dictionaryFilter)
                if (filter == null && settings.dictionaryFilter.isNotBlank()) {
                    settings.dictionaryFilter = ""
                }
                val only = filter?.let { setOf(it) }.orEmpty()
                ResultOverlay.WordsState.Done(
                    matches = engine.lookup(text, only = only),
                    kanji = engine.kanjiBreakdown(text, only = only),
                    dictionaryCount = titles.size,
                    titles = titles,
                    selected = filter,
                )
            }
        }
    } catch (t: Throwable) {
        Log.e(TAG, "dictionary lookup failed", t)
        ResultOverlay.WordsState.Failed(t.message ?: getString(R.string.dictionary_failed))
    }

    private suspend fun runTranslation(text: String, target: String): ResultOverlay.TranslationState = try {
        val instance = translator ?: OfflineTranslator(this).also { translator = it }
        ResultOverlay.TranslationState.Done(instance.translate(text, target))
    } catch (t: Throwable) {
        Log.w(TAG, "offline translation failed", t)
        ResultOverlay.TranslationState.Failed(
            getString(R.string.translate_failed) + "\n" + (t.message ?: t.javaClass.simpleName),
        )
    }

    /**
     * Crops [source] to [region]. The region comes from the selector overlay, whose coordinates are
     * display pixels; the captured bitmap is usually the same size but not guaranteed to be, so the
     * rectangle is scaled rather than used verbatim.
     */
    private fun cropToRegion(source: Bitmap, region: Rect?): Bitmap {
        if (region == null || region.width() <= 1 || region.height() <= 1) return source

        val metrics = realDisplayMetrics()
        val scaleX = source.width.toFloat() / metrics.widthPixels.coerceAtLeast(1)
        val scaleY = source.height.toFloat() / metrics.heightPixels.coerceAtLeast(1)

        val left = (region.left * scaleX).roundToInt().coerceIn(0, source.width - 1)
        val top = (region.top * scaleY).roundToInt().coerceIn(0, source.height - 1)
        val right = (region.right * scaleX).roundToInt().coerceIn(left + 1, source.width)
        val bottom = (region.bottom * scaleY).roundToInt().coerceIn(top + 1, source.height)

        val cropped = Bitmap.createBitmap(source, left, top, right - left, bottom - top)
        if (cropped !== source) source.recycle()
        return cropped
    }

    private fun realDisplayMetrics(): DisplayMetrics {
        val metrics = DisplayMetrics()
        val display = getSystemService(DisplayManager::class.java)
            ?.getDisplay(Display.DEFAULT_DISPLAY)
        if (display != null) {
            @Suppress("DEPRECATION")
            display.getRealMetrics(metrics)
        }
        if (metrics.widthPixels <= 0 || metrics.heightPixels <= 0) {
            val fallback = resources.displayMetrics
            metrics.widthPixels = fallback.widthPixels
            metrics.heightPixels = fallback.heightPixels
        }
        return metrics
    }

    private fun showError(message: String) {
        overlays.showResultError(message, dispatcher.offlineAvailable(forceOffline = false))
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(ClipboardManager::class.java) ?: return
        runCatching { clipboard.setPrimaryClip(ClipData.newPlainText(LABEL_CLIP, text)) }
    }

    // --- notification --------------------------------------------------------------------------

    private fun startForegroundWithNotification() {
        ensureNotificationChannel()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        } else {
            0
        }
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), type)
        } catch (t: Throwable) {
            Log.e(TAG, "startForeground failed", t)
        }
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_desc)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, FloatingOcrService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_scan)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setContentIntent(open)
            .setOngoing(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, getString(R.string.notification_stop), stop)
            .build()
    }

    private fun toast(message: String) {
        runCatching { Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show() }
    }

    @Suppress("DEPRECATION")
    private fun Intent.projectionData(): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            getParcelableExtra(EXTRA_RESULT_DATA)
        }

    companion object {
        private const val TAG = "FloatingOcrService"
        private const val LABEL_CLIP = "yabai_ocr"

        private const val CHANNEL_ID = "yabai_ocr_capture"
        private const val NOTIFICATION_ID = 4201

        /** Pause between hiding our windows and taking the screenshot. */
        private const val CAPTURE_SETTLE_MS = 150L
        private const val CAPTURE_TIMEOUT_MS = 1500L

        const val ACTION_START = "dev.yabai.ocr.action.START"
        const val ACTION_STOP = "dev.yabai.ocr.action.STOP"
        const val EXTRA_RESULT_CODE = "dev.yabai.ocr.extra.RESULT_CODE"
        const val EXTRA_RESULT_DATA = "dev.yabai.ocr.extra.RESULT_DATA"

        /** Lets the settings screen tell whether the bubble is already up. */
        @Volatile
        var isRunning: Boolean = false
    }
}
