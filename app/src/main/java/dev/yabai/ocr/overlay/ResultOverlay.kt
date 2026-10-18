package dev.yabai.ocr.overlay

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.View.MeasureSpec
import android.view.ViewGroup.MarginLayoutParams
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import dev.yabai.ocr.R
import dev.yabai.ocr.core.dict.KanjiEntry
import dev.yabai.ocr.core.dict.TokenMatch
import dev.yabai.ocr.util.dp

/**
 * The panel that shows the recognised text, its translation and the dictionary breakdown.
 *
 * Three things shape this layout:
 *
 *  * the recognised text is an [EditText], not a label. OCR gets kanji wrong, and when it does the
 *    user usually knows exactly which character it should have been; without an editable field the
 *    only option would be to capture the screen again and hope.
 *  * the card hangs from the top. The on-screen keyboard occupies the bottom half, so an editor at
 *    the bottom would be typed into blind.
 *  * the window is only as tall as the card. Touches below it fall through to the app underneath,
 *    so the floating bubble stays reachable while a result is open.
 */
class ResultOverlay(context: Context) : FrameLayout(context) {

    interface Callbacks {
        fun onCopyOriginal(text: String)
        fun onCopyTranslation(text: String)

        /** Re-runs dictionary lookup and translation for the (possibly corrected) text. */
        fun onAnalyse(text: String)

        /** Goes back to the boxes drawn over the screen, to pick a different piece of the capture. */
        fun onRepick()
        fun onRetryOffline()
        fun onDismiss()
    }

    var callbacks: Callbacks? = null

    /** Translation section state. */
    sealed interface TranslationState {
        data object Hidden : TranslationState
        data object Working : TranslationState
        data class Done(val text: String) : TranslationState
        data class Failed(val message: String) : TranslationState
    }

    /** Dictionary section state. */
    sealed interface WordsState {
        data object Hidden : WordsState
        data object Working : WordsState
        data class Done(
            val matches: List<TokenMatch>,
            val kanji: List<KanjiEntry>,
            val dictionaryCount: Int,
        ) : WordsState

        data class Failed(val message: String) : WordsState
    }

