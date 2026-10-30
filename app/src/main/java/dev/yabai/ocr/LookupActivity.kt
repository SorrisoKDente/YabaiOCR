package dev.yabai.ocr

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import dev.yabai.ocr.dict.DictionaryRepository
import dev.yabai.ocr.overlay.ResultOverlay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    /** The query the panel is on, so the dictionary chooser re-runs the right one. */
    private var searching = ""

    /** The lookup in flight; a newer keystroke cancels the answer it replaced. */
    private var searchJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overlay = ResultOverlay(this).apply {
            callbacks = object : ResultOverlay.Callbacks {
                override fun onCopyOriginal(text: String) = Unit
                override fun onCopyTranslation(text: String) = Unit
                override fun onAnalyse(text: String) = Unit
                override fun onRepick() = Unit
                override fun onRetryOffline() = Unit
                override fun onImageToggle() = Unit
                override fun onAskAi(text: String) = Unit
                override fun onDismiss() = finish()
                override fun onSearchChanged(text: String) = lookup(text)
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
        openSearch(intent)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
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
        )
    }

    /** Answers [query] off the main thread; the field keeps the text as it was received. */
    private fun lookup(query: String) {
        searching = query
        searchJob?.cancel()
        searchJob = scope.launch {
            val state = withContext(Dispatchers.Default) {
                val repository = DictionaryRepository.get(this@LookupActivity)
                val engine = repository.lookupEngine()
                val titles = repository.enabledTitles()
                val stale = repository.installed()
                    .filter { it.enabled && it.info.outdated }
                    .map { it.displayName }
                val filter = repository.resolveFilter(settings.dictionaryFilter)
                val only = filter?.let { setOf(it) }.orEmpty()
                val text = settings.dictionaryQuery(query.trim())
                if (engine == null || text.isEmpty()) {
                    ResultOverlay.WordsState.Done(
                        matches = emptyList(),
                        kanji = emptyList(),
                        dictionaryCount = titles.size,
                        titles = titles,
                        selected = filter,
                        stale = stale,
                        query = query,
                    )
                } else {
                    ResultOverlay.WordsState.Done(
                        matches = engine.search(text, only = only),
                        kanji = engine.kanjiBreakdown(text, only = only),
                        dictionaryCount = titles.size,
                        titles = titles,
                        selected = filter,
                        stale = stale,
                        query = query,
                        suggestions = engine.suggest(text, only = only),
                    )
                }
            }
            if (searching == query) overlay.setWords(state)
        }
    }

    companion object {

        /** The screen host, so a test can find the view that keeps the card off the bars. */
        const val HOST_TAG = "yabai.lookup.host"

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
