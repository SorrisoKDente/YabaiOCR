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
import android.text.format.DateFormat
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import dev.yabai.ocr.dict.DictionaryRepository
import dev.yabai.ocr.service.FloatingOcrService
import java.util.Date

/**
 * Settings and permissions screen. Does no real work itself: it collects the two permissions the
 * overlay needs, lets the user pick an engine, then hands a screen-capture grant to
 * [FloatingOcrService].
 */
class MainActivity : AppCompatActivity() {

    private lateinit var settings: AppSettings
    private lateinit var usage: UsageTracker

    private lateinit var statusText: TextView
    private lateinit var overlayStatus: TextView
    private lateinit var notificationStatus: TextView
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button

    private lateinit var usageToday: TextView
    private lateinit var usageTotal: TextView
    private lateinit var usageLast: TextView
    private lateinit var usageLimit: TextView
    private lateinit var dictionarySummary: TextView

    private val handler = Handler(Looper.getMainLooper())

    private val statusPoll = object : Runnable {
        override fun run() {
            refreshRunningState()
            refreshUsage()
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
        usage = UsageTracker(this)

        statusText = findViewById(R.id.statusText)
        overlayStatus = findViewById(R.id.overlayStatus)
        notificationStatus = findViewById(R.id.notificationStatus)
        btnStart = findViewById(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)

        usageToday = findViewById(R.id.usageToday)
        usageTotal = findViewById(R.id.usageTotal)
        usageLast = findViewById(R.id.usageLast)
        usageLimit = findViewById(R.id.usageLimit)
        dictionarySummary = findViewById(R.id.dictionarySummary)

        findViewById<Button>(R.id.btnOverlayPermission).setOnClickListener { requestOverlayPermission() }
        findViewById<Button>(R.id.btnNotificationPermission).setOnClickListener { requestNotifications() }
        findViewById<Button>(R.id.btnResetUsage).setOnClickListener {
            usage.reset()
            refreshUsage()
            toast(getString(R.string.usage_reset_done))
        }

        bindEngineSelector()
        bindBehaviourSwitches()
        bindTranslationAndDictionaries()

        btnStart.setOnClickListener { onStartClicked() }
        btnStop.setOnClickListener { stopService() }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissions()
        refreshRunningState()
        refreshUsage()
        refreshDictionarySummary()
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

        val pickText = findViewById<CheckBox>(R.id.checkPickText).apply { isChecked = settings.pickText }
        pickText.setOnCheckedChangeListener { _, checked -> settings.pickText = checked }

        val upscale = findViewById<CheckBox>(R.id.checkUpscale).apply { isChecked = settings.upscaleSmall }
        upscale.setOnCheckedChangeListener { _, checked -> settings.upscaleSmall = checked }
    }

    private fun bindTranslationAndDictionaries() {
        val dictionary = findViewById<CheckBox>(R.id.checkDictionary).apply {
            isChecked = settings.dictionaryLookup
        }
        dictionary.setOnCheckedChangeListener { _, checked -> settings.dictionaryLookup = checked }

        val translate = findViewById<CheckBox>(R.id.checkTranslate).apply {
            isChecked = settings.translateSentence
        }
        translate.setOnCheckedChangeListener { _, checked -> settings.translateSentence = checked }

        val targets = findViewById<RadioGroup>(R.id.translateTargetGroup)
        targets.check(if (settings.translateTarget == "en") R.id.translateEn else R.id.translatePt)
        targets.setOnCheckedChangeListener { _, id ->
            settings.translateTarget = if (id == R.id.translateEn) "en" else "pt"
        }

        findViewById<Button>(R.id.btnManageDictionaries).setOnClickListener {
            startActivity(DictionaryActivity.intent(this))
        }
    }

    /** Keeps the dictionary line under the checkbox honest after installs or removals. */
    private fun refreshDictionarySummary() {
        val installed = DictionaryRepository.get(this).installed()
        dictionarySummary.text = if (installed.isEmpty()) {
            getString(R.string.dictionary_summary_none)
        } else {
            getString(R.string.dictionary_summary, installed.size, installed.count { it.enabled })
        }
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

    // --- Lens usage ----------------------------------------------------------------------------

    private fun refreshUsage() {
        val snap = usage.snapshot()
        val timeFormat = DateFormat.getTimeFormat(this)
        val dateFormat = DateFormat.getDateFormat(this)

        usageToday.setText(getString(R.string.usage_today, snap.todayCount))

        usageTotal.text = if (snap.firstAtMillis == 0L) {
            getString(R.string.usage_total, snap.totalCount)
        } else {
            getString(R.string.usage_total, snap.totalCount) +
                " · " +
                getString(R.string.usage_since, dateFormat.format(Date(snap.firstAtMillis)))
        }

        usageLast.text = if (snap.lastAtMillis == 0L) {
            getString(R.string.usage_none)
        } else {
            val when_ = dateFormat.format(Date(snap.lastAtMillis)) +
                " " +
                timeFormat.format(Date(snap.lastAtMillis))
            getString(
                R.string.usage_last,
                when_,
                getString(if (snap.lastOk) R.string.usage_ok else R.string.usage_error),
            )
        }

        val remaining = snap.rateLimitRemainingSeconds()
        when {
            remaining > 0L -> {
                usageLimit.visibility = View.VISIBLE
                usageLimit.text = getString(
                    R.string.usage_limit_with_delay,
                    snap.lastHttpStatus,
                    formatDuration(remaining),
                )
            }

            snap.isRefusal() -> {
                usageLimit.visibility = View.VISIBLE
                usageLimit.text = getString(R.string.usage_limit_no_delay, snap.lastHttpStatus)
            }

            else -> usageLimit.visibility = View.GONE
        }
    }

    private fun formatDuration(totalSeconds: Long): String {
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return when {
            minutes <= 0L -> "$seconds s"
            seconds == 0L -> "$minutes min"
            else -> "$minutes min $seconds s"
        }
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
