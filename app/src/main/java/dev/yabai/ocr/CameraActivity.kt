package dev.yabai.ocr

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.Surface
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import dev.yabai.ocr.capture.CameraPhoto
import dev.yabai.ocr.capture.PhotoWait
import dev.yabai.ocr.overlay.roundedBackground
import dev.yabai.ocr.service.FloatingOcrService
import dev.yabai.ocr.util.dp
import java.io.File

/**
 * The bubble's camera (R46d, R48a): a screen of its own, not a doorway to the camera app.
 *
 * The system camera app puts «✕ / ✓» between the shot and the reading, and answering ✓ closed this
 * screen and opened another one with the photo. The shutter is the decision here: the preview is
 * replaced by the picture that was just taken — the very picture the picker will draw its boxes over
 * — and a spinner sits on it while the engines work. Nothing is asked and nothing is opened.
 *
 * The picture goes to a file in the app's cache, because that is what the engines read; the service
 * decodes it and deletes it, so nothing is ever added to the gallery. This screen stays until the
 * service says the reading is on screen ([FloatingOcrService.ACTION_PHOTO_READY]) and only then
 * steps out of the way — the picker's page is the same photo, so the change is invisible.
 */
class CameraActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var frozenPhoto: ImageView
    private lateinit var loading: View
    private lateinit var shutter: View
    private lateinit var close: View

    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null

    /** The file the camera wrote, until the service takes it over. */
    private var photo: File? = null

    /** True from the shutter press until the reading is on screen; a second press does nothing. */
    private var waitingForReading = false

    /** Whether the service already owns [photo]; it deletes the file, so this screen must not. */
    private var handedOff = false

    private var reading: BroadcastReceiver? = null

    /**
     * The wait for the service to say the reading is on screen (R48c).
     *
     * Its answer is what closes this screen; without a limit, a service that died or a notice that
     * was lost would leave the frozen photo and the spinner up for good.
     */
    private val wait = PhotoWait(onTimeout = { photoWaitExpired() })

    private val permission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCamera() else denyCamera()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The picture belongs to the camera session of this screen, and the service it hands it to
        // is the one that opened it.
        if (savedInstanceState != null || !FloatingOcrService.isRunning) {
            finish()
            return
        }
        buildScreen()
        listenForReading()
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) startCamera() else permission.launch(Manifest.permission.CAMERA)
    }

    // --- screen --------------------------------------------------------------------------------

    private fun buildScreen() {
        window.setBackgroundDrawable(ColorDrawable(Color.BLACK))
        // Drawn behind the bars: the page the picker shows is the whole screen, fitted and centred,
        // so the frozen photo has to be laid out the same way for the handover to be invisible.
        WindowCompat.setDecorFitsSystemWindows(window, false)

        previewView = PreviewView(this).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
        frozenPhoto = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            visibility = View.GONE
        }
        loading = buildLoading()
        shutter = buildShutter()
        close = buildClose()

        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        root.addView(previewView, matchParent())
        root.addView(frozenPhoto, matchParent())
        root.addView(
            loading,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            ),
        )
        root.addView(
            close,
            FrameLayout.LayoutParams(dp(44), dp(44), Gravity.TOP or Gravity.START),
        )
        root.addView(
            shutter,
            FrameLayout.LayoutParams(dp(76), dp(76), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL),
        )
        setContentView(root)
        applyBarInsets(root)
    }

    /** Keeps the shutter and the ✕ off the system bars, which this screen draws behind. */
    private fun applyBarInsets(root: FrameLayout) {
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            (close.layoutParams as? FrameLayout.LayoutParams)?.let {
                it.topMargin = dp(16) + bars.top
                it.leftMargin = dp(16) + bars.left
                close.requestLayout()
            }
            (shutter.layoutParams as? FrameLayout.LayoutParams)?.let {
                it.bottomMargin = dp(28) + bars.bottom
                shutter.requestLayout()
            }
            insets
        }
    }

    /** The picture stays put while the engines read it; this is the only thing that moves. */
    private fun buildLoading(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(22), dp(18), dp(22), dp(18))
        background = roundedBackground(0xCC1B1D24.toInt(), dp(16).toFloat(), 0x33FFFFFF)
        visibility = View.GONE
        addView(
            ProgressBar(this@CameraActivity).apply {
                isIndeterminate = true
                indeterminateTintList = ColorStateList.valueOf(Color.WHITE)
            },
        )
        addView(
            TextView(this@CameraActivity).apply {
                text = getString(R.string.camera_reading)
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                gravity = Gravity.CENTER
                setPadding(0, dp(12), 0, 0)
            },
        )
    }

    private fun buildShutter(): View = FrameLayout(this).apply {
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.WHITE)
            setStroke(dp(4), 0x66FFFFFF)
        }
        isClickable = true
        isFocusable = true
        isEnabled = false
        contentDescription = getString(R.string.camera_shutter)
        setOnClickListener { shoot() }
    }

    private fun buildClose(): View = ImageView(this).apply {
        setImageResource(R.drawable.ic_menu_stop)
        imageTintList = ColorStateList.valueOf(Color.WHITE)
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        setPadding(dp(10), dp(10), dp(10), dp(10))
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0x661B1D24)
            setStroke(dp(1), 0x55FFFFFF)
        }
        contentDescription = getString(R.string.camera_close)
        isClickable = true
        isFocusable = true
        setOnClickListener { finish() }
    }

    private fun matchParent() = FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.MATCH_PARENT,
    )

    // --- camera --------------------------------------------------------------------------------

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = runCatching { future.get() }.getOrNull()
            if (provider == null) {
                cameraFailed()
                return@addListener
            }
            cameraProvider = provider
            val preview = Preview.Builder().build().apply {
                setSurfaceProvider(previewView.surfaceProvider)
            }
            val capture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setTargetRotation(previewView.display?.rotation ?: Surface.ROTATION_0)
                .build()
            // A camera that cannot be opened (in use, removed, disabled) closes the screen, not the
            // app: the bubble is still there behind it.
            val bound = runCatching {
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
            }
            if (bound.isFailure) {
                cameraFailed()
                return@addListener
            }
            imageCapture = capture
            shutter.isEnabled = true
        }, ContextCompat.getMainExecutor(this))
    }

    private fun shoot() {
        val capture = imageCapture ?: return
        if (waitingForReading) return
        // The app's own cache, never the gallery: the service reads the file and deletes it, and a
        // shot that never got read is swept here before the next one.
        val directory = File(cacheDir, "photos").apply { mkdirs() }
        CameraPhoto.clearLeftovers(directory)
        val file = runCatching { File.createTempFile("photo-", ".jpg", directory) }.getOrNull()
        if (file == null) {
            cameraFailed()
            return
        }
        photo = file
        waitingForReading = true
        shutter.isEnabled = false
        capture.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    freezeAndHand(file)
                }

                override fun onError(exception: ImageCaptureException) {
                    // A failed shot leaves the camera where it is: the next press tries again.
                    waitingForReading = false
                    shutter.isEnabled = true
                    file.delete()
                    Toast.makeText(this@CameraActivity, R.string.capture_failed, Toast.LENGTH_SHORT).show()
                }
            },
        )
    }

    /**
     * The photo becomes the screen while the engines read it (R48a).
     *
     * The camera is released and the picture that was just taken is drawn where the preview was, so
     * the shutter itself is the «keep this photo» answer — there is no second step to give. The photo
     * is decoded before the handoff, because the service deletes the file as it reads it.
     */
    private fun freezeAndHand(file: File) {
        cameraProvider?.unbindAll()
        previewView.visibility = View.GONE
        loading.visibility = View.VISIBLE
        val maxSide = resources.displayMetrics.widthPixels.coerceAtLeast(1)
        Thread({
            val shown = CameraPhoto.preview(file, maxSide)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (shown != null) {
                    frozenPhoto.setImageBitmap(shown)
                    frozenPhoto.visibility = View.VISIBLE
                }
                hand(file)
            }
        }, "photo-preview").start()
    }

    /** From here the file belongs to the service, which reads it and deletes it. */
    private fun hand(file: File) {
        handedOff = true
        val intent = Intent(this, FloatingOcrService::class.java).apply {
            action = FloatingOcrService.ACTION_PHOTO
            putExtra(FloatingOcrService.EXTRA_PHOTO_PATH, file.path)
        }
        if (runCatching { startService(intent) }.isFailure) {
            handedOff = false
            file.delete()
            cameraFailed()
            return
        }
        // The answer closes this screen; the wait is what closes it when the answer never comes.
        wait.arm()
    }

    /**
     * The service never said the reading was on screen (R48c).
     *
     * Nothing is thrown away: the frozen photo gives the screen back to the preview and the shutter
     * is armed again, so the user can take another picture or leave with the ✕. A reading that shows
     * up late anyway is not a broken one — the picker opens over whatever is on screen by then.
     */
    private fun photoWaitExpired() {
        if (!waitingForReading) return
        waitingForReading = false
        loading.visibility = View.GONE
        frozenPhoto.visibility = View.GONE
        frozenPhoto.setImageDrawable(null)
        previewView.visibility = View.VISIBLE
        shutter.isEnabled = true
        Toast.makeText(this, R.string.camera_no_answer, Toast.LENGTH_LONG).show()
        startCamera()
    }

    /**
     * Waits for the service to put the reading on screen.
     *
     * It is what lets this screen go without a hole appearing: the picker draws the same photo as
     * its page, so by the time it is up there is nothing of the camera left to see.
     */
    private fun listenForReading() {
        if (reading != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action != FloatingOcrService.ACTION_PHOTO_READY) return
                wait.settle()
                finish()
            }
        }
        ContextCompat.registerReceiver(
            this,
            receiver,
            IntentFilter(FloatingOcrService.ACTION_PHOTO_READY),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        reading = receiver
    }

    private fun denyCamera() {
        Toast.makeText(this, R.string.camera_permission_denied, Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun cameraFailed() {
        Toast.makeText(this, R.string.capture_failed, Toast.LENGTH_SHORT).show()
        finish()
    }

    override fun onDestroy() {
        wait.settle()
        reading?.let { runCatching { unregisterReceiver(it) } }
        reading = null
        runCatching { cameraProvider?.unbindAll() }
        // Until the service has the file it is this screen's to delete; after that the service
        // deletes it, and doing it here could pull it from under the read.
        if (!handedOff) photo?.delete()
        super.onDestroy()
    }
}
