package dev.yabai.ocr.capture

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display
import android.view.WindowManager
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.media.projection.MediaProjection

/**
 * Keeps a single mirrored [VirtualDisplay] alive and hands out full-screen bitmaps on demand.
 *
 * Design notes:
 *  - One `MediaProjection` instance creates exactly one `VirtualDisplay`; Android 14+ rejects a
 *    second call, so rotation is handled with `resize()` + `setSurface()` instead of recreating it.
 *  - Frames are drained on every delivery and only converted to a `Bitmap` while a capture is
 *    armed, so an idle screen costs nothing.
 *  - `armCapture()` / `latestFrame()` / `disarmCapture()` bracket the screenshot: arming before the
 *    overlay is hidden makes sure the frame produced by that change is the one we keep, which
 *    matters because a mirrored display stops emitting frames while the screen is static.
 */
class ScreenCaptureSession(
    private val context: Context,
    private val projection: MediaProjection,
) {

    private val lock = Object()
    private var frameSequence = 0L
    private var pending: Bitmap? = null

    /** While armed, every incoming frame is converted so the newest one is always at hand. */
    @Volatile
    private var armed = false
    private var armedSequence = 0L

    private val thread = HandlerThread("yabai-capture").apply { start() }
    private val worker = Handler(thread.looper)

    private var reader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null

    private var width = 0
    private var height = 0
    private var densityDpi = 0

    /** True once the virtual display exists and frames have started flowing. */
    var isReady: Boolean = false
        private set

    fun start(): Boolean {
        val metrics = readDisplayMetrics()
        width = metrics.widthPixels
        height = metrics.heightPixels
        densityDpi = metrics.densityDpi

        return try {
            val newReader = newReader(width, height)
            reader = newReader
            virtualDisplay = projection.createVirtualDisplay(
                "yabai-ocr",
                width,
                height,
                densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                newReader.surface,
                null,
                worker,
            )
            isReady = true
            true
        } catch (t: Throwable) {
            Log.e(TAG, "could not start screen capture", t)
            isReady = false
            false
        }
    }

    /**
     * Starts keeping frames.
     *
     * Call this *before* hiding the overlays. Frame delivery is event-driven: a mirrored display
     * only pushes a frame when the screen content changes, so a "give me something newer" request
     * can wait forever on a static screen. Hiding our own windows guarantees at least one change,
     * and arming first guarantees we keep the frame that results from it.
     *
     * Cheap enough for the main thread: it only sets a flag and re-checks the display geometry.
     */
    fun armCapture() {
        if (!isReady) return
        syncGeometry()
        synchronized(lock) {
            armedSequence = frameSequence
            armed = true
        }
    }

    /**
     * Returns the most recent frame, waiting up to [timeoutMillis] for the first one to arrive
     * after [armCapture]. Must be called off the main thread.
     */
    fun latestFrame(timeoutMillis: Long = 1500L): Bitmap? {
        val deadline = SystemClock.elapsedRealtime() + timeoutMillis
        synchronized(lock) {
            while (frameSequence == armedSequence) {
                val remaining = deadline - SystemClock.elapsedRealtime()
                if (remaining <= 0) break
                try {
                    (lock as Object).wait(remaining)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
            }
            val bitmap = pending
            pending = null
            return bitmap
        }
    }

    /** Stops keeping frames and drops the one still buffered. */
    fun disarmCapture() {
        synchronized(lock) {
            armed = false
            pending?.recycle()
            pending = null
        }
    }

    fun stop() {
        isReady = false
        synchronized(lock) {
            armed = false
        }
        runCatching { virtualDisplay?.release() }
        virtualDisplay = null
        runCatching { reader?.close() }
        reader = null
        synchronized(lock) {
            pending?.recycle()
            pending = null
        }
        thread.quitSafely()
    }

    // --- internals -----------------------------------------------------------------------------

    private fun newReader(w: Int, h: Int): ImageReader {
        val r = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 3)
        r.setOnImageAvailableListener({ source -> onFrameAvailable(source) }, worker)
        return r
    }

    private fun onFrameAvailable(source: ImageReader) {
        val image: Image = try {
            source.acquireLatestImage()
        } catch (t: Throwable) {
            null
        } ?: return

        if (!armed) {
            // Keep the queue empty so the producer never stalls and we never hold a stale bitmap.
            image.close()
            return
        }

        try {
            val bitmap = image.toBitmap()
            synchronized(lock) {
                pending?.recycle()
                pending = bitmap
                frameSequence++
                (lock as Object).notifyAll()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "frame conversion failed", t)
        } finally {
            image.close()
        }
    }

    private fun Image.toBitmap(): Bitmap {
        val plane = planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * width

        val padded = Bitmap.createBitmap(
            width + rowPadding / pixelStride,
            height,
            Bitmap.Config.ARGB_8888,
        )
        padded.copyPixelsFromBuffer(buffer)

        val result = if (padded.width == width) {
            padded
        } else {
            Bitmap.createBitmap(padded, 0, 0, width, height).also { padded.recycle() }
        }
        result.prepareToDraw()
        return result
    }

    /** Re-arms the surface if the display rotated or changed size since the last capture. */
    private fun syncGeometry() {
        val metrics = readDisplayMetrics()
        if (metrics.widthPixels == width && metrics.heightPixels == height) return

        val display = virtualDisplay ?: return
        try {
            val newReader = newReader(metrics.widthPixels, metrics.heightPixels)
            display.setSurface(newReader.surface)
            display.resize(metrics.widthPixels, metrics.heightPixels, metrics.densityDpi)
            reader?.close()
            reader = newReader
            width = metrics.widthPixels
            height = metrics.heightPixels
            densityDpi = metrics.densityDpi
            synchronized(lock) {
                pending?.recycle()
                pending = null
            }
            Log.i(TAG, "capture surface resized to ${width}x$height")
        } catch (t: Throwable) {
            // Some OEM builds refuse to resize a projection-backed display. The capture still
            // works, just letterboxed; the service warns the user in that case.
            Log.w(TAG, "could not resize capture surface", t)
        }
    }

    private fun readDisplayMetrics(): DisplayMetrics {
        val metrics = DisplayMetrics()
        val displayManager = context.getSystemService(DisplayManager::class.java)
        val display = displayManager?.getDisplay(Display.DEFAULT_DISPLAY)
        if (display != null) {
            @Suppress("DEPRECATION")
            display.getRealMetrics(metrics)
            if (metrics.widthPixels > 0 && metrics.heightPixels > 0) {
                metrics.densityDpi = context.resources.displayMetrics.densityDpi
                return metrics
            }
        }

        // Fallbacks for the rare device where getRealMetrics reports nothing useful.
        val windowManager = context.getSystemService(WindowManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && windowManager != null) {
            runCatching {
                val bounds = windowManager.maximumWindowMetrics.bounds
                metrics.widthPixels = bounds.width()
                metrics.heightPixels = bounds.height()
            }
        }
        if (metrics.widthPixels <= 0 || metrics.heightPixels <= 0) {
            @SuppressLint("DiscouragedApi")
            val fallback = context.resources.displayMetrics
            metrics.widthPixels = fallback.widthPixels
            metrics.heightPixels = fallback.heightPixels
        }
        metrics.densityDpi = context.resources.displayMetrics.densityDpi
        return metrics
    }

    companion object {
        private const val TAG = "ScreenCaptureSession"

        /** Builds a `MediaProjection.Callback` that must be registered before creating a display. */
        fun stopCallback(onStop: () -> Unit) = object : MediaProjection.Callback() {
            override fun onStop() = onStop()
        }

        val mainHandler: Handler = Handler(Looper.getMainLooper())
    }
}
