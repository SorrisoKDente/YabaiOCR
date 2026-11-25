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
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import dev.yabai.ocr.AppSettings
import dev.yabai.ocr.CameraActivity
import dev.yabai.ocr.MainActivity
import dev.yabai.ocr.R
import dev.yabai.ocr.UsageTracker
import dev.yabai.ocr.ai.AskAi
import dev.yabai.ocr.capture.CameraPhoto
import dev.yabai.ocr.capture.PhotoPageGeometry
import dev.yabai.ocr.capture.PhotoPageLayout
import dev.yabai.ocr.capture.ScreenCaptureSession
import dev.yabai.ocr.capture.ScreenMetrics
import dev.yabai.ocr.core.dict.LookupEngine
import dev.yabai.ocr.core.jp.JapaneseText
import dev.yabai.ocr.dict.DictionaryRepository
import dev.yabai.ocr.dict.KanjiDefault
import dev.yabai.ocr.dict.searchWords
import dev.yabai.ocr.ocr.OcrDispatcher
import dev.yabai.ocr.ocr.OcrOutcome
import dev.yabai.ocr.overlay.KanjiCard
import dev.yabai.ocr.overlay.KanjiCardText
import dev.yabai.ocr.overlay.KanjiPiece
import dev.yabai.ocr.overlay.OverlayController
import dev.yabai.ocr.overlay.PickerBlock
import dev.yabai.ocr.overlay.PickerDecision
import dev.yabai.ocr.overlay.PickerPiece
import dev.yabai.ocr.overlay.PickerReading
import dev.yabai.ocr.overlay.PickerReadingMode
import dev.yabai.ocr.overlay.PickerTargets
import dev.yabai.ocr.overlay.ResultOverlay
import dev.yabai.ocr.translate.OfflineTranslator
import dev.yabai.ocr.util.BlockImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
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

    /** The reading in flight: what «Cancelar» stops, and who owns the guard (R48m). */
    private var readJob: Job? = null

    /**
     * The dictionary search box (R31): the lookup in flight, whether the panel is in search mode,
     * and the query whose answer is on screen.
     */
    private var searchJob: Job? = null
    private var searchingDictionary = false
    private var searchQuery: String = ""

    /**
     * The text boxes of the last whole-screen capture.
     *
     * Kept after the picker closes so the result panel can offer "trechos" again without paying for
     * another Lens call — those are rate limited, and re-running OCR would also just reproduce the
     * same segmentation.
     */
    private var pendingBlocks: List<PendingBlock> = emptyList()

    /**
     * The same boxes, ready to draw: readings already placed when the setting asks for them.
     *
     * Computed once per capture, because the dictionary pass over a page — one lookup per column —
     * is the slow part; «trechos» from the panel reuses it.
     */
    private var pendingPicker: List<PickerBlock> = emptyList()
    private var pendingReadingMode: PickerReadingMode = PickerReadingMode.OFF

    /**
     * The picture the boxes were placed on: the capture, or a camera photo laid out on a screen-sized
     * page (R47a). Kept apart from [lastCrop] because the panel shows the photo, not the page.
     */
    private var pendingPage: Bitmap? = null

    /** The turn the picker's page takes into the window (R48g); 0 for a screen capture. */
    private var pendingPageTurn = 0

    /**
     * The turn this photo's page is laid out with, from the frame the camera took it in (R48g).
     *
     * Kept across a retry: the photo is the same picture, taken in the same frame.
     */
    private var photoTurn = 0

    /** The strips of the last capture, while the kanji reader is on screen (R17). */
    private var pendingKanji: List<KanjiPiece> = emptyList()

    private var lastOutcome: OcrOutcome? = null

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
     * The photo the last result came from, when it came from the camera (R46d).
     *
     * Kept because «tentar offline» has to run on this picture again: the screen the photo was taken
     * of is gone, and a capture would photograph whatever the camera app left behind.
     */
    private var lastPhoto: Bitmap? = null

    /**
     * One selectable piece of a whole-screen capture.
     *
     * [rect] is where the box goes on screen, which is what the picker draws and hit-tests; [cropRect]
     * is the same box in the pixels of the captured frame, which is what the panel shows as the image
     * beside the text. Both come from the engine's own box, so neither is guessed from the other.
     *
     * [pieces] are the columns or rows the block is made of, on screen, so a reading can be drawn
     * beside the characters it belongs to instead of beside the balloon as a whole.
     */
    private data class PendingBlock(
        val text: String,
        val rect: Rect,
        val cropRect: Rect,
        val lines: List<String>,
        val pieces: List<PendingPiece>,
    )

    /** A column or row of a [PendingBlock], on screen. */
    private data class PendingPiece(val text: String, val rect: Rect, val vertical: Boolean)

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        isPaused = false
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

            ACTION_PAUSE -> {
                pause()
                return START_NOT_STICKY
            }

            ACTION_RESUME -> {
                resume()
                return START_NOT_STICKY
            }

            ACTION_PHOTO -> {
                val path = intent.getStringExtra(EXTRA_PHOTO_PATH)
                // The doorway finishes before this arrives; without a path there is nothing to read.
                if (path != null) {
                    recognisePhoto(File(path), intent.getIntExtra(EXTRA_PHOTO_TURN, 0))
                }
                return START_NOT_STICKY
            }

            ACTION_CANCEL_READ -> {
                cancelReading()
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
        isPaused = false
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
        lastPhoto = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    // --- startup -------------------------------------------------------------------------------

    /**
     * Pauses the reader without ending it (R33c).
     *
     * The capture session and the foreground service stay alive — that is the difference from
     * «Parar» — but the bubble and every panel it owns go off screen, and the notification turns
     * into the way back. This is an action the notification itself offers; it is also what the
     * settings button does while the reader is paused.
     */
    private fun pause() {
        if (isPaused) return
        isPaused = true
        // A lookup that lands after the pause must not put a panel back on the screen.
        analysisJob?.cancel()
        analysisJob = null
        searchJob?.cancel()
        searchJob = null
        busy = false
        overlays.setPaused(true)
        startForegroundWithNotification()
    }

    /** Brings the bubble back where it was, without asking for the screen capture again. */
    private fun resume() {
        if (!isPaused) return
        isPaused = false
        overlays.setPaused(false)
        startForegroundWithNotification()
    }

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
        if (!requireLiveCapture()) return
        overlays.showRegionSelector()
    }

    /**
     * The camera (R46d): the capture session is not involved at all.
     *
     * A photo needs the camera's permission, not the screen's. [CameraActivity] asks for it, shoots
     * in place and hands over one file; the service only reads that file and says when the reading is
     * on screen, which is when that screen closes itself (R48a).
     */
    override fun onCameraRequested() {
        if (busy) return
        val intent = Intent(this, CameraActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { startActivity(intent) }.isFailure) {
            toast(getString(R.string.capture_failed))
        }
    }

    override fun onRegionConfirmed(rect: Rect?) = requestCapture(rect)

    override fun onRegionCancelled() = Unit

    /** The panel's «Cancelar» while a reading runs (R48m). */
    override fun onRecognitionCancelRequested() = cancelReading()

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
            canRetakePhoto = lastPhoto != null,
        )
    }

    override fun onPickAllRequested() {
        val outcome = lastOutcome ?: return
        showResultFor(
            outcome,
            outcome.text,
            outcome.lines,
            canRepick = pendingBlocks.isNotEmpty(),
            image = lastCrop,
            canRetakePhoto = lastPhoto != null,
        )
    }

    override fun onRepickRequested() {
        val outcome = lastOutcome ?: return
        if (!overlays.showTextPicker(
                pendingPicker,
                pendingReadingMode,
                pendingPage ?: lastCrop,
                retake = lastPhoto != null,
                // A screen capture has no turn; the page falls back to it only when the photo's page
                // could not be allocated.
                turn = if (pendingPage != null) pendingPageTurn else 0,
            )
        ) {
            // The boxes are gone (the screen changed, or the service was restarted): fall back to the
            // whole capture, which is still in memory.
            showResultFor(
                outcome,
                outcome.text,
                outcome.lines,
                canRepick = false,
                image = lastCrop,
                canRetakePhoto = lastPhoto != null,
            )
        }
    }

    override fun onRetryOfflineRequested() {
        // A photo is retried on itself: the screen it was taken of is gone, and a capture would
        // photograph whatever the camera app left behind.
        val photo = lastPhoto
        if (photo != null) retryPhoto(photo) else requestCapture(lastRegion, forceOffline = true)
    }

    /** «Ler kanji»: the capture is a page to point at, not a text to copy (R17). */
    override fun onKanjiRequested() = requestCapture(null, kanji = true)

    /**
     * A finger landed on a character of the frozen page.
     *
     * The card is built off the main thread: opening the dictionary is file work, and a lookup is
     * a pass over an in-memory index. Saying «not a kanji» is the one case answered at once — there
     * is nothing to look up, and staying silent would read as a broken tap.
     */
    override fun onKanjiTapped(piece: Int, index: Int) {
        val target = pendingKanji.getOrNull(piece) ?: return
        val character = target.text.getOrNull(index) ?: return
        if (!JapaneseText.isKanji(character.code)) {
            toast(getString(R.string.kanji_not_a_kanji))
            return
        }
        scope.launch {
            val card = withContext(Dispatchers.Default) { kanjiCard(target, index, character) }
            overlays.showKanjiCard(card)
        }
    }

    override fun onResultDismissed() {
        analysisJob?.cancel()
        analysisJob = null
        searchJob?.cancel()
        searchJob = null
        searchingDictionary = false
        searchQuery = ""
        // The panel is gone, and with it the only reason to hold a full screen of pixels — or a photo,
        // which is the same size and would otherwise outlive the panel it was read for.
        lastCrop = null
        lastPhoto = null
        pendingPage = null
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
        if (searchingDictionary) {
            // In the search box the filter changes the answer to the query, not to a capture: no
            // translation to blink, no sentence to re-read.
            searchDictionary(searchQuery)
            return
        }
        val text = lastAnalysedText ?: return
        analyse(text)
    }

    /**
     * The bubble's «Dicionário»: the panel opens as a search box (R31).
     *
     * The dictionary filter is the same one the panel uses for a capture — «todos» or one title —
     * because it is the same question: which installed dictionary should answer. Reading it here also
     * drops a stored title whose dictionary is gone, exactly as a capture does.
     */
    override fun onDictionarySearchRequested() {
        val repository = DictionaryRepository.get(this)
        val titles = repository.enabledTitles()
        val filter = repository.resolveFilter(settings.dictionaryFilter)
        if (filter == null && settings.dictionaryFilter.isNotBlank()) settings.dictionaryFilter = ""
        val stale = repository.installed()
            .filter { it.enabled && it.info.outdated }
            .map { it.displayName }

        searchingDictionary = true
        searchQuery = ""
        searchJob?.cancel()
        val initialQuery = if (settings.dictionaryKeepLastSearch) settings.dictionaryLastQuery else ""
        overlays.showDictionarySearch(
            titles = titles,
            selected = filter,
            dictionaryCount = titles.size,
            stale = stale,
            initialQuery = initialQuery,
            canAskAi = settings.showAiButton,
            searchByTranslation = settings.searchByTranslation,
        )
    }

    override fun onDictionarySearchChanged(text: String) {
        if (!searchingDictionary) return
        searchDictionary(text)
    }

    override fun onDictionarySearchModeChanged(searchByTranslation: Boolean) {
        settings.searchByTranslation = searchByTranslation
        if (searchingDictionary) searchDictionary(searchQuery)
    }

    /** Looks [query] up off the main thread; a newer keystroke cancels the answer in flight. */
    private fun searchDictionary(query: String) {
        searchQuery = query
        searchJob?.cancel()
        // The first meaning query on a dictionary builds its sidecar; say so instead of leaving the
        // panel on a bare «Procurando…» for the several seconds that costs (R35f).
        if (settings.searchByTranslation && query.isNotBlank()) {
            overlays.showResultWords(ResultOverlay.WordsState.Working(preparing = true))
        }
        searchJob = scope.launch {
            val state = runSearch(query)
            if (settings.dictionaryKeepLastSearch && query.isNotBlank()) {
                settings.dictionaryLastQuery = query
            }
            if (isActive && searchQuery == query) overlays.showResultWords(state)
        }
    }

    override fun onAskAiRequested(text: String) {
        askAi(text)
    }

    private fun clearPending() {
        pendingBlocks = emptyList()
        pendingPicker = emptyList()
        pendingReadingMode = PickerReadingMode.OFF
        pendingPage = null
        pendingPageTurn = 0
        pendingKanji = emptyList()
    }

    // --- capture pipeline ----------------------------------------------------------------------

    private fun requestCapture(
        region: Rect?,
        forceOffline: Boolean = false,
        kanji: Boolean = false,
    ) {
        if (busy) return
        // Paused means paused: a tap that was already in flight must not capture and reopen panels.
        if (isPaused) return
        if (!requireLiveCapture()) return
        // A capture replaces the search box, if that is what was open: the panel is about to show a
        // recognised text, and a later dictionary pick must re-analyse it, not re-run a query.
        searchingDictionary = false
        searchQuery = ""
        searchJob?.cancel()
        searchJob = null
        val session = capture
        if (session == null) {
            toast(getString(R.string.capture_failed))
            return
        }

        busy = true
        lastRegion = region
        // A new capture supersedes the boxes of the previous one, and the photo with them.
        clearPending()
        lastPhoto = null

        startReading {
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
                    return@startReading
                }

                val crop = withContext(Dispatchers.Default) { cropToRegion(shot, region) }
                // Kept for the panel's image, and replaced — dropping the reference to the previous
                // one — on every capture.
                lastCrop = crop

                overlays.showResultLoading()
                val outcome = dispatcher.recognize(crop, forceOffline)
                // «Cancelar» can land while an engine that cannot be interrupted is still finishing.
                ensureActive()
                lastOutcome = outcome

                // «Ler kanji»: the same capture, but the boxes are strips to point at instead of
                // paragraphs to copy, and no panel is opened. A page with nothing recognised falls
                // through to the panel, which at least says what was seen.
                if (kanji) {
                    val strips = kanjiPieces(outcome, shot)
                    if (strips.isNotEmpty() && overlays.showKanji(strips, shot)) {
                        pendingKanji = strips
                        overlays.hideResult()
                        return@startReading
                    }
                }

                // A whole-screen capture gets the Lens treatment: the recognised paragraphs are drawn
                // as boxes over the live screen and the user picks one — even when the engine found
                // a single one, because the page is tapped and not solved for the user. A region
                // capture already is a selection and goes straight to the panel.
                if (PickerDecision.offersPicker(region == null, settings.pickText, outcome.blocks.size)) {
                    pendingBlocks = placeBlocks(outcome, shot)
                    if (pendingBlocks.isNotEmpty()) {
                        val mode = settings.pickerReading
                        val picker = pickerBlocks(pendingBlocks, mode)
                        if (overlays.showTextPicker(picker, mode, shot)) {
                            pendingPicker = picker
                            pendingReadingMode = mode
                            pendingPage = shot
                            overlays.hideResult()
                            return@startReading
                        }
                    }
                    clearPending()
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
            }
        }
    }

    /**
     * Reads a photo taken with the camera (R46d) through the same panel as a screen capture.
     *
     * A photo has no screen behind it, so its page is the picture fitted to the screen (R47a). The
     * camera screen is still showing that same picture while this runs, which is why the panel's own
     * spinner stays out of the way (R48a). The file the camera wrote is gone by the time this
     * returns — [CameraPhoto] deletes it while decoding — and the camera screen is told to go once
     * the reading is on screen.
     */
    private fun recognisePhoto(file: File, turn: Int = 0) {
        if (!beginPhoto()) {
            file.delete()
            photoShown()
            return
        }
        // After [beginPhoto], which clears what the previous capture left: the page of this photo is
        // laid out in the frame the phone was held in when it was taken (R48g).
        photoTurn = turn
        startReading {
            try {
                val photo = withContext(Dispatchers.Default) { CameraPhoto.loadUpright(file) }
                if (photo == null) {
                    showError(getString(R.string.capture_failed))
                    return@startReading
                }
                // The camera screen is the loading state of a photo (R48a): it already shows the very
                // picture the boxes land on, and a second spinner at the top of the screen would
                // cover it.
                recogniseImage(photo, loadingPanel = false)
            } catch (t: Throwable) {
                Log.e(TAG, "photo pipeline failed", t)
                showError(t.message ?: getString(R.string.result_failed))
            } finally {
                // A cancelled reading has nothing to show, and the notice would close the camera
                // screen the user just sent back to its preview (R48m).
                if (isActive) photoShown()
            }
        }
    }

    /**
     * Tells the camera screen that the photo's reading is on screen, so it can step out of the way.
     *
     * The picker draws that same photo as its page, so the screen behind it is already invisible when
     * this arrives; closing the camera before the boxes are up would leave a hole where it was, and
     * closing it never would leave a dead screen behind a panel (R48a).
     */
    private fun photoShown() {
        sendBroadcast(Intent(ACTION_PHOTO_READY).setPackage(packageName))
    }

    /** The same photo again, offline: the retry of a result that came from the camera. */
    private fun retryPhoto(photo: Bitmap) {
        if (!beginPhoto()) return
        startReading {
            try {
                recogniseImage(photo, forceOffline = true)
            } catch (t: Throwable) {
                Log.e(TAG, "photo retry failed", t)
                showError(t.message ?: getString(R.string.result_failed))
            }
        }
    }

    /**
     * The state a photo starts from: no search box, no boxes of a previous capture — and no region,
     * because a photo has no selection to keep for a later retry.
     */
    private fun beginPhoto(): Boolean {
        if (busy || isPaused) return false
        searchingDictionary = false
        searchQuery = ""
        searchJob?.cancel()
        searchJob = null
        lastRegion = null
        clearPending()
        busy = true
        return true
    }

    /**
     * Runs one reading and keeps the handle «Cancelar» stops (R48m).
     *
     * Only the reading that is still the current one gives the guard back: a cancelled reading can
     * finish after the next capture has started, and it must not free that one's `busy`.
     */
    private fun startReading(block: suspend CoroutineScope.() -> Unit) {
        readJob = scope.launch {
            try {
                block()
            } finally {
                if (readJob === coroutineContext[Job]) {
                    readJob = null
                    busy = false
                }
            }
        }
    }

    /**
     * Stops the reading that is running (R48m): the panel's «Cancelar» and the camera's ask the same
     * thing and get the same answer — the work in flight is dropped, and its pixels with it.
     */
    private fun cancelReading() {
        // Nothing in flight: a late cancel must not take down a panel that is already the answer.
        val job = readJob ?: return
        readJob = null
        job.cancel()
        busy = false
        lastCrop = null
        lastPhoto = null
        clearPending()
        overlays.hideResult()
    }

    private suspend fun recogniseImage(
        photo: Bitmap,
        forceOffline: Boolean = false,
        /** Whether the panel shows the spinner; the camera screen is the spinner for its own photo. */
        loadingPanel: Boolean = true,
    ) {
        lastCrop = photo
        lastPhoto = photo
        if (loadingPanel) overlays.showResultLoading()
        val outcome = dispatcher.recognize(photo, forceOffline)
        // The same, for a photo: no boxes from a reading that was cancelled (R48m).
        currentCoroutineContext().ensureActive()
        lastOutcome = outcome

        // R47a: a photo is read like a whole-screen capture — the paragraphs become boxes the user
        // taps — except that the page under them is the photo, fitted to the screen. The capture
        // path cannot be reused as is: its boxes are placed with the screen's metrics, and the
        // photo has its own size and aspect ratio.
        if (PickerDecision.offersPicker(true, settings.pickText, outcome.blocks.size)) {
            val page = withContext(Dispatchers.Default) { photoPage(photo) }
            if (page != null) {
                pendingBlocks = placePhotoBlocks(outcome, page)
                if (pendingBlocks.isNotEmpty()) {
                    val mode = settings.pickerReading
                    val picker = pickerBlocks(pendingBlocks, mode)
                    if (overlays.showTextPicker(
                            picker,
                            mode,
                            page.bitmap,
                            retake = true,
                            turn = photoTurn,
                        )
                    ) {
                        pendingPicker = picker
                        pendingReadingMode = mode
                        pendingPage = page.bitmap
                        pendingPageTurn = photoTurn
                        overlays.hideResult()
                        return
                    }
                }
            }
            clearPending()
        }

        showResultFor(
            outcome,
            outcome.text,
            outcome.lines,
            canRepick = false,
            image = photo,
            canRetakePhoto = true,
        )
    }

    /** A camera photo laid out on a screen-sized page, with the geometry the boxes use. */
    private data class PhotoPage(val bitmap: Bitmap, val layout: PhotoPageLayout)

    /**
     * The photo on a black page the size of the screen.
     *
     * The fit and the drawing live in [PhotoPageGeometry], where they can be measured without a
     * screen. Null when the page cannot be allocated; the panel is still shown without it.
     */
    private fun photoPage(photo: Bitmap): PhotoPage? {
        val metrics = realDisplayMetrics()
        // The page is the phone's frame, not the window's (R48g): a portrait window with the phone on
        // its side would fit a landscape photo to the window's long side and leave a strip in the
        // middle of it, with the text a third of the size the screen can give.
        val frame = PhotoPageGeometry.frame(
            screenWidth = metrics.widthPixels.coerceAtLeast(1),
            screenHeight = metrics.heightPixels.coerceAtLeast(1),
            turn = photoTurn,
        )
        val layout = PhotoPageGeometry.fit(
            photoWidth = photo.width,
            photoHeight = photo.height,
            pageWidth = frame.width,
            pageHeight = frame.height,
        ) ?: return null
        val page = PhotoPageGeometry.page(photo, layout) ?: return null
        return PhotoPage(page, layout)
    }

    /**
     * The photo's boxes on the page, keeping the picture coordinates for the panel's crop.
     *
     * Unlike [placeBlocks], there is no display scale to undo here: the engine read the photo
     * itself, so its boxes are already in the photo's pixels. Only the page's fit moves them.
     */
    private fun placePhotoBlocks(outcome: OcrOutcome, page: PhotoPage): List<PendingBlock> {
        val pageBounds = page.layout.photoBounds
        val minTapSize = (12 * resources.displayMetrics.density).roundToInt()

        return outcome.blocks.mapNotNull { block ->
            val rect = block.rect ?: return@mapNotNull null
            val onPage = PhotoPageGeometry.place(page.layout, rect)
            if (!onPage.intersect(pageBounds)) return@mapNotNull null
            if (!PickerTargets.offered(onPage.width(), onPage.height(), minTapSize)) return@mapNotNull null
            PendingBlock(
                text = block.text,
                rect = onPage,
                cropRect = rect,
                lines = block.lines,
                pieces = block.pieces.mapNotNull { piece ->
                    val box = piece.rect ?: return@mapNotNull null
                    val placed = PhotoPageGeometry.place(page.layout, box)
                    if (placed.width() <= 0 || placed.height() <= 0) return@mapNotNull null
                    PendingPiece(piece.text, placed, placed.height() >= placed.width())
                },
            )
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
        canRetakePhoto: Boolean = false,
    ) {
        // The pause can arrive while Lens is working. When it does, the capture is dropped whole:
        // no panel, no clipboard write and no analysis behind a bubble that is no longer there.
        if (isPaused) return
        if (settings.autoCopy && text.isNotBlank()) {
            copyToClipboard(text)
            toast(getString(R.string.result_copied))
        }
        overlays.showResult(
            text = text,
            meta = buildMeta(outcome),
            notes = buildNotes(outcome),
            showLines = settings.showSegments,
            lines = lines,
            canRepick = canRepick,
            image = image,
            showImage = settings.showImage,
            canAskAi = settings.showAiButton,
            canRetakePhoto = canRetakePhoto,
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
        // A finger needs room, but a column of vertical text is thin by nature: see PickerTargets for
        // why the test is on the longest side and not on both.
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
            if (!PickerTargets.offered(onScreen.width(), onScreen.height(), minTapSize)) return@mapNotNull null
            PendingBlock(
                text = block.text,
                rect = onScreen,
                cropRect = rect,
                lines = block.lines,
                pieces = block.pieces.mapNotNull { piece ->
                    val box = piece.rect ?: return@mapNotNull null
                    val placed = Rect(
                        (box.left * scaleX).roundToInt(),
                        (box.top * scaleY).roundToInt(),
                        (box.right * scaleX).roundToInt(),
                        (box.bottom * scaleY).roundToInt(),
                    )
                    if (placed.width() <= 0 || placed.height() <= 0) return@mapNotNull null
                    PendingPiece(piece.text, placed, placed.height() >= placed.width())
                },
            )
        }
    }

    /**
     * The strips a finger can point at in the kanji reader (R17).
     *
     * Same scaling as [placeBlocks] and one important difference: **no minimum tap size**. The text
     * picker refuses a box a finger cannot hit, because picking it would be a lottery; here the
     * smallest strip — a single column, sometimes a single character — is exactly what the user is
     * aiming at, and the cells inside it are what the tap is measured against.
     */
    private fun kanjiPieces(outcome: OcrOutcome, shot: Bitmap): List<KanjiPiece> {
        val metrics = realDisplayMetrics()
        val scaleX = metrics.widthPixels.toFloat() / shot.width.coerceAtLeast(1)
        val scaleY = metrics.heightPixels.toFloat() / shot.height.coerceAtLeast(1)

        fun place(rect: Rect): Rect = Rect(
            (rect.left * scaleX).roundToInt(),
            (rect.top * scaleY).roundToInt(),
            (rect.right * scaleX).roundToInt(),
            (rect.bottom * scaleY).roundToInt(),
        )

        return outcome.blocks.flatMap { block ->
            val strips = block.pieces.mapNotNull { piece ->
                val box = piece.rect ?: return@mapNotNull null
                if (piece.text.isBlank()) return@mapNotNull null
                val placed = place(box)
                if (placed.width() <= 0 || placed.height() <= 0) return@mapNotNull null
                KanjiPiece(piece.text, placed, placed.height() >= placed.width())
            }
            if (strips.isNotEmpty()) {
                strips
            } else {
                val box = block.rect ?: return@flatMap emptyList()
                val placed = place(box)
                if (placed.width() <= 0 || placed.height() <= 0 || block.text.isBlank()) {
                    emptyList()
                } else {
                    listOf(KanjiPiece(block.text, placed, placed.height() >= placed.width()))
                }
            }
        }
    }

    /**
     * What the card says about the kanji under the finger.
     *
     * Three answers in one card, because they answer different questions: the **reading in this
     * sentence** comes from the word the character is part of (腹 is はら in 腹が減っては), the
     * **meaning of that word** comes from the word dictionary (剣技 is fencing, not just 技), and the
     * **dictionary readings** come from the kanji entry itself (音 フク, 訓 はら). A single kanji
     * looked up cold can only give the third, and a word cannot give the third for a character it
     * does not cover — which is why they are worth the space together.
     */
    private fun kanjiCard(target: KanjiPiece, index: Int, character: Char): KanjiCard {
        val symbol = character.toString()
        val repository = DictionaryRepository.get(this)

        // Why the kanji section is empty is a question only the installed list can answer (R28):
        // nothing installed, installed but switched off, or installed and enabled yet the character is
        // not in it. Computed before the lookup because both ways out of this function need it — and
        // because a word-only narrowing can no longer be the reason, since the core widens it.
        val installed = repository.installed()
        val note = getString(
            KanjiCardText.missingNote(
                installed = installed.any { it.info.kanjiCount > 0 },
                enabled = installed.any { it.enabled && it.info.kanjiCount > 0 },
            ),
        )

        val engine = try {
            repository.lookupEngine()
        } catch (t: Throwable) {
            Log.w(TAG, "kanji lookup failed", t)
            null
        }
        if (engine == null) {
            return KanjiCard(
                character = symbol,
                reading = null,
                word = null,
                onYomi = "",
                kunYomi = "",
                meaning = "",
                note = note,
            )
        }

        val only = repository.resolveFilter(settings.dictionaryFilter)?.let { setOf(it) }.orEmpty()
        // R30: the card's own choice answers the kanji questions; the words of the sentence keep
        // following the panel's filter. Choosing KANJIDIC for the card must not make the word
        // context disappear — KANJIDIC has no word entries at all.
        val kanjiOnly = KanjiDefault.titleOf(repository.installed(), settings.kanjiDictionary)
            ?.let { setOf(it) }
            .orEmpty()
        val words = try {
            engine.lookup(target.text, only = only)
        } catch (t: Throwable) {
            Log.w(TAG, "kanji context lookup failed", t)
            emptyList()
        }
        val word = KanjiCardText.wordAt(words, index)
        val entry = try {
            engine.kanjiBreakdown(symbol, only = kanjiOnly).firstOrNull()
        } catch (t: Throwable) {
            Log.w(TAG, "kanji entry lookup failed", t)
            null
        }
        // R29: where the word's reading breaks per kanji. Null (no line) when the dictionaries cannot
        // justify a division — 今日 きょう is not 今 きょ + 日 う, and the card must not pretend it is.
        val parts = try {
            word?.let { KanjiCardText.parts(engine.wordReadings(it.surface, it.reading, only = kanjiOnly)) }
        } catch (t: Throwable) {
            Log.w(TAG, "kanji reading split failed", t)
            null
        }

        return KanjiCard(
            character = symbol,
            reading = word?.reading,
            word = word?.surface?.takeIf { it != symbol },
            onYomi = KanjiCardText.onYomi(entry),
            kunYomi = KanjiCardText.kunYomi(entry),
            meaning = KanjiCardText.meaning(entry),
            // R34a: the meaning belongs to the compound the card names. Asked only for a word worth
            // naming: a single kanji already has the kanji entry's meaning right below.
            wordMeaning = if (word != null && word.surface != symbol) {
                KanjiCardText.wordMeaning(words, index)
            } else {
                ""
            },
            source = entry?.dictionaryTitle?.takeIf { it.isNotBlank() },
            parts = parts.orEmpty(),
            span = KanjiCardText.kanjiSpan(target.text, words, index),
            note = if (entry == null) note else null,
        )
    }

    /**
     * Turns the boxes into what the picker draws, adding the readings when [mode] asks for them.
     *
     * The readings come from the same engine the panel uses, one lookup per column: a column is a few
     * characters and the dictionary is an index in memory, so a whole page costs a pass and not a
     * request. When the dictionary cannot be opened the boxes still appear — the picker is the only
     * way to choose a piece, and losing it because a reading failed would be the wrong trade.
     */
    private suspend fun pickerBlocks(
        blocks: List<PendingBlock>,
        mode: PickerReadingMode,
    ): List<PickerBlock> {
        val plain = blocks.map { PickerBlock(it.rect) }
        if (mode == PickerReadingMode.OFF) return plain
        return withContext(Dispatchers.Default) {
            try {
                val repository = DictionaryRepository.get(this@FloatingOcrService)
                val engine = repository.lookupEngine() ?: return@withContext plain
                val only = repository.resolveFilter(settings.dictionaryFilter)
                    ?.let { setOf(it) }
                    .orEmpty()
                blocks.map { block ->
                    PickerBlock(
                        rect = block.rect,
                        label = if (mode == PickerReadingMode.LABEL) {
                            engine.withFurigana(
                                block.text,
                                engine.lookup(block.text, only = only),
                                only,
                            )?.text
                        } else {
                            null
                        },
                        pieces = if (mode == PickerReadingMode.RUBY) {
                            block.pieces.mapNotNull { piece -> rubyPiece(piece, engine, only) }
                        } else {
                            emptyList()
                        },
                    )
                }
            } catch (t: Throwable) {
                Log.w(TAG, "readings for the picker failed", t)
                plain
            }
        }
    }

    /** The readings of one column or row, placed as fractions of its length. */
    private fun rubyPiece(
        piece: PendingPiece,
        engine: LookupEngine,
        only: Set<String>,
    ): PickerPiece? {
        val length = piece.text.length
        if (length == 0) return null
        val readings = engine.readings(piece.text, engine.lookup(piece.text, only = only), only)
        if (readings.isEmpty()) return null
        return PickerPiece(
            rect = piece.rect,
            vertical = piece.vertical,
            readings = readings.map {
                PickerReading(
                    text = it.text,
                    from = it.start.toFloat() / length,
                    to = it.end.toFloat() / length,
                )
            },
        )
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

        // The lookup is not optional (R46a): what the text says about the words is always part of
        // the result, and a value stored by the old switch no longer hides it.
        val wantTranslation = settings.translateSentence
        val target = settings.translateTarget

        overlays.showResultWords(ResultOverlay.WordsState.Working())
        overlays.showResultTranslation(
            if (wantTranslation) ResultOverlay.TranslationState.Working else ResultOverlay.TranslationState.Hidden,
        )

        analysisJob = scope.launch {
            launch {
                val state = runDictionary(text)
                if (isActive && lastAnalysedText == text) overlays.showResultWords(state)
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
            // Named here, once: the dictionary screen says the same names (R22), so the note points
            // at the exact card to delete (R26).
            val stale = repository.installed()
                .filter { it.enabled && it.info.outdated }
                .map { it.displayName }
            if (engine == null) {
                ResultOverlay.WordsState.Done(emptyList(), emptyList(), 0, titles = titles, stale = stale)
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
                val matches = engine.lookup(text, only = only)
                ResultOverlay.WordsState.Done(
                    matches = matches,
                    kanji = engine.kanjiBreakdown(text, only = only),
                    dictionaryCount = titles.size,
                    titles = titles,
                    selected = filter,
                    stale = stale,
                    // Written from the same segmentation the word list came from: the reading of a
                    // word is part of the hit, so this costs a pass over the text, not a lookup.
                    furigana = if (settings.showFurigana) engine.withFurigana(text, matches, only) else null,
                )
            }
        }
    } catch (t: Throwable) {
        Log.e(TAG, "dictionary lookup failed", t)
        ResultOverlay.WordsState.Failed(t.message ?: getString(R.string.dictionary_failed))
    }

    /**
     * Answers a query typed in the search box (R31).
     *
     * It is deliberately **not** the capture path: no translation, no furigana over a sentence and
     * no «Consultando…» between keystrokes. The whole word is tried first (see [LookupEngine.search]),
     * and the suggestions are the query's neighbours in the same dictionaries the answer came from.
     */
    private suspend fun runSearch(query: String): ResultOverlay.WordsState =
        searchWords(this, query)

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

    /**
     * Whether the system still shows the «OCR ativo» notification.
     *
     * From Android 13 the user can swipe a foreground-service notification away, and the service —
     * with its media projection — carries on just well enough that the next capture times out and
     * the panel says «falha ao capturar» (R24). The notification is the only sign on screen that the
     * capture is live, so a missing notification means the capture is over.
     *
     * Notifications turned off for the app are **not** a dismissal: that is a permission the user
     * refused, the service still has its capture, and stopping there would break the app for
     * someone who simply does not want the shade filled.
     */
    private fun captureAdvertised(): Boolean {
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return true
        val manager = getSystemService(NotificationManager::class.java) ?: return true
        return try {
            manager.activeNotifications.any { it.id == NOTIFICATION_ID }
        } catch (t: Throwable) {
            // An OEM that refuses the query is not evidence that the notification is gone.
            true
        }
    }

    /**
     * A capture the user can still use, or an honest stop (R24).
     *
     * Answering a tap with «falha ao capturar» while the bubble stays on screen is the worst of
     * both: the user is told something went wrong and given no way to fix it. Once the capture is
     * gone, the bubble has nothing left to offer, so it goes with it and the toast says how to come
     * back.
     */
    private fun requireLiveCapture(): Boolean {
        if (capture != null && captureAdvertised()) return true
        toast(getString(R.string.capture_dismissed))
        stopSelf()
        return false
    }

    /**
     * The whole display: the rectangle the camera laid the preview and the frozen photo out in,
     * which the page is stretched into (R48j).
     */
    private fun realDisplayMetrics(): DisplayMetrics = ScreenMetrics.real(this)

    private fun showError(message: String) {
        overlays.showResultError(message, dispatcher.offlineAvailable(forceOffline = false))
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(ClipboardManager::class.java) ?: return
        runCatching { clipboard.setPrimaryClip(ClipData.newPlainText(LABEL_CLIP, text)) }
    }

    /**
     * Copies the recognised text and opens an AI app with the question already written in it.
     *
     * The copy is not a side effect of the share, it is half of what the button promises: the text it
     * hands over is the *recognised* text, prompt and all, while the clipboard keeps the text alone —
     * so closing the other app still leaves something to paste somewhere else.
     *
     * With an app fixed in the settings that app is opened directly. Without one the panel lists the
     * apps that take text and the user picks there: the system chooser was the one route that opened
     * nothing on the phone this was tested on — it closed and left the screen as it was — so the
     * choice is a view this app owns, and the tap takes the same `startActivity` a fixed app takes
     * (R3).
     */
    private fun askAi(text: String) {
        copyToClipboard(text)
        when (val plan = AskAi.plan(settings.aiPackage, AskAi.targets(this))) {
            is AskAi.Plan.Direct -> openAi(plan.packageName, text)

            is AskAi.Plan.Choose -> {
                // A remembered name that is not installed has just lost its app: the list takes its
                // place, and keeping the name would only cost the same fallback on the next tap.
                if (settings.aiPackage.isNotBlank()) settings.aiPackage = ""
                overlays.showAskAiApps(plan.apps, text)
            }

            AskAi.Plan.Nothing -> overlays.showResultNote(getString(R.string.result_ai_no_app))
        }
    }

    /**
     * The app the user picked from the list: same call as a fixed app, and the same ending. The list
     * closes itself on the choice (see [OverlayController.showAskAiApps]); `openAi` closes what is
     * left of the panel once an app has actually opened.
     */
    override fun onAskAiToRequested(packageName: String, text: String) = openAi(packageName, text)

    /**
     * Opens [packageName] with the question in it and takes the panel down.
     *
     * Taking it down is part of asking: the panel is a floating window, so left on screen it would sit
     * over the app that is about to answer. It is taken down **only** when an app actually opened —
     * after a failure the panel is the only thing that can say so and still holds the list.
     *
     * When the app does not open — uninstalled between the list being drawn and the tap — the name is
     * forgotten and the list is offered again, so the tap still ends somewhere.
     */
    private fun openAi(packageName: String, text: String) {
        val payload = AskAi.request(settings.aiPrompt, text)
        if (payload.isBlank()) return
        copyToClipboard(text)

        val opened = AskAi.attempt(
            candidates = listOf(AskAi.directIntent(payload, packageName)),
            start = { runCatching { startActivity(it) }.isSuccess },
            onOpened = { overlays.hideResult() },
        )
        if (opened) return

        settings.aiPackage = ""
        val apps = AskAi.targets(this)
        if (apps.isEmpty()) overlays.showResultNote(getString(R.string.result_ai_no_app))
        else overlays.showAskAiApps(apps, text)
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

    internal fun buildNotification(): Notification {
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
        // Pause and resume are one action whose wording and target depend on the state (R33c).
        val paused = isPaused
        val toggle = PendingIntent.getService(
            this,
            2,
            Intent(this, FloatingOcrService::class.java)
                .setAction(if (paused) ACTION_RESUME else ACTION_PAUSE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            // A status bar icon is drawn from its alpha only, so this is the monochrome variant; the
            // bubble keeps the plain picture (`ic_scan`), which would be a white square here.
            .setSmallIcon(R.drawable.ic_stat_scan)
            .setContentTitle(
                getString(if (paused) R.string.notification_title_paused else R.string.notification_title),
            )
            .setContentText(
                getString(if (paused) R.string.notification_text_paused else R.string.notification_text),
            )
            .setContentIntent(open)
            .setOngoing(true)
            // Swiping it away is «stop», the same as its own «Parar» action (R24). Android 13 lets a
            // foreground-service notification be dismissed, and this is the one place that says so.
            .setDeleteIntent(stop)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(
                0,
                getString(if (paused) R.string.notification_resume else R.string.notification_pause),
                toggle,
            )
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
        const val ACTION_PAUSE = "dev.yabai.ocr.action.PAUSE"
        const val ACTION_RESUME = "dev.yabai.ocr.action.RESUME"

        /** The reading of a photo is on screen; [CameraActivity] closes itself (R48a). */
        const val ACTION_PHOTO_READY = "dev.yabai.ocr.action.PHOTO_READY"

        /** A photo from [CameraActivity], in [EXTRA_PHOTO_PATH] (R46d). */
        const val ACTION_PHOTO = "dev.yabai.ocr.action.PHOTO"

        /** That photo's reading is no longer wanted: [CameraActivity] abandoned it (R48m). */
        const val ACTION_CANCEL_READ = "dev.yabai.ocr.action.CANCEL_READ"
        const val EXTRA_RESULT_CODE = "dev.yabai.ocr.extra.RESULT_CODE"
        const val EXTRA_RESULT_DATA = "dev.yabai.ocr.extra.RESULT_DATA"
        const val EXTRA_PHOTO_PATH = "dev.yabai.ocr.extra.PHOTO_PATH"

        /** The turn the picker's page takes into the window: the phone's frame (R48g). */
        const val EXTRA_PHOTO_TURN = "dev.yabai.ocr.extra.PHOTO_TURN"

        /** Lets the settings screen tell whether the bubble is already up. */
        @Volatile
        var isRunning: Boolean = false

        /**
         * Whether the reader is up but paused (R33c): the service is alive and the capture session is
         * still valid, so the settings button must resume instead of asking for the screen again.
         */
        @Volatile
        var isPaused: Boolean = false
    }
}
