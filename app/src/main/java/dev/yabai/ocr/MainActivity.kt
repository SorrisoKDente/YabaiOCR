package dev.yabai.ocr

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import dev.yabai.ocr.service.FloatingOcrService

/**
 * Settings and permissions screen. Does no real work itself: it collects the two permissions the
 * overlay needs, lets the user pick an engine, then hands a screen-capture grant to
 * [FloatingOcrService].
 */
class MainActivity : AppCompatActivity() {

    private lateinit var settings: AppSettings

    private lateinit var statusText: TextView
    private lateinit var overlayStatus: TextView
    private lateinit var notificationStatus: TextView
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button

    private val handler = Handler(Looper.getMainLooper())

    private val statusPoll = object : Runnable {
        override fun run() {
            refreshRunningState()
            handler.postDelayed(this, 1000L)
        }
    }

    // --- result contracts ----------------------------------------------------------------------

    private val overlayLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        // Many devices report RESULT_CANCELED even after granting, so re-read the real state.
        refreshPermissions()
    }

    private val notificationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        refreshPermissions()
    }

    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            val intent = Intent(this, FloatingOcrService::class.java).apply {
                action = FloatingOcrService.ACTION_START
                putExtra(FloatingOcrService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(FloatingOcrService.EXTRA_RESULT_DATA, data)
            }
            try {
                ContextCompat.startForegroundService(this, intent)
            } catch (t: Throwable) {
                toast(getString(R.string.capture_failed))
            }
            handler.postDelayed({ refreshRunningState() }, 400L)
        } else {
            toast(getString(R.string.projection_denied))
        }
    }

    // --- lifecycle -----------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        settings = AppSettings(this)

        statusText = findViewById(R.id.statusText)
        overlayStatus = findViewById(R.id.overlayStatus)
        notificationStatus = findViewById(R.id.notificationStatus)
        btnStart = findViewById(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)

        findViewById<Button>(R.id.btnOverlayPermission).setOnClickListener { requestOverlayPermission() }
        findViewById<Button>(R.id.btnNotificationPermission).setOnClickListener { requestNotifications() }

        bindEngineSelector()
        bindBehaviourSwitches()

        btnStart.setOnClickListener { onStartClicked() }
        btnStop.setOnClickListener { stopService() }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissions()
        refreshRunningState()
        handler.postDelayed(statusPoll, 1000L)
    }

    override fun onPause() {
        handler.removeCallbacks(statusPoll)
        super.onPause()
    }

    // --- settings wiring -----------------------------------------------------------------------

    private fun bindEngineSelector() {
        val group = findViewById<RadioGroup>(R.id.engineGroup)
        val checked = when (settings.engine) {
            EngineChoice.LENS -> R.id.engineLens
            EngineChoice.MLKIT -> R.id.engineMlKit
            EngineChoice.AUTO -> R.id.engineAuto
        }
        group.check(checked)
        group.setOnCheckedChangeListener { _, id ->
            settings.engine = when (id) {
                R.id.engineLens -> EngineChoice.LENS
                R.id.engineMlKit -> EngineChoice.MLKIT
                else -> EngineChoice.AUTO
            }
        }
    }

    private fun bindBehaviourSwitches() {
        val autoCopy = findViewById<CheckBox>(R.id.checkAutoCopy).apply { isChecked = settings.autoCopy }
        autoCopy.setOnCheckedChangeListener { _, checked -> settings.autoCopy = checked }

        val showSegments = findViewById<CheckBox>(R.id.checkShowSegments).apply {
            isChecked = settings.showSegments
        }
        showSegments.setOnCheckedChangeListener { _, checked -> settings.showSegments = checked }

        val upscale = findViewById<CheckBox>(R.id.checkUpscale).apply { isChecked = settings.upscaleSmall }
        upscale.setOnCheckedChangeListener { _, checked -> settings.upscaleSmall = checked }
    }

    // --- actions -------------------------------------------------------------------------------

    private fun onStartClicked() {
        if (!Settings.canDrawOverlays(this)) {
            toast(getString(R.string.need_overlay_permission))
            requestOverlayPermission()
            return
        }

        // Capture works without the notification permission, so this is a nudge, not a gate.
        if (!hasNotificationPermission()) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        launchScreenCapture()
    }

    private fun launchScreenCapture() {
        val manager = getSystemService(MediaProjectionManager::class.java)
        if (manager == null) {
            toast(getString(R.string.capture_failed))
            return
        }
        try {
            projectionLauncher.launch(manager.createScreenCaptureIntent())
        } catch (t: Throwable) {
            toast(getString(R.string.capture_failed))
        }
    }

    private fun stopService() {
        val intent = Intent(this, FloatingOcrService::class.java)
            .setAction(FloatingOcrService.ACTION_STOP)
        runCatching { startService(intent) }
        handler.postDelayed({ refreshRunningState() }, 300L)
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName"),
        )
        try {
            overlayLauncher.launch(intent)
        } catch (t: Throwable) {
            toast(getString(R.string.need_overlay_permission))
        }
    }

    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            toast(getString(R.string.permission_granted))
            return
        }
        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    // --- state refresh -------------------------------------------------------------------------

    private fun refreshPermissions() {
        paintStatus(overlayStatus, Settings.canDrawOverlays(this))
        paintStatus(notificationStatus, hasNotificationPermission())
    }

    private fun refreshRunningState() {
        val running = FloatingOcrService.isRunning
        statusText.setText(if (running) R.string.status_running else R.string.status_stopped)
        btnStart.setText(if (running) R.string.start_button_running else R.string.start_button)
        btnStop.visibility = if (running) View.VISIBLE else View.GONE
    }

    private fun paintStatus(view: TextView, granted: Boolean) {
        view.setText(if (granted) R.string.permission_granted else R.string.permission_missing)
        view.setTextColor(
            ContextCompat.getColor(this, if (granted) R.color.accent else R.color.text_secondary),
        )
    }

    private fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
