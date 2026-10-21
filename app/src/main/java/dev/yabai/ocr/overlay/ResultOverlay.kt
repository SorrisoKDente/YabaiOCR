package dev.yabai.ocr.overlay

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.text.style.LeadingMarginSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.View.MeasureSpec
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import dev.yabai.ocr.R
import dev.yabai.ocr.core.dict.Furigana
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
 *  * the panel owns the whole screen but is only drawn as a card at the top. The empty area is what
 *    BACK means, physically: a tap there steps out of the panel (to the boxes of a whole-screen
 *    capture, or out of it when there is nothing behind). That is also why the card itself swallows
 *    its own taps.
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

        /** Shows or hides the captured image beside the text; the choice is remembered. */
        fun onImageToggle()

        /**
         * The user picked a dictionary in the panel, or asked for all of them again ([title] null).
         * The choice is a setting, and the lookup runs again with it.
         */
        fun onDictionaryPicked(title: String?)

        /**
         * The user wants an AI app to answer about the recognised text. The text is what is on screen,
         * corrections included, and what happens to it — clipboard, prompt, which app — is the app's
         * business, not the panel's.
         */
        fun onAskAi(text: String)
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
            /** Titles of the enabled dictionaries, in priority order; the selector offers these. */
            val titles: List<String> = emptyList(),
            /** The one dictionary the lookup was narrowed to, or null for all of them. */
            val selected: String? = null,
            /**
             * The sentence with a reading after every kanji the dictionaries can account for, or
             * null when there is nothing to write (or the setting is off). The panel writes it into
             * the recognised text itself, beside the kanji it reads.
             */
            val furigana: Furigana.Annotated? = null,
        ) : WordsState {
            /** One dictionary is nothing to choose between, so the selector stays out of the way. */
            val pickable: Boolean get() = titles.size > 1
        }

        data class Failed(val message: String) : WordsState
    }

    private val card = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = roundedBackground(context.getColor(R.color.panel_bg), dp(18).toFloat(), 0x33FFFFFF)
        setPadding(dp(16), dp(14), dp(16), dp(12))
        elevation = dp(12).toFloat()
        // The card takes its own taps so that the root's «outside» listener never sees one. Without
        // it, a tap on the card's padding would read as a tap outside it.
        isClickable = true
    }

    /**
     * Whether there is a step behind the panel: the boxes of a whole-screen capture, which can be
     * shown again without paying for another Lens call. BACK and a tap outside the card go there when
     * there is one, and close the panel when there is not.
     */
    private var canGoBack = false

    private val notesContainer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

    /**
     * The notes already written in [notesContainer], so a note that is added twice — a button pressed
     * twice while the first answer is still on screen — is written once.
     */
    private val noteTexts = mutableListOf<String>()
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
     * The text the editor was last given by the panel itself — the recognised text, or that text with
     * the readings written into it. Kept so that a change the panel made (writing a reading, reverting
     * one) and an edit that ends where it started do not buy a second, identical lookup.
     */
    private var lastShownText: String = ""

    /**
     * The text the user last sent to be looked up, without the readings the panel writes. Writing the
     * readings into the editor must not make the dictionary read its own answer back.
     */
    private var lastAnalysedText: String = ""

    /**
     * The recognised text as it arrived, before any reading was written into it. It is what the panel
     * shows again when a later lookup has nothing to read.
     */
    private var cleanText: String = ""

    /** True once the user has typed in the editor: from then on the panel does not touch the text. */
    private var userEdited: Boolean = false

    /** True while the panel is writing into the editor, so its own change is not read as the user's. */
    private var writingText: Boolean = false

    private var showLines: Boolean = false
    private var segmentLines: List<String> = emptyList()

    /**
     * Sections the user folded away, by key rather than by title: the dictionary title carries a word
     * count that changes with every re-analysis, so it cannot be the name of a remembered choice.
     */
    private val foldedSections = mutableSetOf<String>()

    /** Whether the whole card is folded into a strip that keeps showing the answer. */
    private var minimised = false

    /** The one row a minimised card keeps, and therefore the anchor for folding it. */
    private var headerRow: View? = null

    /** Kept because [rebuildHeader] has to rebuild the header without the original arguments. */
    private var lastMeta: String = ""

    /**
     * The frame the text was recognised from, and whether the user wants it on screen.
     *
     * The bitmap is not owned here — the service keeps it for the whole life of the result and drops
     * it when the panel closes — so this is only a reference to draw, never something to recycle.
     */
    private var image: Bitmap? = null
    private var imageShown = false

    /** The row holding the image and the editor, so it can be rebuilt in place when the image toggles. */
    private var textRow: View? = null

    /** The toggle itself, so its «on» colour can follow the layout. */
    private var imageButton: TextView? = null

    /** Whether the dictionary chooser is unfolded under the section title. */
    private var pickerOpen = false

    /**
     * The dictionary answer the chooser was opened from, so closing the chooser can redraw the very
     * same words instead of asking the app to look them up again (R16).
     */
    private var wordsState: WordsState.Done? = null

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
        if (text.trim() != lastShownText.trim()) {
            lastShownText = text
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

        // Focusable so text can be edited and BACK can step out of the panel instead of leaking
        // through to the app underneath. The empty area of the window belongs to the panel too, so a
        // tap there is the same step; the window used to be only as tall as the card, and the gesture
        // existed nowhere.
        isFocusableInTouchMode = true
        isClickable = true
        setOnClickListener { stepBack() }

        originalEditor.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                // A reading the panel wrote is not a correction: asking the dictionary to read its
                // own answer back would double every reading and never settle.
                if (writingText) return
                userEdited = true
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

    /**
     * Called when the window stops being the one with focus — HOME, the recents screen, another app
     * coming to the front (R9).
     *
     * The panel is not part of any task: pressing HOME sends the reader app behind it to the background
     * and leaves the panel floating over the launcher until something tells it to go. The focus is the
     * cheapest such signal — no new permission, no accessibility service — and it exists because the
     * window is focusable anyway, for the editable text and for BACK.
     */
    var onWindowLost: (() -> Unit)? = null

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        // Gaining focus is the panel's normal state; only losing it means the user went elsewhere.
        if (!hasWindowFocus) onWindowLost?.invoke()
    }

    /**
     * BACK steps out of the panel, one step at a time, instead of leaking to the app underneath —
     * which is why the window is focusable in the first place. Overriding the dispatch, rather than
     * hanging a key listener on the view, keeps the step where it can be tested: a key listener on a
     * `ViewGroup` is not delivered by Robolectric, and the back button is exactly the gesture this
     * has to answer to (R16).
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
            stepBack()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    /**
     * One step out of the panel, asked for by BACK or by a tap outside the card.
     *
     * The panel is not always the last thing on screen: after a whole-screen capture the boxes are
     * still one tap away, and that is where the user came from. Closing the panel there would throw
     * the capture away and cost another Lens call to get it back, so BACK goes to «◀ Trechos». With
     * nothing behind the panel — a region capture — the same gesture is «Fechar».
     *
     * The dictionary chooser is one more step: it is a list over the panel, not a place of its own,
     * so BACK — or a tap outside the card, which is the same step — closes the list and leaves the
     * panel, and the words, exactly as they were (R16).
     */
    private fun stepBack() {
        if (pickerOpen) {
            pickerOpen = false
            wordsState?.let { renderWords(it) }
            return
        }
        if (canGoBack) callbacks?.onRepick() else callbacks?.onDismiss()
    }

    // --- entry points --------------------------------------------------------------------------

    fun showLoading() {
        card.removeAllViews()
        translationText = null
        // A spinner is not a result: there is no text behind it to go back to.
        canGoBack = false
        // No header in this card, so nothing can be folded: leave both the state and the stale view
        // reference of a previous panel behind rather than folding this spinner away.
        headerRow = null
        minimised = false
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
     *
     * [canAskAi] puts the «Perguntar à IA» button in the row of copy buttons, where it belongs: it is
     * one more way of taking the recognised text away from the panel.
     */
    fun showResult(
        text: String,
        meta: String,
        notes: List<String>,
        offlineAvailable: Boolean,
        showLines: Boolean,
        segmentLines: List<String>,
        canRepick: Boolean,
        image: Bitmap? = null,
        showImage: Boolean = false,
        canAskAi: Boolean = false,
    ) {
        card.removeAllViews()
        this.showLines = showLines
        this.segmentLines = segmentLines
        translationText = null
        lastAnalysedText = text
        lastShownText = text
        cleanText = text
        userEdited = false
        canGoBack = canRepick

        // A new capture opens unfolded. Folding is about the answer already in front of the user, and
        // a panel that came up collapsed would hide the very thing they just asked for.
        minimised = false
        foldedSections.clear()
        headerRow = null

        this.image = image
        // Showing a picture that is not there is not a state the panel can be in.
        imageShown = showImage && image != null
        textRow = null
        imageButton = null
        pickerOpen = false
        wordsState = null

        // Before the header: a folded header carries the text itself.
        showText(text)
        originalEditor.setBackground(
            roundedBackground(0x14FFFFFF, dp(10).toFloat(), 0x22FFFFFF),
        )

        card.addView(buildHeader(meta))

        notesContainer.removeAllViews()
        noteTexts.clear()
        for (note in notes) {
            notesContainer.addView(noteView(note))
            noteTexts += note
        }
        if (notes.isNotEmpty()) card.addView(notesContainer)

        card.addView(
            textLabel(context.getString(R.string.result_original_label), 12f, context.getColor(R.color.text_secondary))
                .apply { setPadding(dp(2), dp(8), dp(2), dp(4)) },
        )

        card.addView(
            buildTextRow(),
            LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT),
        )


        // These views outlive the card they were put in — they are fields, not fresh views — and a
        // previous card may still hold them. Adding a view that already has a parent is an error, not
        // a move, so a second capture on the same panel would fail without this.
        for (view in listOf(translationSection, wordsSection, btCopyOriginal, btCopyTranslation)) {
            (view.parent as? ViewGroup)?.removeView(view)
        }
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

        // One grid for every action, outputs first: the two «Copiar» and the question are what the
        // panel is for, and the tools that change the capture come after them. See [ActionGrid].
        val actions = ActionGrid(context).apply { setPadding(0, dp(10), 0, 0) }
        btCopyTranslation.visibility = View.GONE
        actions.add(btCopyOriginal)
        actions.add(btCopyTranslation)
        if (canAskAi) {
            // Right after the copies: it is the answer to the whole sentence, where the two «Copiar»
            // buttons are the answer to a piece of it.
            actions.add(
                context.actionButton(context.getString(R.string.result_ask_ai)) {
                    callbacks?.onAskAi(originalEditor.text.toString())
                },
            )
        }
        actions.add(
            context.actionButton(context.getString(R.string.result_close)) { callbacks?.onDismiss() },
        )
        if (image != null) {
            // A toggle: what it does is visible in the layout the moment it is pressed, and the choice
            // is written to the settings so the next panel opens the way the user left this one.
            imageButton = context.actionButton(
                context.getString(R.string.result_image),
                background = imageButtonBackground(),
            ) { callbacks?.onImageToggle() }
            actions.add(imageButton!!)
        }
        if (canRepick) {
            // Only offered for a whole-screen capture: it reopens the boxes without paying for
            // another Lens call, which matters because those are rate limited.
            actions.add(
                context.actionButton(
                    context.getString(R.string.result_repick),
                ) { callbacks?.onRepick() },
            )
        }
        if (offlineAvailable) {
            actions.add(
                context.actionButton(context.getString(R.string.result_retry_offline)) { callbacks?.onRetryOffline() },
            )
        }
        card.addView(actions)

        applyFold()
    }

    /**
     * Adds a note under the header of the panel that is already open.
     *
     * This is where something that happened *after* the capture is said, such as a share that found
     * nowhere to go: a toast raised by the service is drawn behind the app the user is looking at, so
     * the message would be invisible exactly when it matters. The same text asked for twice is
     * written once — pressing the button again while the answer is still there is not news.
     */
    fun addNote(message: String) {
        if (message.isBlank() || message in noteTexts) return
        if (notesContainer.parent == null) {
            // Under the header, where [showResult] puts the notes it was built with. A card with no
            // header (loading, error) takes it at the top.
            card.addView(notesContainer, if (card.childCount > 0) 1 else 0)
        }
        notesContainer.addView(noteView(message))
        noteTexts += message
    }

    fun showError(message: String, offlineAvailable: Boolean) {
        card.removeAllViews()
        // Nothing was recognised, so there is nothing to go back to.
        canGoBack = false
        // This card has no header either, so it cannot be folded.
        headerRow = null
        minimised = false
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

    /** Writes [text] into the editor as the panel's own change, not as the user's edit. */
    private fun showText(text: String) {
        writingText = true
        try {
            originalEditor.setText(text)
            originalEditor.setSelection(originalEditor.text.length)
        } finally {
            writingText = false
        }
        lastShownText = text
    }

    /**
     * Writes the readings into the recognised text itself, beside the kanji they read — or puts the
     * recognised text back when a later lookup has nothing to write.
     *
     * This is the text the user was already reading, not a copy of it further down the panel: that is
     * the whole point of the block the readings used to live in. When the user has typed, the text is
     * theirs and is left alone — a lookup that lands after a correction must not replace the
     * correction with a reading of the sentence as it was before.
     */
    private fun applyReading(annotated: Furigana.Annotated?) {
        if (userEdited) return
        val shown = annotated?.text?.takeIf { it.isNotBlank() } ?: cleanText
        if (shown == originalEditor.text.toString()) return
        showText(shown)
    }

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
                translationSection.addView(
                    section(SECTION_TRANSLATION, context.getString(R.string.result_translation_label)) { body ->
                        body.addView(
                            textLabel(state.text, 17f, context.getColor(R.color.text_primary)).apply {
                                setTextIsSelectable(true)
                                setLineSpacing(dp(4).toFloat(), 1f)
                                setPadding(dp(2), dp(4), dp(2), dp(10))
                            },
                        )
                    },
                )
                btCopyTranslation.visibility = View.VISIBLE
            }
        }

        // A folded card shows this translation on its one line, so the line is now out of date.
        if (minimised) rebuildHeader()
    }

    fun setWords(state: WordsState) {
        wordsSection.removeAllViews()
        // A list of dictionaries belongs to the answer it was opened over. A new lookup makes it
        // stale, so it goes away with the old answer instead of reappearing over the next one (R16).
        if (state !is WordsState.Done) {
            pickerOpen = false
            wordsState = null
        }
        when (state) {
            is WordsState.Hidden -> applyReading(null)
            is WordsState.Working -> wordsSection.addView(busyRow(context.getString(R.string.dictionary_working)))
            is WordsState.Failed -> {
                wordsSection.addView(
                    section(SECTION_DICTIONARY, context.getString(R.string.result_dictionary_label)) { body ->
                        body.addView(noteView(state.message))
                    },
                )
                // No answer means no reading: leaving the one from a previous lookup would pin it to
                // a sentence that is no longer backed by anything.
                applyReading(null)
            }

            is WordsState.Done -> renderWords(state)
        }
        if (minimised) rebuildHeader()
    }

    private fun renderWords(state: WordsState.Done) {
        // The section can be rebuilt on its own — the chooser opening or closing does that — so it
        // clears itself instead of relying on the caller having done it.
        wordsSection.removeAllViews()
        // Kept so closing the chooser can redraw these words without another lookup (R16).
        wordsState = state

        // The reading belongs to the sentence above, not to a section of its own: it goes into the
        // recognised text, beside the kanji it reads, the moment the lookup that produced it lands.
        applyReading(state.furigana)
        if ((state.furigana?.uncertain ?: 0) > 0) {
            // A guess is still said out loud, under the words: what is between the parentheses came
            // from the kanji data, not from a word, and the `?` in the text is what this explains.
            wordsSection.addView(noteView(context.getString(R.string.result_furigana_note)))
        }

        if (state.dictionaryCount == 0) {
            wordsSection.addView(
                dictionarySection(state, context.getString(R.string.result_dictionary_label)) { body ->
                    body.addView(noteView(context.getString(R.string.dictionary_none_installed)))
                },
            )
            return
        }

        if (state.kanji.isNotEmpty()) {
            wordsSection.addView(
                section(SECTION_KANJI, context.getString(R.string.result_kanji_label)) { body ->
                    for (kanji in state.kanji) body.addView(kanjiView(kanji))
                },
            )
        }
        if (state.matches.isEmpty()) {
            wordsSection.addView(
                dictionarySection(state, context.getString(R.string.result_dictionary_label)) { body ->
                    body.addView(noteView(context.getString(R.string.dictionary_no_match)))
                },
            )
            return
        }

        val shown = state.matches.take(MAX_WORDS)
        wordsSection.addView(
            dictionarySection(
                state,
                context.getString(R.string.result_dictionary_label) +
                    "  " + context.getString(R.string.dictionary_word_count, state.matches.size),
            ) { body ->
                for (match in shown) body.addView(wordView(match))
                if (state.matches.size > shown.size) {
                    body.addView(
                        noteView(context.getString(R.string.dictionary_truncated, shown.size, state.matches.size)),
                    )
                }
            },
        )
    }

    /**
     * The dictionary block: the selector in its header and, while it is open, the dictionaries on top
     * of the body.
     *
     * The chooser is part of the card rather than a popup because the panel *is* an overlay: a second
     * window would need its own token, could be clipped by edges the panel already respects, and a
     * layout test could not see it at all.
     */
    private fun dictionarySection(
        state: WordsState.Done,
        title: String,
        fill: (LinearLayout) -> Unit,
    ): View = section(SECTION_DICTIONARY, title, trailing = dictionaryPicker(state)) { body ->
        if (pickerOpen) body.addView(dictionaryChooser(state))
        fill(body)
    }

    /**
     * The header control that says which dictionary is answering, and opens the chooser.
     *
     * Absent when there is nothing to choose from: with a single dictionary it would be a label
     * dressed up as a switch.
     */
    private fun dictionaryPicker(state: WordsState.Done): View? {
        if (!state.pickable) return null
        val label = state.selected ?: context.getString(R.string.dictionary_all)
        return context.actionButton(
            "$label ▾",
            textColor = context.getColor(R.color.accent),
            background = 0x00000000,
        ) {
            pickerOpen = !pickerOpen
            // The chooser lives in the body, and a folded body shows nothing at all.
            if (pickerOpen) foldedSections.remove(SECTION_DICTIONARY)
            renderWords(state)
        }.apply {
            contentDescription = context.getString(R.string.dictionary_pick)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            maxWidth = dp(PICKER_MAX_WIDTH_DP)
            setPadding(dp(8), 0, 0, 0)
        }
    }

    /** All of them first, then each dictionary by name, the one in use marked. */
    private fun dictionaryChooser(state: WordsState.Done): View {
        val row = FlowLayout(context).apply {
            alignEnd = true
            setPadding(0, dp(2), 0, dp(6))
            // The panel is built in code and this row comes and goes, so the tag is the only handle
            // a layout test has on it.
            tag = DICTIONARY_CHOOSER_TAG
        }
        row.addView(
            dictionaryChip(context.getString(R.string.dictionary_all), state.selected == null) {
                pickDictionary(state, null)
            },
            wrapWithMargin(start = 0),
        )
        for (title in state.titles) {
            row.addView(
                dictionaryChip(title, state.selected == title) {
                    pickDictionary(state, title)
                },
                wrapWithMargin(),
            )
        }
        return row
    }

    /**
     * The answer to «which dictionary» has just been given, so the chooser folds away — and the
     * section is redrawn before the app is told, because the app answers with a «Consultando…» state
     * that a stale redraw would otherwise paint over.
     */
    private fun pickDictionary(state: WordsState.Done, title: String?) {
        pickerOpen = false
        renderWords(state)
        callbacks?.onDictionaryPicked(title)
    }

    private fun dictionaryChip(label: String, chosen: Boolean, onClick: () -> Unit): TextView =
        context.actionButton(
            label,
            background = if (chosen) context.getColor(R.color.accent_dark) else ACTION_BUTTON_BACKGROUND,
            onClick = onClick,
        )

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
            // Jitendex keeps the part of speech only in the markup; the compiler lifts it out of the
            // definition and it belongs here, under the headword, where the eye looks for it (R19).
            if (match.entry.tags.isNotBlank()) add(match.entry.tags)
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
            textLabel(styledDefinition(definition), 14f, context.getColor(R.color.text_primary)).apply {
                setTextIsSelectable(true)
                setLineSpacing(dp(3).toFloat(), 1f)
                setPadding(dp(10), 0, 0, 0)
            },
        )
        return column
    }

    /**
     * The definition with its senses picked out: the numbering bold, and notes and examples smaller,
     * greyer and indented under the sense they explain (R19). The marks come from the compiled text
     * itself — see [DefinitionStyle] — so an old dictionary keeps working, just plainer.
     */
    private fun styledDefinition(text: String): CharSequence {
        val marks = DefinitionStyle.marks(text)
        if (marks.isEmpty()) return text
        val styled = SpannableStringBuilder(text)
        val flag = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        for (mark in marks) {
            when (mark.kind) {
                DefinitionStyle.Kind.NUMBER -> styled.setSpan(
                    StyleSpan(Typeface.BOLD), mark.start, mark.end, flag,
                )

                DefinitionStyle.Kind.SECONDARY -> {
                    styled.setSpan(RelativeSizeSpan(0.92f), mark.start, mark.end, flag)
                    styled.setSpan(
                        ForegroundColorSpan(context.getColor(R.color.text_secondary)),
                        mark.start,
                        mark.end,
                        flag,
                    )
                    styled.setSpan(LeadingMarginSpan.Standard(dp(14)), mark.start, mark.end, flag)
                }
            }
        }
        return styled
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

    /**
     * The top row: the title, the metadata, the fold control and the ✕. When the card is minimised
     * this row is all that is left of it, so the title and the metadata step aside for the answer.
     */
    private fun buildHeader(meta: String): View {
        lastMeta = meta
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            if (minimised) {
                addView(
                    textLabel(stripText(), 13f, context.getColor(R.color.text_primary)).apply {
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                    },
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
                )
            } else {
                addView(
                    textLabel(context.getString(R.string.result_title), 17f, context.getColor(R.color.text_primary), bold = true),
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
                )
                addView(textLabel(meta, 12f, context.getColor(R.color.text_secondary)))
            }
            addView(
                chevron(
                    rotation = if (minimised) 0f else PANEL_FOLD_ROTATION,
                    labelRes = if (minimised) R.string.result_unfold else R.string.result_fold,
                ).apply { setOnClickListener { toggleMinimised() } },
            )
            addView(
                context.actionButton("✕", background = 0x00000000) { callbacks?.onDismiss() }
                    .apply { setPadding(dp(10), dp(4), dp(4), dp(4)) },
            )
        }
        headerRow = row
        return row
    }

    // --- the captured image ---------------------------------------------------------------------

    /**
     * Puts the captured image beside the recognised text, or takes it away again.
     *
     * The row is rebuilt in place rather than the whole card: the text, the translation, the
     * dictionary and which sections are folded are all still good, and rebuilding the card would
     * throw them away — and start the lookups over.
     */
    fun setImageShown(shown: Boolean) {
        val wanted = shown && image != null
        if (wanted == imageShown) return
        imageShown = wanted
        rebuildTextRow()
        refreshImageButton()
    }

    /**
     * The recognised text, with the frame it came from beside it when the user wants to compare them.
     *
     * The picture is sized by the picture itself, up to a cap, and the text takes what is left: a
     * manga column is tall and narrow, a whole screen is tall and wide, and a fixed half-and-half
     * split would either squeeze the text for nothing or leave a hole where the picture is not.
     */
    private fun buildTextRow(): View {
        // The editor is a field, so the row it is taken out of may still hold it, and a view that
        // already has a parent cannot be added to another one.
        (originalEditor.parent as? ViewGroup)?.removeView(originalEditor)

        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val picture = image
        if (imageShown && picture != null) {
            row.addView(
                CappedImageView(context, ::freeHeightForImage).apply {
                    setImageBitmap(picture)
                    contentDescription = context.getString(R.string.result_image_description)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    // Never enlarged, only shrunk to fit: a small crop shown at its own size beats the
                    // same few pixels smeared across a third of the panel.
                    adjustViewBounds = true
                    maxWidth = dp(IMAGE_MAX_WIDTH_DP)
                    // The hard ceiling. [freeHeightForImage] asks for less when the screen is short.
                    maxHeight = dp(IMAGE_MAX_HEIGHT_DP)
                    setPadding(0, dp(2), dp(8), dp(2))
                },
            )
        }
        row.addView(
            originalEditor,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )
        textRow = row
        return row
    }

    private fun rebuildTextRow() {
        val old = textRow ?: return
        val index = card.indexOfChild(old)
        if (index < 0) return
        card.removeViewAt(index)
        card.addView(
            buildTextRow(),
            index,
            LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT),
        )
        applyFold()
    }

    /** The toggle reads as «on» while the picture is on screen, because that is what it toggles. */
    private fun refreshImageButton() {
        imageButton?.setBackground(roundedBackground(imageButtonBackground(), dp(12).toFloat()))
    }

    private fun imageButtonBackground(): Int =
        if (imageShown) context.getColor(R.color.accent_dark) else ACTION_BUTTON_BACKGROUND

    // --- folding -------------------------------------------------------------------------------

    /**
     * One folding block of the panel: a header with an indicator, and the body it hides.
     *
     * The dictionary block can run to sixty entries while the translation is often the only thing
     * wanted, so each block folds on its own. The choice is remembered by [key] — a re-analysis
     * rebuilds every block, and the user should not have to fold the same section twice — and the key
     * is deliberately not the title, because the dictionary title carries a word count that changes.
     */
    private fun section(
        key: String,
        title: String,
        trailing: View? = null,
        fill: (LinearLayout) -> Unit,
    ): View {
        val body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        fill(body)

        val folded = foldedSections.contains(key)
        body.visibility = if (folded) View.GONE else View.VISIBLE
        val indicator = chevron(
            rotation = if (folded) FOLDED_ROTATION else 0f,
            labelRes = if (folded) R.string.result_unfold_section else R.string.result_fold_section,
        )

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            // Clickable, and tagged with the key: the panel is built in code, so this is what the
            // tests have to hold on to. The label is not an action button and carries no button tag.
            isClickable = true
            isFocusable = true
            tag = key
            setPadding(dp(2), dp(8), dp(2), dp(2))
            addView(indicator)
            addView(
                textLabel(title, 12f, context.getColor(R.color.accent), bold = true)
                    .apply {
                        setPadding(dp(6), 0, 0, 0)
                        if (trailing != null) {
                            // Something shares the header line with the title, so the title gives way
                            // to it instead of pushing it off the edge.
                            maxLines = 1
                            ellipsize = TextUtils.TruncateAt.END
                        }
                    },
                if (trailing == null) {
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    )
                } else {
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                },
            )
            if (trailing != null) addView(trailing)
            setOnClickListener {
                val nowFolded = body.visibility == View.VISIBLE
                body.visibility = if (nowFolded) View.GONE else View.VISIBLE
                indicator.rotation = if (nowFolded) FOLDED_ROTATION else 0f
                indicator.contentDescription = context.getString(
                    if (nowFolded) R.string.result_unfold_section else R.string.result_fold_section,
                )
                if (nowFolded) foldedSections.add(key) else foldedSections.remove(key)
            }
        }

        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(header)
            addView(body)
        }
    }

    /** The little chevron that says which way a block will move. */
    private fun chevron(rotation: Float, labelRes: Int): ImageView = ImageView(context).apply {
        setImageResource(R.drawable.ic_chevron)
        imageTintList = ColorStateList.valueOf(context.getColor(R.color.text_secondary))
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        contentDescription = context.getString(labelRes)
        this.rotation = rotation
        val size = dp(22)
        layoutParams = LinearLayout.LayoutParams(size, size)
    }

    /**
     * Folds the whole card into its header, which then carries the answer instead of the title.
     *
     * The card hangs from the top of the screen and grows until the screen runs out, and in manga the
     * text that matters is usually underneath it. One line of translation plus a control to bring the
     * rest back is worth more than a panel that covers half the page.
     */
    private fun toggleMinimised() {
        if (headerRow == null) return
        minimised = !minimised
        rebuildHeader()
        applyFold()
    }

    /** Rebuilds the header, the one row whose content depends on the folded state. */
    private fun rebuildHeader() {
        val old = headerRow ?: return
        val index = card.indexOfChild(old)
        if (index < 0) return
        card.removeViewAt(index)
        card.addView(buildHeader(lastMeta), index)
    }

    /**
     * Hides every row of the card but the header while it is minimised.
     *
     * A folded *section* hides its own body and is no business of this function. Every row of the card
     * is either shown or not added at all — none of them carries a visibility of its own — so showing
     * them all is a correct way back.
     */
    private fun applyFold() {
        val header = headerRow ?: return
        for (index in 0 until card.childCount) {
            val child = card.getChildAt(index)
            child.visibility = if (minimised && child !== header) View.GONE else View.VISIBLE
        }
    }

    /**
     * What a folded card shows on its one line: the translation when there is one, because that is
     * what the panel is usually opened for, and the recognised text when there is not.
     */
    private fun stripText(): CharSequence {
        val text = translationText?.takeIf { it.isNotBlank() } ?: originalEditor.text.toString()
        val line = text.replace('\n', ' ').replace(Regex(" {2,}"), " ").trim()
        return line.ifBlank { context.getString(R.string.result_title) }
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
    private fun freeHeightForScroller(width: Int): Int =
        (availableHeight() - heightOfChrome(except = scroller, width = width))
            .coerceAtLeast(dp(MIN_CONTENT_DP))

    /**
     * How tall the captured picture may be.
     *
     * It used to be a fixed [IMAGE_MAX_HEIGHT_DP] and the panel simply got taller than the screen when
     * the rest did not fit — which is what the two-column grid of actions (R6) did on a 640 dp screen:
     * with the picture at its cap there was no longer any room left to squeeze out of the scrolling
     * section, and the last row of buttons sat below the bottom edge. The picture is the one part of
     * the card that can give way without anything becoming unreachable, so it gets what is left once
     * the header, the text and the actions have taken their share — the same measure-don't-guess rule
     * as [freeHeightForScroller].
     */
    private fun freeHeightForImage(width: Int): Int =
        (availableHeight() - heightOfChrome(except = textRow, width = width) - dp(MIN_CONTENT_DP))
            .coerceIn(0, dp(IMAGE_MAX_HEIGHT_DP))

    /** What the panel has to work with: the screen minus the bars it cannot draw over. */
    private fun availableHeight(): Int =
        context.resources.displayMetrics.heightPixels -
            systemBarHeight("status_bar_height", fallbackDp = 24) -
            systemBarHeight("navigation_bar_height", fallbackDp = 48) -
            dp(CARD_TOP_MARGIN_DP)

    /**
     * The height the card spends on everything except [except] (and except the scrolling section, whose
     * own budget this is). Children are measured here as they would be on screen, so the sum cannot go
     * stale the way a constant does.
     */
    private fun heightOfChrome(except: View?, width: Int): Int {
        var chrome = card.paddingTop + card.paddingBottom
        for (index in 0 until card.childCount) {
            val child = card.getChildAt(index)
            // Skipped: the child being sized, the scroller, and anything the user cannot see.
            if (child === except || child === scroller || child.visibility != View.VISIBLE) continue
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
        return chrome
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

    /**
     * An [ImageView] whose cap is asked for on every measure pass, for the same reason as
     * [MaxHeightScrollView]: how much of the picture fits depends on how tall the rest of the card
     * turned out, and that is only known once the neighbours have been measured.
     */
    private class CappedImageView(
        context: Context,
        private val cap: (width: Int) -> Int,
    ) : ImageView(context) {
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

        /** Fold state keys. Titles cannot be keys: the dictionary one carries a word count. */
        private const val SECTION_TRANSLATION = "translation"
        private const val SECTION_KANJI = "kanji"
        private const val SECTION_DICTIONARY = "dictionary"

        /**
         * Tag on the row of dictionaries the selector opens. The panel is built in code and the row
         * comes and goes, so this is the only handle a layout test has on it.
         */
        const val DICTIONARY_CHOOSER_TAG = "yabai.dictionaryChooser"

        /**
         * Tag on the block of apps a question can go to. Like [DICTIONARY_CHOOSER_TAG], it is the
         * handle a layout test has on a block that only exists after a tap.
         */
        /** Folded sections point their chevron to the right; a folded panel points it up. */
        private const val FOLDED_ROTATION = -90f
        private const val PANEL_FOLD_ROTATION = 180f

        /**
         * How much of the panel the captured image may take. Wide enough for a manga column, short
         * enough that the text beside it keeps the larger share of the width. The height is a ceiling:
         * on a short screen the picture is shrunk further rather than pushing the buttons off.
         */
        private const val IMAGE_MAX_WIDTH_DP = 120
        private const val IMAGE_MAX_HEIGHT_DP = 200

        /** Where the dictionary name in the section header stops, so the title beside it survives. */
        private const val PICKER_MAX_WIDTH_DP = 150
    }
}
