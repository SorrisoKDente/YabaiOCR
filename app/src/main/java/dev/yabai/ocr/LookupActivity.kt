package dev.yabai.ocr

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.provider.Settings
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import dev.yabai.ocr.ai.AskAi
import dev.yabai.ocr.anki.AnkiDroidPanel
import dev.yabai.ocr.core.anki.AnkiNote
import dev.yabai.ocr.core.dict.TokenMatch
import dev.yabai.ocr.dict.DictionaryRepository
import dev.yabai.ocr.dict.searchWords
import dev.yabai.ocr.overlay.ResultOverlay
import dev.yabai.ocr.service.LookupOverlayService
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * The dictionary, opened with a word chosen in another app (R35e).
 *
 * The floating bubble needs the capture permission and a running service; a reader that hands a word
 * to another app cannot depend on either. This activity is the door for that: it receives the text,
 * opens the panel already in search mode and answers the query with the installed dictionaries — the
 * same panel, the same dictionaries, no capture and no overlay.
 *
 * Two intents are accepted, because the sender chooses: `ACTION_PROCESS_TEXT` (the «Processar texto»
 * entry of a selection menu) and `ACTION_SEND` with `text/plain` (the share sheet). ReadEra lists the
 * apps that accept text this way — the same list Google Tradutor and Takoboto appear in — so the app
 * has to answer to the same doors to be on it.
 */
class LookupActivity : AppCompatActivity() {

    private val scope = MainScope()
    private val settings by lazy { AppSettings(this) }
    private lateinit var overlay: ResultOverlay
    private lateinit var anki: AnkiDroidPanel

    /** The query the panel is on, so the dictionary chooser re-runs the right one. */
    private var searching = ""

    /** The lookup in flight; a newer keystroke cancels the answer it replaced. */
    private var searchJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppSettings(this).applyTheme(this)
        // With the overlay permission the panel floats over the reader, exactly as the bubble's
        // dictionary does (R37). The activity only carries the word: it starts the service and gets
        // out of the way, so ReadEra is not replaced by a page of ours. It lives in a task of its
        // own (manifest, R38): finishing it removes that task and the reader is what comes back;
        // had it shared the app's task, the app's main screen would be behind the panel instead.
        if (Settings.canDrawOverlays(this)) {
            deliver(incomingText(intent))
            finish()
            return
        }
        overlay = ResultOverlay(this).apply {
            callbacks = object : ResultOverlay.Callbacks {
                override fun onCopyOriginal(text: String) = copyToClipboard(text)
                override fun onCopyTranslation(text: String) = Unit
                override fun onAnalyse(text: String) = Unit
                override fun onRepick() = Unit
                override fun onRetryOffline() = Unit
                override fun onImageToggle() = Unit
                override fun onAskAi(text: String) = askAi(text)

                override fun onAnkiWord(match: TokenMatch, sentence: String) {
                    anki.send(
                        AnkiNote.word(match, sentence),
                        match.surface.ifBlank { match.entry.term },
                    )
                }

                override fun onDismiss() = finish()
                override fun onSearchChanged(text: String) = lookup(text)
                override fun onSearchModeChanged(searchByTranslation: Boolean) {
                    settings.searchByTranslation = searchByTranslation
                    lookup(searching)
                }
                override fun onDictionaryPicked(title: String?) {
                    settings.dictionaryFilter = title.orEmpty()
                    lookup(searching)
                }
            }
        }
        val host = buildHost()
        host.addView(
            overlay,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        setContentView(host)
        anki = AnkiDroidPanel(this, scope) { notice ->
            overlay.addNote(notice.text, notice.undo)
        }
        anki.retryQueued()
        // The doorway theme is transparent so the panel can replace it without a flash; this path is
        // a screen of its own (no overlay permission) and needs its own paper.
        window.setBackgroundDrawable(ColorDrawable(getColor(R.color.panel_bg)))
        openSearch(intent)
        // A activity não é uma janela flutuante: ela ocupa a tela, e num app que mira o Android 16
        // isso é desenhar de borda a borda. As barras viram padding, para o cabeçalho da busca não
        // nascer debaixo do relógio.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        ViewCompat.setOnApplyWindowInsetsListener(host) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        // O campo já tem o foco; o teclado é o gesto seguinte, e no painel flutuante quem o abre é o
        // serviço. Aqui não há serviço para pedir.
        overlay.post { overlay.showKeyboard() }
    }

