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
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import dev.yabai.ocr.ai.AskAi
import dev.yabai.ocr.dict.DictionaryRepository
import dev.yabai.ocr.dict.DictionaryUpdateScheduler
import dev.yabai.ocr.overlay.PickerReadingMode
import dev.yabai.ocr.service.FloatingOcrService
import java.util.Date

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
    private lateinit var btnOverlayPermission: Button
    private lateinit var btnNotificationPermission: Button

    private lateinit var dictionarySummary: TextView

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
        settings = AppSettings(this)
        settings.applyTheme(this)

        setContentView(R.layout.activity_main)

        // WorkManager persists the periodic check, but a force-stop clears it; opening the app is
        // the moment to put it back without resetting the period (R43).
        DictionaryUpdateScheduler.ensure(this, settings.dictionaryUpdateInterval)

        statusText = findViewById(R.id.statusText)
        overlayStatus = findViewById(R.id.overlayStatus)
        notificationStatus = findViewById(R.id.notificationStatus)
        btnStart = findViewById(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)
        btnOverlayPermission = findViewById(R.id.btnOverlayPermission)
        btnNotificationPermission = findViewById(R.id.btnNotificationPermission)
        dictionarySummary = findViewById(R.id.dictionarySummary)

        btnOverlayPermission.setOnClickListener {
            if (Settings.canDrawOverlays(this)) confirmRemoveOverlay() else requestOverlayPermission()
        }
        btnNotificationPermission.setOnClickListener {
            if (hasNotificationPermission()) confirmRemoveNotifications() else requestNotifications()
        }
        findViewById<Button>(R.id.btnHelp).setOnClickListener {
            startActivity(Intent(this, HelpActivity::class.java))
        }

        bindThemeSelector()
        bindEngineSelector()
        bindBehaviourSwitches()
        bindTranslationAndDictionaries()
        bindAskAi()

        btnStart.setOnClickListener { onStartClicked() }
        btnStop.setOnClickListener { stopService() }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissions()
        refreshRunningState()
        refreshDictionarySummary()
        refreshAiApp()
        handler.postDelayed(statusPoll, 1000L)
    }

    override fun onPause() {
        handler.removeCallbacks(statusPoll)
        super.onPause()
    }

    // --- settings wiring -----------------------------------------------------------------------

    private fun bindThemeSelector() {
        val btnThemeToggle = findViewById<Button>(R.id.btnThemeToggle)
        val themeGroup = findViewById<RadioGroup>(R.id.themeGroup)

        fun updateUi() {
            btnThemeToggle.text = when (settings.appTheme) {
                AppThemeChoice.DARK -> "🌙 Escuro"
                AppThemeChoice.LIGHT -> "☀️ Claro"
                AppThemeChoice.SYSTEM -> "💻 Sistema"
            }
            val checkedId = when (settings.appTheme) {
                AppThemeChoice.DARK -> R.id.themeDark
                AppThemeChoice.LIGHT -> R.id.themeLight
                AppThemeChoice.SYSTEM -> R.id.themeSystem
            }
            if (themeGroup.checkedRadioButtonId != checkedId) {
                themeGroup.check(checkedId)
            }
        }

        updateUi()

        btnThemeToggle.setOnClickListener {
            val nextTheme = when (settings.appTheme) {
                AppThemeChoice.DARK -> AppThemeChoice.LIGHT
                AppThemeChoice.LIGHT -> AppThemeChoice.SYSTEM
                AppThemeChoice.SYSTEM -> AppThemeChoice.DARK
            }
            if (settings.appTheme != nextTheme) {
                settings.appTheme = nextTheme
                updateUi()
            }
        }

        themeGroup.setOnCheckedChangeListener { _, id ->
            val newTheme = when (id) {
                R.id.themeLight -> AppThemeChoice.LIGHT
                R.id.themeSystem -> AppThemeChoice.SYSTEM
                else -> AppThemeChoice.DARK
            }
            if (settings.appTheme != newTheme) {
                settings.appTheme = newTheme
                updateUi()
            }
        }
    }

    private fun bindEngineSelector() {
        val group = findViewById<RadioGroup>(R.id.engineGroup)
        val checked = when (settings.engine) {
            EngineChoice.UNLIMITED_OCR -> R.id.engineUnlimitedOcr
            EngineChoice.LENS -> R.id.engineLens
            EngineChoice.MLKIT -> R.id.engineMlKit
            EngineChoice.AUTO -> R.id.engineAuto
        }
        group.check(checked)
        group.setOnCheckedChangeListener { _, id ->
            settings.engine = when (id) {
                R.id.engineUnlimitedOcr -> EngineChoice.UNLIMITED_OCR
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

        // Free placement is the default: the bubble stays where it is dropped, inside the screen
        // (R35c). Pinning is for whoever wants it always out of the way on the same side.
        val bubbleOnEdge = findViewById<CheckBox>(R.id.checkBubbleOnEdge).apply {
            isChecked = settings.bubbleOnEdge
        }
        bubbleOnEdge.setOnCheckedChangeListener { _, checked -> settings.bubbleOnEdge = checked }

        val upscale = findViewById<CheckBox>(R.id.checkUpscale).apply { isChecked = settings.upscaleSmall }
        upscale.setOnCheckedChangeListener { _, checked -> settings.upscaleSmall = checked }

        // The same flag the panel's «Imagem» button writes: whoever turns it on in one place finds it
        // on in the other.
        val showImage = findViewById<CheckBox>(R.id.checkShowImage).apply { isChecked = settings.showImage }
        showImage.setOnCheckedChangeListener { _, checked -> settings.showImage = checked }

        // What to do with the readings Lens reports beside the kanji. Dropping them is the default
        // because a reading is not part of the sentence; keeping them is for whoever wants to read
        // the word aloud.
        val furigana = findViewById<CheckBox>(R.id.checkFurigana).apply {
            isChecked = settings.furiganaInParentheses
        }
        furigana.setOnCheckedChangeListener { _, checked -> settings.furiganaInParentheses = checked }

        // The other direction: the readings the manga did *not* print, taken from the dictionaries.
        val furiganaReadings = findViewById<CheckBox>(R.id.checkFuriganaReadings).apply {
            isChecked = settings.showFurigana
        }
        furiganaReadings.setOnCheckedChangeListener { _, checked -> settings.showFurigana = checked }

        // The same readings, but drawn on the page in the picker, before anything is tapped. Both
        // ways are offered because they trade differently: ruby keeps the drawing visible and is as
        // small as printed ruby, the strip is readable and covers part of the page.
        val pickerReading = findViewById<RadioGroup>(R.id.groupPickerReading)
        pickerReading.check(
            when (settings.pickerReading) {
                PickerReadingMode.RUBY -> R.id.readingRuby
                PickerReadingMode.LABEL -> R.id.readingLabel
                PickerReadingMode.OFF -> R.id.readingOff
            },
        )
        pickerReading.setOnCheckedChangeListener { _, id ->
            settings.pickerReading = when (id) {
                R.id.readingRuby -> PickerReadingMode.RUBY
                R.id.readingLabel -> PickerReadingMode.LABEL
                else -> PickerReadingMode.OFF
            }
        }
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

        val keepLast = findViewById<CheckBox>(R.id.checkDictionaryKeepLast).apply {
            isChecked = settings.dictionaryKeepLastSearch
        }
        keepLast.setOnCheckedChangeListener { _, checked ->
            settings.dictionaryKeepLastSearch = checked
            if (!checked) settings.dictionaryLastQuery = ""
        }
    }

    /**
     * The «Perguntar à IA» half of the panel: whether the button is offered, what is asked, and which
     * app answers.
     *
     * The app list is read off the phone when this screen opens and again on every tap, so an app
     * installed in between is offered without restarting anything — and the list is never written
     * down, because the only thing worth storing across phones is the package name (the choice).
     */
    private fun bindAskAi() {
        val showAi = findViewById<CheckBox>(R.id.checkAskAi).apply { isChecked = settings.showAiButton }
        showAi.setOnCheckedChangeListener { _, checked -> settings.showAiButton = checked }

        // The hint *is* the default prompt, from the one place that defines it: after the field is
        // cleared it is what tells whoever asks themselves «and what did it send before?».
        val prompt = findViewById<EditText>(R.id.editAiPrompt).apply {
            hint = AskAi.DEFAULT_PROMPT
            setText(settings.aiPrompt)
        }
        // Written as it is typed: this is not a form with a submit button, and the panel can be used
        // for a long time before anyone comes back to this screen.
        prompt.doAfterTextChanged { settings.aiPrompt = it?.toString().orEmpty() }

        findViewById<Button>(R.id.btnAiApp).setOnClickListener { chooseAiApp() }
        refreshAiApp()
    }

    /** The choice, named as the phone names it; a package that is gone reads as «no choice». */
    private fun refreshAiApp() {
        val button = findViewById<Button>(R.id.btnAiApp)
        val installed = AskAi.targets(this)
        button.isEnabled = installed.isNotEmpty()
        button.text = if (installed.isEmpty()) {
            getString(R.string.ai_app_none)
        } else {
            AskAi.label(settings.aiPackage, installed, getString(R.string.ai_app_always))
        }
    }

    private fun chooseAiApp() {
        val all = AskAi.targets(this)
        if (all.isEmpty()) {
            toast(getString(R.string.ai_app_none))
            return
        }
        showAiApps(all, showingAll = false)
    }

    /**
     * The app list, built from [AskAi.rows] — the same list the panel shows (R11, R18).
     *
     * The default is the first row: the phone's list of AI and translation apps, offered at the
     * moment of asking. Fixing one app is still possible; the footer reaches everything else the
     * phone reports, because a judgement made from a name can be wrong.
     */
    private fun showAiApps(all: List<AskAi.Target>, showingAll: Boolean) {
        val rows = AskAi.rows(all, showingAll)
        val labels = rows.map { row ->
            when (row) {
                AskAi.Row.Automatic -> getString(R.string.ai_app_always)
                is AskAi.Row.App -> row.target.label
                AskAi.Row.Toggle -> if (showingAll) {
                    getString(R.string.result_ask_ai_only_ai)
                } else {
                    getString(R.string.result_ask_ai_all, all.size)
                }
            }
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.setting_ai_app)
            .setItems(labels.toTypedArray()) { _, which ->
                when (val row = rows[which]) {
                    AskAi.Row.Automatic -> {
                        settings.aiPackage = ""
                        refreshAiApp()
                    }

                    is AskAi.Row.App -> {
                        settings.aiPackage = row.target.packageName
                        refreshAiApp()
                    }

                    AskAi.Row.Toggle -> showAiApps(all, !showingAll)
                }
            }
            .show()
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
        // While the reader is paused the capture session is still valid: the button's job is to
        // resume it, not to ask the system for the screen a second time (R33c).
        if (FloatingOcrService.isRunning && FloatingOcrService.isPaused) {
            val intent = Intent(this, FloatingOcrService::class.java)
                .setAction(FloatingOcrService.ACTION_RESUME)
            runCatching { startService(intent) }
            handler.postDelayed({ refreshRunningState() }, 300L)
            return
        }

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

    /**
     * The way back out of a permission (R33d).
     *
     * Android only lets an app *ask* for these two; taking them away is the user's decision in the
     * system screen. The dialog exists so that a tap on a button that has said «Conceder» for months
     * does not silently send the user to a settings page they did not mean to open.
     */
    private fun confirmRemoveOverlay() {
        AlertDialog.Builder(this)
            .setTitle(R.string.permission_remove_overlay_title)
            .setMessage(R.string.permission_remove_overlay_message)
            .setNegativeButton(R.string.permission_cancel, null)
            .setPositiveButton(R.string.permission_remove_confirm) { _, _ ->
                // The app-specific page opens with the switch already on, so removing it is one tap.
                requestOverlayPermission()
            }
            .show()
    }

    private fun confirmRemoveNotifications() {
        AlertDialog.Builder(this)
            .setTitle(R.string.permission_remove_notifications_title)
            .setMessage(R.string.permission_remove_notifications_message)
            .setNegativeButton(R.string.permission_cancel, null)
            .setPositiveButton(R.string.permission_remove_confirm) { _, _ ->
                openNotificationSettings()
            }
            .show()
    }

    private fun openNotificationSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
        }
        try {
            overlayLauncher.launch(intent)
        } catch (t: Throwable) {
            toast(getString(R.string.need_notification_permission))
        }
    }

    // --- state refresh -------------------------------------------------------------------------

    private fun refreshPermissions() {
        val overlay = Settings.canDrawOverlays(this)
        val notifications = hasNotificationPermission()
        paintStatus(overlayStatus, overlay)
        paintStatus(notificationStatus, notifications)
        // The same button grants and removes; its label says which one a tap will do (R33d).
        btnOverlayPermission.setText(
            if (overlay) R.string.permission_remove else R.string.permission_grant,
        )
        btnNotificationPermission.setText(
            if (notifications) R.string.permission_remove else R.string.permission_grant,
        )
    }

    private fun refreshRunningState() {
        val running = FloatingOcrService.isRunning
        val paused = running && FloatingOcrService.isPaused
        statusText.setText(
            when {
                !running -> R.string.status_stopped
                paused -> R.string.status_paused
                else -> R.string.status_running
            },
        )
        btnStart.setText(
            when {
                !running -> R.string.start_button
                paused -> R.string.start_button_paused
                else -> R.string.start_button_running
            },
        )
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
