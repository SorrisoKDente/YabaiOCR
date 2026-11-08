package dev.yabai.ocr.service

import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Rect
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import dev.yabai.ocr.AppSettings
import dev.yabai.ocr.R
import dev.yabai.ocr.ai.AskAi
import dev.yabai.ocr.dict.DictionaryRepository
import dev.yabai.ocr.dict.searchWords
import dev.yabai.ocr.overlay.OverlayController
import dev.yabai.ocr.overlay.ResultOverlay
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * The dictionary panel another app asked for (R35e, R37).
 *
 * ReadEra (and the share sheet) hand the app a word; the answer belongs **over** the page the user
 * is reading, not on a screen of its own. This service owns one `TYPE_APPLICATION_OVERLAY` window —
 * the very panel the bubble opens — and closes itself when the panel goes away. There is no capture,
 * no bubble and no foreground notification: the service exists only while the panel does, and the
 * activity that received the intent starts it and finishes.
 *
 * The panel is the same object as the bubble's, so the word cards, the kanji block, the dictionary
 * chooser and the selection menu come along unchanged, and a tap outside the card closes it the same
 * way it closes the dictionary opened from the bubble.
 */
class LookupOverlayService : Service() {

    private val scope = MainScope()
    private val settings by lazy { AppSettings(this) }
    private lateinit var overlays: OverlayController

    /** The main looper, for the one delayed step this service has: the first panel (R38). */
    private val handler = Handler(Looper.getMainLooper())

    /** Draws the first panel once the doorway has left the screen; see [open]. */
    private val showPanel = Runnable { showPanelNow() }

    /** The query the panel is on, so a filter change re-runs the right one. */
    private var query = ""

    private var searchJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        overlays = OverlayController(this).apply {
            callbacks = object : OverlayController.Callbacks {
                override fun onDictionarySearchChanged(text: String) = lookup(text)

                override fun onDictionarySearchModeChanged(searchByTranslation: Boolean) {
                    settings.searchByTranslation = searchByTranslation
                    lookup(query)
                }

                override fun onDictionaryPicked(title: String?) {
                    settings.dictionaryFilter = title.orEmpty()
                    lookup(query)
                }

                override fun onCopyRequested(text: String, closeAfter: Boolean) {
                    copyToClipboard(text)
                }

                override fun onResultDismissed() = stopSelf()

                // The search card has no capture behind it: none of these can be asked for.
                override fun onFullScreenRequested() = Unit
                override fun onRegionRequested() = Unit
                override fun onRegionConfirmed(rect: Rect?) = Unit
                override fun onRegionCancelled() = Unit
                override fun onStopRequested() = stopSelf()
                override fun onOpenSettingsRequested() = Unit
                override fun onAnalyseRequested(text: String) = Unit
                override fun onRetryOfflineRequested() = Unit
                override fun onBlockPicked(index: Int) = Unit
                override fun onPickAllRequested() = Unit
                override fun onRepickRequested() = Unit
                override fun onKanjiRequested() = Unit
                override fun onKanjiTapped(piece: Int, index: Int) = Unit
                override fun onImageToggleRequested() = Unit
                override fun onAskAiRequested(text: String) = askAi(text)
                override fun onAskAiToRequested(packageName: String, text: String) = openAi(packageName, text)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val text = intent?.getStringExtra(EXTRA_TEXT).orEmpty()
        open(text)
        // The panel is the only thing this service does: if Android ever takes it down, there is
        // nothing worth restarting.
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        searchJob?.cancel()
        handler.removeCallbacks(showPanel)
        // Normally the controller has already removed the window through onResultDismissed; this is
        // the belt for a stop that came from elsewhere (the system, the app being removed).
        overlays.hideResult()
        scope.cancel()
        super.onDestroy()
    }

    /**
     * Opens the search card with [text], or replaces the query when the panel is already up.
     *
     * Without the overlay permission there is no floating window to have: the service says nothing
     * and leaves, and the caller's activity is the screen (see `LookupActivity`).
     *
     * The first panel waits [DOORWAY_DELAY_MS] before being drawn (R38). The doorway that started
     * this service finishes on the same main-loop turn, and its task is removed a moment later; a
     * window added in between takes the focus from the doorway and gives it back when the reader
     * returns — and a focus that goes away and does not come back is exactly what the panel reads
     * as «the user left» (R9). Waiting means the panel is born over the reader, the same way the
     * bubble's dictionary is born over whatever app is on screen. A second word, with the panel
     * already up, is answered at once.
     */
    private fun open(text: String) {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        query = text
        if (overlays.isResultVisible()) {
            showPanelNow()
            return
        }
        handler.removeCallbacks(showPanel)
        handler.postDelayed(showPanel, DOORWAY_DELAY_MS)
    }

    /** Draws the panel for [query], or leaves if no window could be created. */
    private fun showPanelNow() {
        val repository = DictionaryRepository.get(this)
        val titles = repository.enabledTitles()
        val filter = repository.resolveFilter(settings.dictionaryFilter)
        if (filter == null && settings.dictionaryFilter.isNotBlank()) settings.dictionaryFilter = ""
        val stale = repository.installed()
            .filter { it.enabled && it.info.outdated }
            .map { it.displayName }

        // showSearch asks for the query itself (the same call the field makes after a pause), so a
        // word that arrives here goes through exactly one lookup path.
        overlays.showDictionarySearch(
            titles = titles,
            selected = filter,
            dictionaryCount = titles.size,
            stale = stale,
            initialQuery = query,
            canAskAi = settings.showAiButton,
            searchByTranslation = settings.searchByTranslation,
        )
        // No window, no reason to stay: a service with no panel would be a process the user cannot
        // see or close.
        if (!overlays.isResultVisible()) stopSelf()
    }

    /** Answers [text] off the main thread; a newer keystroke cancels the answer it replaced. */
    private fun lookup(text: String) {
        query = text
        searchJob?.cancel()
        if (settings.searchByTranslation && text.isNotBlank()) {
            overlays.showResultWords(ResultOverlay.WordsState.Working(preparing = true))
        }
        searchJob = scope.launch {
            val state = searchWords(this@LookupOverlayService, text)
            if (query == text) overlays.showResultWords(state)
            if (settings.dictionaryKeepLastSearch && text.isNotBlank()) {
                settings.dictionaryLastQuery = text
            }
        }
    }

    /**
     * Hands the word to an AI app the user already has (R39).
     *
     * The same route as the bubble's panel: the word goes to the clipboard first, and then the app
     * opens with the prompt in front of it. With no app chosen, the list is drawn over the panel by
     * this app — the system chooser is the route that opened nothing on the phone this was tested on.
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
     * Opens [packageName] with the question and takes the panel down, so it does not sit over the app
     * that is about to answer. Only when an app actually opened: after a failure the panel is the only
     * thing that can still say so and still holds the list.
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

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(ClipboardManager::class.java) ?: return
        runCatching { clipboard.setPrimaryClip(ClipData.newPlainText(CLIP_LABEL, text)) }
    }

    companion object {

        /** The word another app handed over, as a plain string. */
        const val EXTRA_TEXT = "dev.yabai.ocr.extra.lookupText"

        /**
         * How long the first panel waits for the doorway's task to be removed (R38).
         *
         * The doorway finishes on the turn it receives the word; the task disappears and the reader
         * comes back a few frames later. This is a ceiling, not a schedule: it only has to be longer
         * than that hand-over, and short enough not to be felt as a delay in the lookup.
         */
        internal const val DOORWAY_DELAY_MS = 200L

        private const val CLIP_LABEL = "yabai_ocr"
    }
}
