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
import dev.yabai.ocr.capture.ScreenCaptureSession
import dev.yabai.ocr.ocr.OcrDispatcher
import dev.yabai.ocr.overlay.OverlayController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
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

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        settings = AppSettings(this)
        dispatcher = OcrDispatcher(settings)
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
        scope.cancel()
        handler.removeCallbacksAndMessages(null)
        overlays.destroy()
        teardownCapture()
        dispatcher.close()
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

    override fun onRetryOfflineRequested() = requestCapture(lastRegion, forceOffline = true)

    override fun onResultDismissed() = Unit

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

                overlays.showResultLoading()
                val outcome = dispatcher.recognize(crop, forceOffline)

                if (settings.autoCopy && outcome.text.isNotBlank()) {
                    copyToClipboard(outcome.text)
                    toast(getString(R.string.result_copied))
                }

                overlays.showResult(
                    outcome = outcome,
                    showLines = settings.showSegments,
                    offlineAvailable = dispatcher.offlineAvailable(forceOffline),
                )
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