    private val card = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = roundedBackground(context.getColor(R.color.panel_bg), dp(18).toFloat(), 0x33FFFFFF)
        setPadding(dp(16), dp(14), dp(16), dp(12))
        elevation = dp(12).toFloat()
    }

    private val notesContainer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val translationSection = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val wordsSection = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

    private val originalEditor = EditText(context).apply {
        background = roundedBackground(0x14FFFFFF, dp(10).toFloat(), 0x22FFFFFF)
        setTextColor(context.getColor(R.color.text_primary))
        setHintTextColor(context.getColor(R.color.text_secondary))
        setHint(R.string.result_original_hint)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        isSingleLine = false
        maxLines = 8
        gravity = Gravity.TOP or Gravity.START
        setPadding(dp(10), dp(10), dp(10), dp(10))
        setLineSpacing(dp(4).toFloat(), 1f)
    }

    private val btCopyOriginal = context.actionButton(context.getString(R.string.result_copy_original)) {
        callbacks?.onCopyOriginal(originalEditor.text.toString())
    }
    private val btCopyTranslation = context.actionButton(context.getString(R.string.result_copy_translation)) {
        val text = translationText
        if (text.isNullOrBlank()) return@actionButton
        callbacks?.onCopyTranslation(text)
    }

    private var translationText: String? = null

    /**
     * The text the sections on screen describe. Kept so that an edit which ends where it started does
     * not buy a second, identical lookup.
     */
    private var lastAnalysedText: String = ""

    private var showLines: Boolean = false
    private var segmentLines: List<String> = emptyList()

    private val analyseHandler = Handler(Looper.getMainLooper())

    /**
     * Re-runs dictionary and translation once the user stops editing.
     *
     * A corrected kanji is only useful if it gets looked up again, and the panel is the only part of
     * the app that knows when typing stopped. The delay keeps a sentence from being analysed on every
     * keystroke; the comparison keeps an edit that ends where it began from buying the same answer
     * twice.
     */
    private val analyseSoon = Runnable {
        val text = originalEditor.text.toString()
        if (text.trim() != lastAnalysedText.trim()) {
            lastAnalysedText = text
            callbacks?.onAnalyse(text)
        }
    }

    /** The scrolling middle section of the current card, excluded from the chrome measurement. */
    private var scroller: MaxHeightScrollView? = null

    init {
        addView(
            card,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.TOP).apply {
                marginStart = dp(8)
                marginEnd = dp(8)
                topMargin = dp(6)
            },
        )

        // Focusable so text can be edited and BACK can close the panel instead of leaking through.
        isFocusableInTouchMode = true
        setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                callbacks?.onDismiss()
                true
            } else {
                false
            }
        }

        originalEditor.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                // Everything on screen was derived from the old text, so it is out of date now. Wait
                // for a pause before asking again: a lookup per keystroke would thrash both the
                // dictionary and the on-device translator.
                analyseHandler.removeCallbacks(analyseSoon)
                analyseHandler.postDelayed(analyseSoon, ANALYSE_DELAY_MS)
            }
        })
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        // The panel is taken out of the window whenever it goes away, so this is also the dismissal
        // path: nothing should be looked up for a panel the user has already closed.
        analyseHandler.removeCallbacks(analyseSoon)
    }

    // --- entry points --------------------------------------------------------------------------

    fun showLoading() {
        card.removeAllViews()
        translationText = null
        card.addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(10), 0, dp(10))
                addView(ProgressBar(context).apply { isIndeterminate = true })
                addView(
                    textLabel(context.getString(R.string.recognizing), 16f, context.getColor(R.color.text_primary))
                        .apply { setPadding(dp(14), 0, 0, 0) },
                )
            },
            LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT),
        )
    }

    /**
     * Builds the panel for a finished capture. Subsequent section updates ([setTranslation],
     * [setWords]) only touch their own sub-view, so a lookup that lands while the user is editing
     * will not yank the cursor out from under them.
     */
    fun showResult(
        text: String,
        meta: String,
        notes: List<String>,
        offlineAvailable: Boolean,
        showLines: Boolean,
        segmentLines: List<String>,
        canRepick: Boolean,
    ) {
        card.removeAllViews()
        this.showLines = showLines
        this.segmentLines = segmentLines
        translationText = null
        lastAnalysedText = text

        card.addView(buildHeader(meta))

        notesContainer.removeAllViews()
        for (note in notes) notesContainer.addView(noteView(note))
        if (notes.isNotEmpty()) card.addView(notesContainer)

        card.addView(
            textLabel(context.getString(R.string.result_original_label), 12f, context.getColor(R.color.text_secondary))
                .apply { setPadding(dp(2), dp(8), dp(2), dp(4)) },
        )

        originalEditor.setText(text)
        originalEditor.setSelection(originalEditor.text.length)
        originalEditor.setBackground(
            roundedBackground(0x14FFFFFF, dp(10).toFloat(), 0x22FFFFFF),
        )
        card.addView(
            originalEditor,
            LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT),
        )


        translationSection.removeAllViews()
        wordsSection.removeAllViews()

        val scrolling = MaxHeightScrollView(context, ::freeHeightForScroller).apply {
            addView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    if (showLines && segmentLines.isNotEmpty()) {
                        addView(
                            textLabel(segmentLines.joinToString("\n"), 12f, context.getColor(R.color.text_secondary))
                                .apply { setPadding(dp(2), dp(6), dp(2), 0) },
                        )
                    }
                    addView(translationSection)
                    addView(wordsSection)
                },
            )
        }
        scroller = scrolling
        card.addView(scrolling, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        val actions = FlowLayout(context).apply {
            alignEnd = true
            setPadding(0, dp(8), 0, 0)
        }
        if (canRepick) {
            // Only offered for a whole-screen capture: it reopens the boxes without paying for
            // another Lens call, which matters because those are rate limited.
            actions.addView(
                context.actionButton(
                    context.getString(R.string.result_repick),
                ) { callbacks?.onRepick() },
                wrapWithMargin(start = 0),
            )
        }
        if (offlineAvailable) {
            actions.addView(
                context.actionButton(context.getString(R.string.result_retry_offline)) { callbacks?.onRetryOffline() },
                wrapWithMargin(),
            )
        }
        actions.addView(
            context.actionButton(context.getString(R.string.result_close)) { callbacks?.onDismiss() },
            wrapWithMargin(),
        )
        card.addView(actions)

        val copies = FlowLayout(context).apply {
            alignEnd = true
            setPadding(0, dp(6), 0, 0)
        }
        copies.addView(btCopyOriginal, wrapWithMargin(start = 0))
        btCopyTranslation.visibility = View.GONE
        copies.addView(btCopyTranslation, wrapWithMargin())
        card.addView(copies)
    }

    fun showError(message: String, offlineAvailable: Boolean) {
        card.removeAllViews()
        translationText = null
        card.addView(
            textLabel(message, 16f, context.getColor(R.color.text_primary))
                .apply { setPadding(dp(2), dp(6), dp(2), dp(12)) },
        )
        val buttons = FlowLayout(context).apply { alignEnd = true }
        if (offlineAvailable) {
            buttons.addView(
                context.actionButton(context.getString(R.string.result_retry_offline)) { callbacks?.onRetryOffline() },
                wrapWithMargin(start = 0),
            )
        }
        buttons.addView(
            context.actionButton(context.getString(R.string.result_close)) { callbacks?.onDismiss() },
            wrapWithMargin(if (offlineAvailable) dp(6) else 0),
        )
        card.addView(buttons)
    }

    /** The text the user is looking at, edited or not. */
    fun currentText(): String = originalEditor.text.toString()

    // --- sections ------------------------------------------------------------------------------

    fun setTranslation(state: TranslationState) {
        translationSection.removeAllViews()
        btCopyTranslation.visibility = View.GONE
        translationText = null

        when (state) {
            is TranslationState.Hidden -> Unit
            is TranslationState.Working -> translationSection.addView(busyRow(context.getString(R.string.translate_working)))
            is TranslationState.Failed -> translationSection.addView(noteView(state.message))
            is TranslationState.Done -> {
                if (state.text.isBlank()) return
                translationText = state.text
                translationSection.addView(sectionTitle(context.getString(R.string.result_translation_label)))
                translationSection.addView(
                    textLabel(state.text, 17f, context.getColor(R.color.text_primary)).apply {
                        setTextIsSelectable(true)
                        setLineSpacing(dp(4).toFloat(), 1f)
                        setPadding(dp(2), dp(4), dp(2), dp(10))
                    },
                )
                btCopyTranslation.visibility = View.VISIBLE
            }
        }
    }

    fun setWords(state: WordsState) {
        wordsSection.removeAllViews()
        when (state) {
            is WordsState.Hidden -> Unit
            is WordsState.Working -> wordsSection.addView(busyRow(context.getString(R.string.dictionary_working)))
            is WordsState.Failed -> {
                wordsSection.addView(sectionTitle(context.getString(R.string.result_dictionary_label)))
                wordsSection.addView(noteView(state.message))
            }

            is WordsState.Done -> renderWords(state)
        }
    }

    private fun renderWords(state: WordsState.Done) {
        if (state.dictionaryCount == 0) {
            wordsSection.addView(sectionTitle(context.getString(R.string.result_dictionary_label)))
            wordsSection.addView(noteView(context.getString(R.string.dictionary_none_installed)))
            return
        }

        if (state.kanji.isNotEmpty()) {
            wordsSection.addView(sectionTitle(context.getString(R.string.result_kanji_label)))
            for (kanji in state.kanji) {
                wordsSection.addView(kanjiView(kanji))
            }
        }

        if (state.matches.isEmpty()) {
            wordsSection.addView(sectionTitle(context.getString(R.string.result_dictionary_label)))
            wordsSection.addView(noteView(context.getString(R.string.dictionary_no_match)))
            return
        }

        val shown = state.matches.take(MAX_WORDS)
        wordsSection.addView(
            sectionTitle(
                context.getString(R.string.result_dictionary_label) +
                    "  " + context.getString(R.string.dictionary_word_count, state.matches.size),
            ),
        )
        for (match in shown) {
            wordsSection.addView(wordView(match))
        }
        if (state.matches.size > shown.size) {
            wordsSection.addView(
                noteView(context.getString(R.string.dictionary_truncated, shown.size, state.matches.size)),
            )
        }
    }

    private fun wordView(match: TokenMatch): View {
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(2))
        }

        val head = buildString {
            if (match.surface != match.entry.term) {
                append(match.surface).append(" → ")
            }
            append(match.entry.term)
            if (match.entry.reading.isNotBlank() && match.entry.reading != match.entry.term) {
                append("  【").append(match.entry.reading).append("】")
            }
        }
        column.addView(textLabel(head, 16f, context.getColor(R.color.text_primary), bold = true))

        val details = buildList {
            if (match.reasons.isNotEmpty()) add(match.reasons.joinToString(" + ") { reasonLabel(it) })
            add(match.dictionaryTitle)
        }.joinToString(" · ")
        column.addView(
            textLabel(details, 11f, context.getColor(R.color.text_secondary))
                .apply { setPadding(0, dp(1), 0, dp(3)) },
        )

        val definition = if (match.entry.definition.length > MAX_DEFINITION_CHARS) {
            match.entry.definition.take(MAX_DEFINITION_CHARS).trimEnd() + "…"
        } else {
            match.entry.definition
        }
        column.addView(
            textLabel(definition, 14f, context.getColor(R.color.text_primary)).apply {
                setTextIsSelectable(true)
                setLineSpacing(dp(3).toFloat(), 1f)
                setPadding(dp(10), 0, 0, 0)
            },
        )
        return column
    }

    private fun kanjiView(kanji: KanjiEntry): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, dp(6), 0, dp(2))
        addView(textLabel(kanji.character, 26f, context.getColor(R.color.accent), bold = true))
        addView(
            textLabel(kanji.body, 14f, context.getColor(R.color.text_primary)).apply {
                setTextIsSelectable(true)
                setLineSpacing(dp(3).toFloat(), 1f)
            },
        )
    }

    // --- small pieces --------------------------------------------------------------------------

    private fun buildHeader(meta: String): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(
            textLabel(context.getString(R.string.result_title), 17f, context.getColor(R.color.text_primary), bold = true),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )
        addView(textLabel(meta, 12f, context.getColor(R.color.text_secondary)))
        addView(
            context.actionButton("✕", background = 0x00000000) { callbacks?.onDismiss() }
                .apply { setPadding(dp(10), dp(4), dp(4), dp(4)) },
        )
    }

    private fun sectionTitle(text: String): TextView =
        textLabel(text, 12f, context.getColor(R.color.accent), bold = true).apply {
            setPadding(dp(2), dp(12), dp(2), dp(2))
        }

    private fun busyRow(text: String): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(2), dp(10), dp(2), dp(2))
        addView(ProgressBar(context).apply { isIndeterminate = true })
        addView(
            textLabel(text, 13f, context.getColor(R.color.text_secondary)).apply { setPadding(dp(10), 0, 0, 0) },
        )
    }

    private fun noteView(message: String): TextView =
        textLabel(message, 12f, context.getColor(R.color.text_secondary)).apply {
            setPadding(dp(2), dp(6), dp(2), 0)
        }

    private fun wrapWithMargin(start: Int = dp(6)): MarginLayoutParams =
        MarginLayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT,
        ).apply { marginStart = start }

    /**
     * How tall the scrolling middle section may get: whatever the screen has left once the parts of
     * the card that cannot scroll have taken their share.
     *
     * This used to be a constant guess (`FIXED_CHROME_DP`) at the height of the header, the editor
     * and the two button rows. A guess goes stale the moment anything about the card changes: a long
     * OCR result makes the editor taller, and a narrow screen makes the button row wrap onto a second
     * line. The symptom is always the same -- the copy buttons end up past the bottom of the screen,
     * where nothing can reach them. Measuring the neighbours costs one extra pass and cannot go
     * stale. [width] is the width the scroller itself was handed, which is the card's inner width.
     */
    private fun freeHeightForScroller(width: Int): Int {
        val available = context.resources.displayMetrics.heightPixels -
            systemBarHeight("status_bar_height", fallbackDp = 24) -
            systemBarHeight("navigation_bar_height", fallbackDp = 48) -
            dp(CARD_TOP_MARGIN_DP)

        var chrome = card.paddingTop + card.paddingBottom
        for (index in 0 until card.childCount) {
            val child = card.getChildAt(index)
            // Skipped: the scroller, whose budget this is, and anything the user cannot see.
            if (child === scroller || child.visibility != View.VISIBLE) continue
            val params = child.layoutParams as? MarginLayoutParams
            child.measure(
                MeasureSpec.makeMeasureSpec(
                    width,
                    if (params?.width == LayoutParams.MATCH_PARENT) MeasureSpec.EXACTLY else MeasureSpec.AT_MOST,
                ),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
            )
            chrome += child.measuredHeight + (params?.topMargin ?: 0) + (params?.bottomMargin ?: 0)
        }

        return (available - chrome).coerceAtLeast(dp(MIN_CONTENT_DP))
    }

    private fun systemBarHeight(name: String, fallbackDp: Int): Int {
        val id = context.resources.getIdentifier(name, "dimen", "android")
        return if (id > 0) context.resources.getDimensionPixelSize(id) else dp(fallbackDp)
    }

    /**
     * A [ScrollView] that takes at most `cap` pixels of height, asked afresh on every measure pass.
     *
     * The cap has to be a function rather than a number: how much room is left for the scrolling
     * section depends on how tall the parts that cannot scroll turned out to be, and those are only
     * known once they have been measured, which happens while this view is being measured.
     */
    private class MaxHeightScrollView(
        context: Context,
        private val cap: (width: Int) -> Int,
    ) : ScrollView(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val limit = MeasureSpec.makeMeasureSpec(cap(MeasureSpec.getSize(widthMeasureSpec)), MeasureSpec.AT_MOST)
            super.onMeasure(widthMeasureSpec, limit)
        }
    }

    companion object {
        /** Enough for a page of manga; beyond this the panel is just a wall of text. */
        private const val MAX_WORDS = 60
        private const val MAX_DEFINITION_CHARS = 400

        /** The card is inset from the top of its window; the height budget has to account for it. */
        private const val CARD_TOP_MARGIN_DP = 6

        /** Never squeeze the scrolling section to nothing: a couple of lines always stay visible. */
        private const val MIN_CONTENT_DP = 96

        /**
         * How long the panel waits after the last keystroke before looking the corrected text up
         * again. Long enough that typing a sentence does not start a lookup per character, short
         * enough that it feels like a reaction to the edit.
         */
        private const val ANALYSE_DELAY_MS = 800L

        /**
         * The rule table names inflections in English ("polite past"). Naming them in the language
         * of the rest of the screen costs one map and makes the arrow line readable.
         */
        private val REASON_LABELS = mapOf(
            "polite" to "polido",
            "polite past" to "passado polido",
            "polite negative" to "negativo polido",
            "polite volitional" to "volitivo polido",
            "past" to "passado",
            "negative" to "negativo",
            "imperative" to "imperativo",
            "imperative negative" to "ordem negativa",
            "potential" to "potencial",
            "potential or passive" to "potencial/passiva",
            "passive" to "passiva",
            "causative" to "causativa",
            "causative passive" to "causativa passiva",
            "volitional" to "volitivo",
            "te" to "forma -te",
            "progressive or perfect" to "progressivo",
            "masu stem" to "radical de -masu",
            "noun" to "substantivo",
            "adv" to "advérbio",
            "-ba" to "condicional -ba",
            "-tara" to "condicional -tara",
            "-tari" to "alternância -tari",
            "-tai" to "querer -tai",
            "-sou" to "aparência -sou",
            "-sugiru" to "excesso -sugiru",
            "-nasai" to "ordem -nasai",
            "-zu" to "sem -zu",
            "-nu" to "negativo -nu",
            "-chau" to "contração -chau",
            "-shimau" to "completivo -shimau",
            "-toku" to "contração -toku",
            "-chimau" to "contração -chimau",
            "-ki" to "atributivo -ki",
            "-ge" to "aparência -ge",
            "-e" to "potencial -e",
            "adj-i" to "adjetivo -i",
        )

        fun reasonLabel(reason: String): String = REASON_LABELS[reason] ?: reason
    }
}
