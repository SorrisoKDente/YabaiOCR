package dev.yabai.ocr

import android.Manifest
import android.app.Activity
import android.app.Dialog
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dev.yabai.ocr.ai.AskAi
import dev.yabai.ocr.anki.AnkiDroidGateway
import dev.yabai.ocr.core.DonationInfo
import dev.yabai.ocr.core.ppocr.PPOcrModelDownloader
import dev.yabai.ocr.core.ppocr.PPOcrModelStore
import dev.yabai.ocr.dict.DictionaryRepository
import dev.yabai.ocr.dict.DictionaryUpdateScheduler
import dev.yabai.ocr.overlay.PickerReadingMode
import dev.yabai.ocr.overlay.actionButton
import dev.yabai.ocr.service.FloatingOcrService
import dev.yabai.ocr.update.ApkInstall
import dev.yabai.ocr.update.AppUpdate
import dev.yabai.ocr.update.AppUpdateChecker
import dev.yabai.ocr.util.dp
import dev.yabai.ocr.update.markdownToSpanned
import android.text.method.LinkMovementMethod
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

    private lateinit var updateVersionText: TextView
    private lateinit var btnCheckAppUpdate: Button
    private lateinit var checkAppUpdatesOnStart: CheckBox
    private lateinit var appUpdateStatus: TextView
    private lateinit var appUpdateProgress: ProgressBar
    private lateinit var btnReleaseNotes: Button
    private lateinit var btnDownloadUpdate: Button

    /** The release the current screen is talking about; null until a check finds a newer one. */
    private var pendingUpdate: AppUpdate? = null

    /** The automatic check runs once per launch, not on every resume of the activity. */
    private var autoUpdateCheckDone = false

    /** Id of the APK download in progress; the completion broadcast is matched against it. */
    private var updateDownloadId: Long = -1L
    private var updateDownloadReceiver: BroadcastReceiver? = null

    /** Polls DownloadManager's byte counters; cancelled when the download ends. */
    private var updateProgressJob: Job? = null

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

    /**
     * The AnkiDroid provider permission (A5). Asked from this screen, once, because a floating
     * panel cannot show the system dialog without losing the window it belongs to.
     */
    private val ankiPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

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

        bindTabs(savedInstanceState?.getInt(STATE_TAB) ?: 0)
        bindThemeSelector()
        bindAppUpdates()
        bindLanguageSelector()
        bindEngineSelector()
        bindBehaviourSwitches()
        bindTranslationAndDictionaries()
        bindAskAi()
        bindDonation()
        requestAnkiPermissionIfNeeded()

        btnStart.setOnClickListener { onStartClicked() }
        btnStop.setOnClickListener { stopService() }

        registerUpdateDownloadReceiver()
    }

    /**
     * Asks for AnkiDroid's read/write permission the first time the app opens with the provider
     * visible. A denial is remembered: the panel then queues the card and says why.
     */
    private fun requestAnkiPermissionIfNeeded() {
        if (!settings.showAnkiButton || settings.ankiPermissionAsked) return
        val gateway = AnkiDroidGateway(this)
        if (!gateway.isInstalled() || gateway.hasPermission()) return
        settings.ankiPermissionAsked = true
        ankiPermissionLauncher.launch(AnkiDroidGateway.PERMISSION)
    }

    override fun onResume() {
        super.onResume()
        refreshPermissions()
        refreshRunningState()
        refreshDictionarySummary()
        updatePpOcrDownloadUi()
        refreshAiApp()
        handler.removeCallbacks(statusPoll)
        handler.postDelayed(statusPoll, 1000L)

        // The automatic check belongs to «opening the app», not to every resume.
        if (!autoUpdateCheckDone && settings.checkUpdatesOnStart) {
            autoUpdateCheckDone = true
            checkForAppUpdate(manual = false)
        }
    }

    override fun onPause() {
        handler.removeCallbacks(statusPoll)
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        updateDownloadReceiver?.let { receiver ->
            runCatching { unregisterReceiver(receiver) }
        }
        updateDownloadReceiver = null
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // A rotation must not send the reader back to Geral in the middle of a setting.
        outState.putInt(STATE_TAB, findViewById<TabLayout>(R.id.settingsTabs).selectedTabPosition)
    }

    // --- settings wiring -----------------------------------------------------------------------

    /** The page each tab shows, in the order the strip lists them. */
    private val tabPages = listOf(
        R.id.pageGeneral,
        R.id.pageReading,
        R.id.pageDictionaries,
        R.id.pageAbout,
    )

    /**
     * The screen split into tabs, the way the desktop settings already were (R56).
     *
     * Nine full-height sections in one scroll meant passing language, permissions, engine,
     * behaviour, dictionaries, translation, AI, updates and donations to reach the last one, and the
     * subject a reader was looking for could be a screen and a half away. The tabs keep the same
     * content and the same view ids — only one page is laid out at a time, and the strip stays put,
     * so another subject is always one tap away whatever the current page's height.
     */
    private fun bindTabs(restoreTab: Int) {
        val tabs = findViewById<TabLayout>(R.id.settingsTabs)
        val scroll = findViewById<ScrollView>(R.id.settingsScroll)

        fun show(index: Int) {
            tabPages.forEachIndexed { position, id ->
                findViewById<View>(id).visibility =
                    if (position == index) View.VISIBLE else View.GONE
            }
            // A page starts at its own beginning: carrying the previous page's offset over lands
            // the new one halfway down, with its first card out of sight.
            scroll.scrollTo(0, 0)
        }

        tabs.addTab(tabs.newTab().setText(R.string.tab_general))
        tabs.addTab(tabs.newTab().setText(R.string.tab_reading))
        tabs.addTab(tabs.newTab().setText(R.string.tab_dictionaries))
        tabs.addTab(tabs.newTab().setText(R.string.tab_about))

        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) = show(tab.position)

            override fun onTabUnselected(tab: TabLayout.Tab) = Unit

            /** Tapping the tab already open is the familiar «go back to the top of this page». */
            override fun onTabReselected(tab: TabLayout.Tab) = scroll.scrollTo(0, 0)
        })

        show(0)
        tabs.getTabAt(restoreTab)?.select()
    }

    /**
     * The theme control is the button in the header, and only it.
     *
     * The screen used to repeat the same three choices in a radio section further down; two places
     * writing the same preference is one more state to keep in sync, and the button already names
     * the current choice and cycles through them.
     */
    private fun bindThemeSelector() {
        val btnThemeToggle = findViewById<Button>(R.id.btnThemeToggle)

        fun updateUi() {
            btnThemeToggle.text = when (settings.appTheme) {
                AppThemeChoice.DARK -> "🌙 " + getString(R.string.theme_dark)
                AppThemeChoice.LIGHT -> "☀️ " + getString(R.string.theme_light)
                AppThemeChoice.SYSTEM -> "💻 " + getString(R.string.theme_system)
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
    }

    // --- app updates ---------------------------------------------------------------------------

    private fun bindAppUpdates() {
        updateVersionText = findViewById(R.id.updateVersionText)
        btnCheckAppUpdate = findViewById(R.id.btnCheckAppUpdate)
        checkAppUpdatesOnStart = findViewById(R.id.checkAppUpdatesOnStart)
        appUpdateStatus = findViewById(R.id.appUpdateStatus)
        appUpdateProgress = findViewById(R.id.appUpdateProgress)
        btnReleaseNotes = findViewById(R.id.btnReleaseNotes)
        btnDownloadUpdate = findViewById(R.id.btnDownloadUpdate)

        updateVersionText.text = getString(
            R.string.update_version_installed,
            AppUpdateChecker.currentVersion(),
        )
        checkAppUpdatesOnStart.isChecked = settings.checkUpdatesOnStart
        checkAppUpdatesOnStart.setOnCheckedChangeListener { _, checked ->
            settings.checkUpdatesOnStart = checked
        }

        btnCheckAppUpdate.setOnClickListener { checkForAppUpdate(manual = true) }
        btnReleaseNotes.setOnClickListener {
            pendingUpdate?.let { showReleaseNotes(it) }
        }
        btnDownloadUpdate.setOnClickListener { downloadUpdate() }
    }

    /**
     * Enqueues the APK download.
     *
     * `DownloadManager` lives in another process, so a test cannot watch what it does; it swaps this
     * for a fake to prove that the buttons which offer «Baixar» really reach the download and hand it
     * the release's URL, the way `DictionaryActivity.remote` stands in for the network.
     */
    internal var apkDownloader: (Context, String, String) -> Long = ApkInstall::downloadApk

    /**
     * Downloads the APK into Downloads and stops there.
     *
     * Installing would need the «install unknown apps» permission and the system installer on top of
     * the download; the user asked for «Baixar» only, and a downloaded APK is something they can open
     * whenever they want.
     */
    private fun downloadUpdate() {
        val update = pendingUpdate ?: return
        val url = update.apkUrl
        if (url.isNullOrBlank()) {
            // A release without an APK can only be read, not downloaded.
            openInBrowser(update.releaseUrl)
            return
        }

        appUpdateStatus.visibility = View.VISIBLE
        appUpdateStatus.text = getString(R.string.update_downloading)
        appUpdateProgress.visibility = View.VISIBLE
        appUpdateProgress.isIndeterminate = true
        appUpdateProgress.progress = 0
        updateDownloadId = runCatching {
            apkDownloader(this, url, update.latestVersion)
        }.getOrElse {
            appUpdateStatus.text = getString(R.string.update_download_failed)
            appUpdateProgress.visibility = View.GONE
            -1L
        }
        if (updateDownloadId < 0) return
        btnDownloadUpdate.isEnabled = false
        pollUpdateProgress(updateDownloadId)
    }

    // --- supporting the project ----------------------------------------------------------------

    /**
     * Donation controls, kept at the end of the screen.
     *
     * The app is free and non-commercial, so this is an offer and not a nag: Ko-fi for
     * international support, the Pix key for Brazil, and the license that governs the build.
     */
    private fun bindDonation() {
        findViewById<Button>(R.id.btnDonateKofi).setOnClickListener {
            openInBrowser(DonationInfo.KOFI_URL)
        }

        findViewById<TextView>(R.id.pixKeyText).text =
            getString(R.string.donate_pix_label, DonationInfo.PIX_KEY)

        findViewById<Button>(R.id.btnCopyPix).setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Pix", DonationInfo.PIX_KEY))
            toast(getString(R.string.donate_pix_copied))
        }

        findViewById<Button>(R.id.btnShowLicense).setOnClickListener { showLicenseDialog() }
    }

    private fun readLicense(): String = runCatching {
        assets.open("LICENSE").bufferedReader().use { it.readText() }
    }.getOrDefault(getString(R.string.donate_license_missing))

    private fun showLicenseDialog() {
        val accent = ContextCompat.getColor(this, R.color.accent)
        val secondary = ContextCompat.getColor(this, R.color.text_secondary)
        val codeBg = ContextCompat.getColor(this, R.color.card_bg)

        val body = TextView(this).apply {
            textSize = 12f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_primary))
            val horizontal = (20 * resources.displayMetrics.density).toInt()
            val vertical = (12 * resources.displayMetrics.density).toInt()
            setPadding(horizontal, vertical, horizontal, vertical)
            text = markdownToSpanned(readLicense(), accent, secondary, codeBg)
            movementMethod = android.text.method.LinkMovementMethod.getInstance()
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.donate_license_title)
            .setView(ScrollView(this).apply { addView(body) })
            .setPositiveButton(R.string.update_close, null)
            .show()
    }

    private fun registerUpdateDownloadReceiver() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (id == -1L || id != updateDownloadId) return
                updateDownloadId = -1L
                onUpdateDownloadFinished(id)
            }
        }
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        // DownloadManager lives in another process, so this is a broadcast from another app. A
        // NOT_EXPORTED receiver never sees it and the screen stays on «Baixando atualização…»
        // forever, which is exactly the bug this replaced.
        ContextCompat.registerReceiver(this, receiver, filter, updateReceiverFlags())
        updateDownloadReceiver = receiver
    }

    private fun onUpdateDownloadFinished(downloadId: Long) {
        updateProgressJob?.cancel()
        appUpdateProgress.visibility = View.GONE
        val update = pendingUpdate ?: return
        btnDownloadUpdate.isEnabled = true
        appUpdateStatus.visibility = View.VISIBLE
        appUpdateStatus.text = if (ApkInstall.isDownloaded(this, downloadId)) {
            getString(R.string.update_download_done, ApkInstall.fileName(update.latestVersion))
        } else {
            getString(R.string.update_download_failed)
        }
    }

    /**
     * Keeps the bar in step with DownloadManager until the file arrives.
     *
     * The system downloader writes no progress callback, so the screen reads its byte counters while
     * the transfer runs. The completion broadcast stops the loop; matching the id is the second
     * guard, so a newer download is never painted with the previous one's progress.
     */
    private fun pollUpdateProgress(downloadId: Long) {
        updateProgressJob?.cancel()
        updateProgressJob = lifecycleScope.launch {
            while (updateDownloadId == downloadId) {
                val progress = withContext(Dispatchers.IO) {
                    ApkInstall.awaitProgress { ApkInstall.progress(this@MainActivity, downloadId) }
                } ?: break

                val percent = progress.percent
                if (percent == null) {
                    // The server has not reported a size yet.
                    appUpdateProgress.isIndeterminate = true
                } else {
                    appUpdateProgress.isIndeterminate = false
                    appUpdateProgress.progress = percent
                    appUpdateStatus.text = getString(R.string.update_downloading_percent, percent)
                }
                delay(400L)
            }
        }
    }

    /** Silent when automatic: a failed background check must not interrupt the user. */
    private fun checkForAppUpdate(manual: Boolean) {
        if (manual) {
            btnCheckAppUpdate.isEnabled = false
            appUpdateStatus.visibility = View.VISIBLE
            appUpdateStatus.text = getString(R.string.update_checking)
        }

        lifecycleScope.launch {
            val update = withContext(Dispatchers.IO) { AppUpdateChecker.check() }
            if (manual) btnCheckAppUpdate.isEnabled = true

            if (update == null) {
                if (manual) {
                    appUpdateStatus.visibility = View.VISIBLE
                    appUpdateStatus.text = getString(R.string.update_check_failed)
                }
                return@launch
            }

            pendingUpdate = update.takeIf { it.hasUpdate }
            if (update.hasUpdate) {
                appUpdateStatus.visibility = View.VISIBLE
                appUpdateStatus.text = getString(
                    R.string.update_available_message,
                    update.latestVersion,
                )
                btnReleaseNotes.visibility = View.VISIBLE
                btnDownloadUpdate.visibility = View.VISIBLE
                if (!manual) {
                    // «Check for updates when the app starts» asked to be told, not just to have a
                    // status line change somewhere down the screen. The dialog offers the download
                    // right away; the inline controls keep the same actions available afterwards.
                    showUpdateFoundDialog(update)
                }
            } else {
                btnReleaseNotes.visibility = View.GONE
                btnDownloadUpdate.visibility = View.GONE
                if (manual) {
                    appUpdateStatus.visibility = View.VISIBLE
                    appUpdateStatus.text = getString(
                        R.string.update_up_to_date,
                        update.currentVersion,
                    )
                }
            }
        }
    }

    /**
     * The release notes, rendered from the release's own Markdown.
     *
     * The dialog scrolls because release bodies can be long; links stay tappable, which is the
     * point of previewing instead of dumping the raw text.
     */
    private fun showReleaseNotes(update: AppUpdate) {
        val body = TextView(this).apply {
            textSize = 14f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_primary))
            val horizontal = (20 * resources.displayMetrics.density).toInt()
            val vertical = (12 * resources.displayMetrics.density).toInt()
            setPadding(horizontal, vertical, horizontal, vertical)
            text = markdownToSpanned(
                markdown = update.releaseNotes.ifBlank {
                    this@MainActivity.getString(R.string.update_release_notes_empty)
                },
                accentColor = ContextCompat.getColor(this@MainActivity, R.color.accent),
                secondaryColor = ContextCompat.getColor(this@MainActivity, R.color.text_secondary),
                codeBackgroundColor = ContextCompat.getColor(this@MainActivity, R.color.text_secondary),
            )
            movementMethod = LinkMovementMethod.getInstance()
        }

        val scroll = ScrollView(this).apply { addView(body) }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.update_release_notes_title, update.latestVersion))
            .setView(scroll)
            .setPositiveButton(R.string.update_close, null)
            .show()
    }

    private fun openInBrowser(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (t: Throwable) {
            toast(getString(R.string.update_link_failed))
        }
    }

    /**
     * The startup notice: the automatic check found a release, and the download is one tap away.
     *
     * «Depois» only closes the dialog; the inline status and buttons stay, so the update is not lost.
     */
    private fun showUpdateFoundDialog(update: AppUpdate) {
        if (isFinishing || isDestroyed) return

        val content = layoutInflater.inflate(R.layout.dialog_update_available, null)
        content.findViewById<TextView>(R.id.dialogUpdateVersion).text =
            getString(R.string.update_dialog_version, update.latestVersion)

        // A plain Dialog, not an AlertDialog: the card, its background and its border are the
        // layout's own, so the notice looks like the desktop window instead of the platform's
        // default dialog surface.
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(content)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        content.findViewById<Button>(R.id.dialogUpdateDownload).setOnClickListener {
            dialog.dismiss()
            // «Baixar» starts the download and opens «Sobre»: the progress bar, the percentage and
            // the saved file live there, and a dialog that closes on the tap would otherwise leave
            // the download without any visible feedback.
            showAboutPage()
            downloadUpdate()
        }
        content.findViewById<Button>(R.id.dialogUpdateNotes).setOnClickListener {
            dialog.dismiss()
            showReleaseNotes(update)
        }
        content.findViewById<Button>(R.id.dialogUpdateLater).setOnClickListener { dialog.dismiss() }

        dialog.show()
        dialog.window?.setLayout(
            minOf((resources.displayMetrics.widthPixels * 0.92f).toInt(), dp(440)),
            WindowManager.LayoutParams.WRAP_CONTENT,
        )
    }

    /** Opens the «Sobre» tab, where the update controls and the download progress live. */
    private fun showAboutPage() {
        val tabs = findViewById<TabLayout>(R.id.settingsTabs)
        tabs.getTabAt(tabPages.indexOf(R.id.pageAbout))?.select()
    }

    private fun bindLanguageSelector() {
        val langGroup = findViewById<RadioGroup>(R.id.langGroup) ?: return
        val checkedId = when (settings.appLanguage) {
            AppLanguageChoice.PT_BR -> R.id.langPtBr
            AppLanguageChoice.EN_US -> R.id.langEnUs
            AppLanguageChoice.SYSTEM -> R.id.langSystem
        }
        if (langGroup.checkedRadioButtonId != checkedId) {
            langGroup.check(checkedId)
        }
        langGroup.setOnCheckedChangeListener { _, id ->
            val newLang = when (id) {
                R.id.langPtBr -> AppLanguageChoice.PT_BR
                R.id.langEnUs -> AppLanguageChoice.EN_US
                else -> AppLanguageChoice.SYSTEM
            }
            if (settings.appLanguage != newLang) {
                settings.appLanguage = newLang
            }
        }
    }

    private fun bindEngineSelector() {
        val group = findViewById<RadioGroup>(R.id.engineGroup)
        val container = findViewById<View>(R.id.enginePriorityContainer)

        fun updatePriorityVisibility() {
            container?.visibility = if (settings.engine == EngineChoice.AUTO) View.VISIBLE else View.GONE
            if (settings.engine == EngineChoice.AUTO) {
                renderEnginePriorityList()
            }
        }

        val checked = when (settings.engine) {
            EngineChoice.LENS -> R.id.engineLens
            EngineChoice.PP_OCR -> R.id.enginePpOcr
            EngineChoice.MLKIT -> R.id.engineMlKit
            EngineChoice.AUTO -> R.id.engineAuto
        }
        group.check(checked)
        updatePriorityVisibility()
        updatePpOcrDownloadUi()

        group.setOnCheckedChangeListener { _, id ->
            settings.engine = when (id) {
                R.id.engineLens -> EngineChoice.LENS
                R.id.enginePpOcr -> EngineChoice.PP_OCR
                R.id.engineMlKit -> EngineChoice.MLKIT
                else -> EngineChoice.AUTO
            }
            updatePriorityVisibility()
            updatePpOcrDownloadUi()
        }
    }

    private var downloadJob: kotlinx.coroutines.Job? = null

    private fun updatePpOcrDownloadUi() {
        val store = PPOcrModelStore(settings.ppOcrDir)
        val btnDownload = findViewById<Button>(R.id.btnDownloadPpOcr)
        val txtStatus = findViewById<TextView>(R.id.txtPpOcrStatus)
        if (btnDownload == null || txtStatus == null) return

        val available = store.isAvailable()
        if (available) {
            btnDownload.visibility = View.GONE
            txtStatus.visibility = View.VISIBLE
            txtStatus.text = getString(R.string.pp_ocr_downloaded)
        } else if (settings.engine == EngineChoice.PP_OCR) {
            btnDownload.visibility = View.VISIBLE
            txtStatus.visibility = View.VISIBLE
            btnDownload.isEnabled = downloadJob?.isActive != true
            btnDownload.text = getString(R.string.pp_ocr_download)
            if (downloadJob?.isActive != true && (txtStatus.text.isNullOrEmpty() || txtStatus.text == getString(R.string.pp_ocr_downloaded))) {
                txtStatus.text = getString(R.string.pp_ocr_not_installed)
            }
            btnDownload.setOnClickListener {
                startPpOcrDownload(btnDownload, txtStatus)
            }
        } else {
            btnDownload.visibility = View.GONE
            txtStatus.visibility = View.GONE
        }
    }

    private fun startPpOcrDownload(btnDownload: Button, txtStatus: TextView) {
        if (downloadJob?.isActive == true) return
        btnDownload.isEnabled = false
        txtStatus.text = getString(R.string.pp_ocr_downloading, "", "0%")

        val store = PPOcrModelStore(settings.ppOcrDir)
        val downloader = PPOcrModelDownloader(store)

        downloadJob = lifecycleScope.launch(Dispatchers.IO) {
            val result = runCatching {
                downloader.downloadModels { label, read, total ->
                    val percent = if (total > 0) "${(read * 100 / total)}%" else "${read / 1024 / 1024} MB"
                    withContext(Dispatchers.Main) {
                        txtStatus.text = getString(R.string.pp_ocr_downloading, label, percent)
                    }
                }
            }
            withContext(Dispatchers.Main) {
                result.onSuccess {
                    Toast.makeText(this@MainActivity, R.string.pp_ocr_downloaded, Toast.LENGTH_SHORT).show()
                    updatePpOcrDownloadUi()
                }.onFailure { t ->
                    btnDownload.isEnabled = true
                    val msg = t.message ?: "Erro desconhecido"
                    txtStatus.text = getString(R.string.pp_ocr_download_failed, msg)
                    Toast.makeText(this@MainActivity, getString(R.string.pp_ocr_download_failed, msg), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun renderEnginePriorityList() {
        val priorityListLayout = findViewById<LinearLayout>(R.id.priorityListLayout) ?: return
        priorityListLayout.removeAllViews()

        val list = settings.enginePriorityList
        list.forEachIndexed { index, choice ->
            val label = when (choice) {
                EngineChoice.LENS -> getString(R.string.engine_label_lens)
                EngineChoice.PP_OCR -> getString(R.string.engine_label_pp_ocr)
                EngineChoice.MLKIT -> getString(R.string.engine_label_mlkit)
                else -> choice.name
            }

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 8, 0, 8)
                gravity = android.view.Gravity.CENTER_VERTICAL
            }

            val textView = TextView(this).apply {
                text = "${index + 1}º  $label"
                setTextColor(getColor(R.color.text_primary))
                textSize = 13f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            row.addView(textView)

            // R46c: the same arrows as the dictionary list, in the app's accent, with the words
            // only for a screen reader — «▲ Subir» on a 30 dp button was two glyphs and a guess.
            if (index > 0) {
                val upBtn = actionButton("↑").apply {
                    contentDescription = getString(R.string.dictionary_up)
                    setOnClickListener {
                        val current = settings.enginePriorityList.toMutableList()
                        val tmp = current[index]
                        current[index] = current[index - 1]
                        current[index - 1] = tmp
                        settings.enginePriorityList = current
                        renderEnginePriorityList()
                    }
                }
                row.addView(upBtn)
            }

            if (index < list.size - 1) {
                val downBtn = actionButton("↓").apply {
                    contentDescription = getString(R.string.dictionary_down)
                    setOnClickListener {
                        val current = settings.enginePriorityList.toMutableList()
                        val tmp = current[index]
                        current[index] = current[index + 1]
                        current[index + 1] = tmp
                        settings.enginePriorityList = current
                        renderEnginePriorityList()
                    }
                }
                // R47c: the two moves are two buttons, not one «↑↓» block. The gap is the same one
                // the dictionary list puts between its arrows.
                row.addView(
                    downBtn,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).apply { marginStart = dp(6) },
                )
            }

            priorityListLayout.addView(row)
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
        // No «Consultar os dicionários» switch: the panel always looks the text up (R46a).
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

        // R35d moved here by R46b: the cleanup changes what the dictionaries are asked, so it sits
        // beside them instead of on their own screen. What the panel shows is untouched.
        val stripReadings = findViewById<CheckBox>(R.id.checkStripReadings).apply {
            isChecked = settings.stripSearchReadings
        }
        stripReadings.setOnCheckedChangeListener { _, checked ->
            settings.stripSearchReadings = checked
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
            hint = getString(R.string.default_ai_prompt)
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

    /** Keeps the dictionary line above the button honest after installs or removals. */
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

    private companion object {
        /** Which tab the settings were showing, kept across a rotation (R56). */
        const val STATE_TAB = "settingsTab"
    }
}

/**
 * Flags for the receiver that listens to `DownloadManager.ACTION_DOWNLOAD_COMPLETE`.
 *
 * The broadcast comes from the system's download manager, another process. `RECEIVER_NOT_EXPORTED`
 * makes Android drop it, which left the screen on «Baixando atualização…» after a finished download.
 */
internal fun updateReceiverFlags(): Int = ContextCompat.RECEIVER_EXPORTED