    /**
     * The host that keeps the card off the system bars — the panel itself is the only child.
     *
     * It exists as a view of its own because the insets belong to the screen, not to the card: the
     * panel is reused unchanged from the floating window, where it never touches the bars.
     */
    private fun buildHost(): FrameLayout = FrameLayout(this).apply {
        tag = HOST_TAG
        setBackgroundColor(getColor(R.color.panel_bg))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (!::overlay.isInitialized) {
            // The screen was only a doorway: a second word opens the panel again, not a page.
            deliver(incomingText(intent))
            finish()
            return
        }
        openSearch(intent)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    /** Hands [text] to the floating panel; the activity is finished by the caller. */
    private fun deliver(text: String) {
        startService(
            Intent(this, LookupOverlayService::class.java)
                .putExtra(LookupOverlayService.EXTRA_TEXT, text),
        )
    }

    /**
     * Opens the search card with the text of [intent].
     *
     * The panel runs the query itself from the field, exactly as it does behind the bubble: one path
     * for «the text in the box changed», whether it came from an intent or from a keystroke.
     */
    private fun openSearch(intent: Intent?) {
        val repository = DictionaryRepository.get(this)
        val titles = repository.enabledTitles()
        val filter = repository.resolveFilter(settings.dictionaryFilter)
        if (filter == null && settings.dictionaryFilter.isNotBlank()) settings.dictionaryFilter = ""
        val stale = repository.installed()
            .filter { it.enabled && it.info.outdated }
            .map { it.displayName }
        overlay.showSearch(
            titles = titles,
            selected = filter,
            dictionaryCount = titles.size,
            stale = stale,
            initialQuery = incomingText(intent),
            canAskAi = settings.showAiButton,
            canAnki = settings.showAnkiButton,
            searchByTranslation = settings.searchByTranslation,
        )
    }

    /** Answers [query] off the main thread; the field keeps the text as it was received. */
    private fun lookup(query: String) {
        searching = query
        searchJob?.cancel()
        if (settings.searchByTranslation && query.isNotBlank() && ::overlay.isInitialized) {
            overlay.setWords(ResultOverlay.WordsState.Working(preparing = true))
        }
        searchJob = scope.launch {
            val state = searchWords(this@LookupActivity, query)
            if (searching == query) overlay.setWords(state)
            if (settings.dictionaryKeepLastSearch && query.isNotBlank()) {
                settings.dictionaryLastQuery = query
            }
        }
    }

    /**
     * Hands the word to an AI app (R39).
     *
     * This screen is the path without the overlay permission, so there is no floating panel to draw
     * the list of apps in. With an app fixed in the settings it opens directly, as it does behind the
     * bubble; without one the system chooser does the choosing — the degraded route, in the screen
     * that is itself the degraded one.
     */
    private fun askAi(text: String) {
        val payload = AskAi.request(settings.aiPrompt, text)
        if (payload.isBlank()) return
        copyToClipboard(text)
        val opened = when (val plan = AskAi.plan(settings.aiPackage, AskAi.targets(this))) {
            is AskAi.Plan.Direct -> start(AskAi.sendIntent(payload, plan.packageName))
            is AskAi.Plan.Choose -> start(
                Intent.createChooser(
                    AskAi.sendIntent(payload, null),
                    getString(R.string.result_ask_ai_choose),
                ),
            )
            AskAi.Plan.Nothing -> false
        }
        if (!opened) {
            Toast.makeText(this, R.string.result_ai_no_app, Toast.LENGTH_SHORT).show()
            return
        }
        // The screen was only the way to the question: closing it leaves the AI app in front.
        finish()
    }

    private fun start(intent: Intent): Boolean = runCatching { startActivity(intent) }.isSuccess

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(ClipboardManager::class.java) ?: return
        runCatching { clipboard.setPrimaryClip(ClipData.newPlainText(CLIP_LABEL, text)) }
    }

    companion object {

        /** The screen host, so a test can find the view that keeps the card off the bars. */
        const val HOST_TAG = "yabai.lookup.host"

        /** The label the clipboard entry carries, the same one the floating panel uses. */
        private const val CLIP_LABEL = "yabai_ocr"

        /**
         * The text another app sent, from either accepted intent.
         *
         * Empty when there is nothing to look up: the screen still opens as a search box, which is
         * better than a blank activity that cannot be used for anything.
         */
        fun incomingText(intent: Intent?): String {
            val raw = when (intent?.action) {
                Intent.ACTION_PROCESS_TEXT -> intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)
                Intent.ACTION_SEND -> intent.getCharSequenceExtra(Intent.EXTRA_TEXT)
                else -> null
            }
            return raw?.toString()?.trim().orEmpty()
        }

        /** The intent a test — or another screen — uses to open a word here. */
        fun intent(context: Context, text: String): Intent =
            Intent(context, LookupActivity::class.java).apply {
                action = Intent.ACTION_PROCESS_TEXT
                type = "text/plain"
                putExtra(Intent.EXTRA_PROCESS_TEXT, text)
            }
    }
}
