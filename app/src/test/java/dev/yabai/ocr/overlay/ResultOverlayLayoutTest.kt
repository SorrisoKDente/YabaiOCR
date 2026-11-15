package dev.yabai.ocr.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Looper
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import dev.yabai.ocr.R
import dev.yabai.ocr.core.dict.DictionaryEntry
import dev.yabai.ocr.core.dict.TokenMatch
import dev.yabai.ocr.util.dp
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Regression tests for the result panel: how it lays out, and what it does when the text is edited.
 *
 * The panel is built in code, so no layout file gets reviewed by a compiler: a mistake only shows up
 * on a phone, in whatever font and screen width the user happens to have. These tests measure the
 * real views instead — the panel is built, measured at a phone width and laid out, and the
 * assertions are about the result.
 *
 * Robolectric measures text at one pixel per character instead of using real font metrics, so no
 * assertion here depends on how wide a glyph is: buttons are always compared against themselves.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w320dp-h640dp-mdpi")
class ResultOverlayLayoutTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    /**
     * Regression: the panel reserved a constant 260dp for the parts of the card that cannot scroll
     * and handed the rest of the screen to the scroller. The guess went stale as soon as those parts
     * grew — a long OCR result makes the editor taller, and a narrow screen makes the button row wrap
     * onto a second line — and the card then grew past the bottom of the screen, where the copy
     * buttons cannot be tapped.
     */
    @Test
    fun `the panel never asks for more height than the screen has`() {
        val panel = layout(captured(), 320)
        assertTrue(
            "the panel wants ${panel.measuredHeight}px but only ${freeHeight()}px fit below the status bar",
            panel.measuredHeight <= freeHeight(),
        )
    }

    /**
     * Regression: the button rows were `LinearLayout`s, and a LinearLayout never wraps. Once the
     * buttons added up to more than the panel was wide, the last one was squeezed to a sliver and its
     * label was drawn one character per line — «Fechar» as a vertical column of letters, hanging off
     * the bottom of the card.
     */
    @Test
    fun `no button is squeezed narrower than its own label`() {
        for (width in listOf(320, 240, 160)) {
            val panel = layout(captured(), width)
            val buttons = buttonRows(panel).flatten()
            assertTrue("the panel should have buttons to measure", buttons.size >= 6)
            for (button in buttons) {
                val laidOut = button.width
                // Measured again with nothing in the way: that is the width the button asks for,
                // padding and all. Going through the button itself rather than a fresh one keeps the
                // comparison fair for the ones that are deliberately tighter, like the header's ✕.
                button.measure(
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                )
                assertTrue(
                    "at ${width}px «${button.text}» was laid out ${laidOut}px wide but wants " +
                        "${button.measuredWidth}px",
                    laidOut >= button.measuredWidth,
                )
            }
        }
    }

    /**
     * R6: the actions are a grid, so a narrow screen cannot push one button under another or off the
     * card. At 160 px the labels wrap inside their cells; the cells stay two, side by side.
     */
    @Test
    fun `at a narrow width the actions sit side by side without overlapping`() {
        val panel = layout(captured(), 160)

        for (row in buttonRows(panel)) {
            assertTrue("a row of the grid has ${row.size} buttons", row.size <= 2)
            if (row.size == 2) {
                assertTrue(
                    "two buttons overlap at 160 px: ${row[0].left}..${row[0].right} and ${row[1].left}..${row[1].right}",
                    row[1].left >= row[0].right,
                )
            }
        }
    }

    /**
     * Regression: in a landscape game (a short window) with the «Imagem» toggle on, the panel opened
     * showing nothing but the header and the «Texto reconhecido» label. The picture takes whatever
     * height is left once the header, the text and the actions had their share, and on a short screen
     * that is nothing; an [android.widget.ImageView] with `adjustViewBounds` and no height to fit
     * then divides the empty height into a width of sixteen million pixels. The row kept that width
     * for the picture, so the editor got the zero that was left — present, editable, invisible — and
     * the card, now taller than the window, squeezed the action grid to nothing as well.
     */
    @Test
    @Config(qualifiers = "w780dp-h360dp-xxhdpi")
    fun `a short landscape screen keeps the text and the actions with the picture on`() {
        val panel = capturedWithPicture()
        val metrics = context.resources.displayMetrics
        panel.measure(
            View.MeasureSpec.makeMeasureSpec(metrics.widthPixels, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(metrics.heightPixels, View.MeasureSpec.EXACTLY),
        )
        panel.layout(0, 0, metrics.widthPixels, metrics.heightPixels)

        val editor = editor(panel)
        assertTrue("the text field was squeezed to ${editor.width}px wide", editor.width > 0)

        val buttons = buttonRows(panel).flatten()
        assertTrue("the panel should still offer its actions", buttons.size >= 5)
        for (button in buttons) {
            assertTrue(
                "«${button.text}» was squeezed to ${button.height}px tall",
                button.height > 0,
            )
        }
    }

    /**
     * The other side of the test above: where the screen does have room, the picture is still there.
     * The guard that drops a picture with no room must not become a panel that never shows one.
     */
    @Test
    fun `the picture still shows when the screen has room for it`() {
        val panel = layout(capturedWithPicture(), 320)
        val picture = picture(panel)
        assertNotNull("the panel should carry the captured picture", picture)
        assertTrue(
            "the picture should be on screen: ${picture!!.width}x${picture.height}",
            picture.width > 0 && picture.height > 0,
        )
    }

    /**
     * Regression: on a phone held sideways the action grid used to run off the bottom of the card and
     * take the last button with it — the button was there in the view tree, half of it below the
     * window, and could not be tapped. A landscape window is barely 360 dp tall and the actions were
     * two per row; the picture, a long OCR result, its translation and a dictionary entry had already
     * spent the height. Three narrower columns and a tighter button style are what buy the rows back.
     *
     * This is the worst case the panel can be in, so the assertion is the whole card and every one of
     * its actions, not just the grid.
     */
    @Test
    @Config(qualifiers = "w780dp-h360dp-xxhdpi")
    fun `a short landscape screen keeps every action inside the window`() {
        val panel = capturedFullPageWithPicture()
        val metrics = context.resources.displayMetrics
        panel.measure(
            View.MeasureSpec.makeMeasureSpec(metrics.widthPixels, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(metrics.heightPixels, View.MeasureSpec.EXACTLY),
        )
        panel.layout(0, 0, metrics.widthPixels, metrics.heightPixels)

        val buttons = buttonRows(panel).flatten()
        assertTrue("the panel should offer its actions", buttons.size >= 6)
        for (button in buttons) {
            assertTrue(
                "«${button.text}» was squeezed to ${button.height}px tall",
                button.height > 0,
            )
            val bottom = bottomWithin(panel, button)
            assertTrue(
                "«${button.text}» ends at ${bottom}px, past the ${panel.height}px window",
                bottom <= panel.height,
            )
            // The other half of the report: the actions were not merely cut, they were so tall that
            // they covered the meanings. A grid button is at most 32 dp tall — its own old size was
            // 34 dp, which is what turned six actions into four rows.
            assertTrue(
                "«${button.text}» is ${button.height}px tall, more than the 32 dp a grid action takes",
                button.height <= context.dp(32),
            )
        }
    }

    /**
     * The number of columns follows the screen: three on a phone turned sideways, where the width
     * has room for a third label and the height has none to spare; two held upright, where a third
     * would only force the labels to wrap.
     */
    @Test
    fun `a phone held up keeps two actions on a row`() {
        assertEquals(listOf(2, 2, 2), gridRows(layout(capturedFullPageWithPicture(), 320)).map { it.size })
    }

    @Test
    @Config(qualifiers = "w780dp-h360dp-xxhdpi")
    fun `a phone turned sideways puts three actions on a row`() {
        val panel = capturedFullPageWithPicture()
        val metrics = context.resources.displayMetrics
        panel.measure(
            View.MeasureSpec.makeMeasureSpec(metrics.widthPixels, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(metrics.heightPixels, View.MeasureSpec.EXACTLY),
        )
        panel.layout(0, 0, metrics.widthPixels, metrics.heightPixels)

        assertEquals(listOf(3, 3), gridRows(panel).map { it.size })
    }

    /** The actions on offer, in the order the user sees them. */
    @Test
    fun `the panel offers the actions it is meant to`() {
        val panel = layout(captured(), 320)
        assertEquals(
            listOf(
                "✕",
                context.getString(R.string.result_copy_original),
                context.getString(R.string.result_copy_translation),
                context.getString(R.string.result_ask_ai),
                context.getString(R.string.result_close),
                context.getString(R.string.result_repick),
            ),
            buttonRows(panel).flatten().map { it.text.toString() },
        )
    }

    // --- editing the recognised text ------------------------------------------------------------

    /**
     * Regression: the panel used to carry an «Analisar» button for this. Nothing about the button
     * said what it did — it was reported as doing nothing at all — and a correction only took effect
     * if the user found it and pressed it. The panel watches the edited text itself now.
     */
    @Test
    fun `correcting the text re-runs the lookup once the typing stops`() {
        val panel = captured()
        val asked = ArrayList<String>()
        panel.callbacks = recordingCallbacks(asked)
        val editor = editor(panel)

        editor.text.append("！")

        // Mid-typing the old answer is still on screen and no lookup has been asked for.
        idle(400)
        assertEquals(emptyList<String>(), asked)

        // The pause is long enough now.
        idle(400)
        assertEquals(listOf(editor.text.toString()), asked)
    }

    /** Typing a sentence is one edit, not one lookup per keystroke. */
    @Test
    fun `a burst of typing asks for a single lookup`() {
        val panel = captured()
        val asked = ArrayList<String>()
        panel.callbacks = recordingCallbacks(asked)
        val editor = editor(panel)

        editor.text.append("あ")
        idle(300)
        editor.text.append("い")
        idle(300)
        editor.text.append("う")
        idle(900)

        assertEquals(listOf(editor.text.toString()), asked)
    }

    /** Showing a result is not an edit: it must not buy a second, identical lookup. */
    @Test
    fun `showing a result does not look it up again`() {
        val panel = captured()
        val asked = ArrayList<String>()
        panel.callbacks = recordingCallbacks(asked)

        idle(1000)

        assertEquals(emptyList<String>(), asked)
    }

    /**
     * Regression: the dictionary list is drawn *inside* the panel, over the words. BACK — and a tap
     * outside the card, which is the same step — used to close the whole panel, throwing the capture
     * away and costing another Lens call to get it back. The list closes first, and only a second
     * BACK leaves the panel (R16).
     */
    @Test
    fun `back closes the dictionary list before leaving the panel`() {
        val panel = layout(panelWithTwoDictionaries(), 320)
        var repicks = 0
        var dismissals = 0
        panel.callbacks = object : ResultOverlay.Callbacks by recordingCallbacks(ArrayList()) {
            override fun onRepick() {
                repicks++
            }

            override fun onDismiss() {
                dismissals++
            }
        }

        openChooser(panel)

        back(panel)

        assertNull(
            "BACK left the dictionary list open",
            panel.findViewWithTag<View>(ResultOverlay.DICTIONARY_CHOOSER_TAG),
        )
        assertEquals("the panel closed together with the list", 0, repicks)
        assertEquals(0, dismissals)

        // The tap outside the card is the same step, so it has to close the list too.
        openChooser(panel)
        tapOutside(panel)
        assertNull(
            "a tap outside left the dictionary list open",
            panel.findViewWithTag<View>(ResultOverlay.DICTIONARY_CHOOSER_TAG),
        )
        assertEquals(0, repicks)

        back(panel)

        assertEquals("a second BACK steps out of the panel, as before", 1, repicks)
        assertEquals(0, dismissals)
    }

    /** Opens the dictionary list the way the user does: the header control under «Dicionário». */
    private fun openChooser(panel: ResultOverlay) {
        val picker = labels(panel).firstOrNull {
            it.contentDescription == context.getString(R.string.dictionary_pick)
        }
        assertNotNull("the panel lost its dictionary picker", picker)
        picker!!.performClick()
        assertNotNull(
            "the picker did not open the list",
            panel.findViewWithTag<View>(ResultOverlay.DICTIONARY_CHOOSER_TAG),
        )
    }

    private fun back(view: View) {
        view.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
        view.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))
    }

    /**
     * A tap in the margin beside the card. The gesture is a touch listener on the panel, not a
     * click: a click would also fire on SPACE, and space is the keyboard's language key (R32).
     */
    private fun tapOutside(view: View) {
        val x = 2f
        val y = view.height / 2f
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, x, y, 0)
        val up = MotionEvent.obtain(0, 10, MotionEvent.ACTION_UP, x, y, 0)
        view.dispatchTouchEvent(down)
        view.dispatchTouchEvent(up)
        down.recycle()
        up.recycle()
    }

    /** A result whose dictionary section can offer a chooser: two dictionaries, nothing selected. */
    private fun panelWithTwoDictionaries(): ResultOverlay = ResultOverlay(context).apply {
        showResult(
            text = "職員",
            meta = "Google Lens · 1200 ms · ja",
            notes = emptyList(),
            showLines = false,
            segmentLines = emptyList(),
            canRepick = true,
            canAskAi = true,
        )
        setWords(
            ResultOverlay.WordsState.Done(
                matches = listOf(
                    TokenMatch(
                        surface = "職員",
                        start = 0,
                        end = 2,
                        entry = DictionaryEntry(
                            term = "職員",
                            reading = "しょくいん",
                            tags = "n",
                            rulesFlags = 0,
                            score = 1,
                            definition = "funcionário; empregado",
                        ),
                        dictionaryTitle = "JMdict",
                        reasons = emptyList(),
                    ),
                ),
                kanji = emptyList(),
                dictionaryCount = 2,
                titles = listOf("JMdict", "Jitendex"),
            ),
        )
    }

    // --- the panel under test -------------------------------------------------------------------

    /**
     * A whole-screen capture of a full page of manga, already translated and looked up: the case
     * that fills the card from top to bottom.
     */
    private fun captured(
        picture: Bitmap? = null,
        showImage: Boolean = false,
    ): ResultOverlay = ResultOverlay(context).apply {
        showResult(
            text = "しょくいんネオの職員さんに聞いたら非童帝くんいうから…いしゅつ外出したって".repeat(6),
            meta = "Google Lens · 2348 ms · ja",
            notes = listOf("texto obtido com o Google Lens"),
            showLines = false,
            segmentLines = emptyList(),
            canRepick = true,
            image = picture,
            showImage = showImage,
            canAskAi = true,
        )
        setTranslation(
            ResultOverlay.TranslationState.Done(
                "Nasceu um cereal chamado Neo. Quando perguntei ao funcionário, ele disse que saiu por aí.".repeat(12),
            ),
        )
        setWords(
            ResultOverlay.WordsState.Done(
                matches = listOf(
                    TokenMatch(
                        surface = "職員",
                        start = 0,
                        end = 2,
                        entry = DictionaryEntry(
                            term = "職員",
                            reading = "しょくいん",
                            tags = "n",
                            rulesFlags = 0,
                            score = 1,
                            definition = "1. funcionário; empregado\n2. equipe",
                        ),
                        dictionaryTitle = "JMdict",
                        reasons = listOf("past"),
                    ),
                ),
                kanji = emptyList(),
                dictionaryCount = 1,
            ),
        )
    }

    /** The same full page, with the captured frame on screen beside it. */
    private fun capturedFullPageWithPicture(): ResultOverlay = captured(
        picture = Bitmap.createBitmap(1600, 900, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(40, 60, 90))
        },
        showImage = true,
    )

    // --- helpers --------------------------------------------------------------------------------

    /**
     * A capture whose picture is on screen — the «Imagem» button writes a saved setting, so every
     * later capture opens this way.
     */
    private fun capturedWithPicture(): ResultOverlay = ResultOverlay(context).apply {
        showResult(
            text = "職員さんに聞いたら",
            meta = "Google Lens · 4857 ms · ja",
            notes = emptyList(),
            showLines = false,
            segmentLines = emptyList(),
            canRepick = true,
            image = Bitmap.createBitmap(320, 90, Bitmap.Config.ARGB_8888).apply {
                eraseColor(Color.rgb(70, 90, 140))
            },
            showImage = true,
            canAskAi = true,
        )
    }

    /** Measures and lays out [view] the way the window manager would at a screen [width]. */
    private fun <T : View> layout(view: T, width: Int): T {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        view.layout(0, 0, width, view.measuredHeight)
        return view
    }

    /** How much height the panel's window has: below the status bar, above the navigation bar. */
    private fun freeHeight(): Int {
        var free = context.resources.displayMetrics.heightPixels
        for (name in listOf("status_bar_height", "navigation_bar_height")) {
            val id = context.resources.getIdentifier(name, "dimen", "android")
            if (id > 0) free -= context.resources.getDimensionPixelSize(id)
        }
        return free
    }

    /** Runs whatever the panel scheduled, [millis] of clock later. */
    private fun idle(millis: Long) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))
    }

    /** The panel's editable recognised text. */
    private fun editor(root: ViewGroup): EditText {
        fun find(group: ViewGroup): EditText? {
            for (index in 0 until group.childCount) {
                when (val child = group.getChildAt(index)) {
                    is EditText -> return child
                    is ViewGroup -> find(child)?.let { return it }
                }
            }
            return null
        }
        return find(root) ?: error("the panel has no editable text")
    }

    /** The picture beside the recognised text, if the panel put one on screen. */
    private fun picture(root: ViewGroup): ImageView? {
        val wanted = context.getString(R.string.result_image_description)
        fun find(group: ViewGroup): ImageView? {
            for (index in 0 until group.childCount) {
                when (val child = group.getChildAt(index)) {
                    is ImageView ->
                        if (child.contentDescription?.toString() == wanted) return child
                    is ViewGroup -> find(child)?.let { return it }
                }
            }
            return null
        }
        return find(root)
    }

    /** Every label under [root], in the order it was added, so a test can look for the one it means. */
    private fun labels(root: ViewGroup): List<TextView> {
        val found = ArrayList<TextView>()
        fun walk(group: ViewGroup) {
            for (index in 0 until group.childCount) {
                when (val child = group.getChildAt(index)) {
                    is TextView -> found.add(child)
                    is ViewGroup -> walk(child)
                }
            }
        }
        walk(root)
        return found
    }

    /** A [ResultOverlay.Callbacks] that only remembers what it was asked for. */
    private fun recordingCallbacks(asked: MutableList<String>): ResultOverlay.Callbacks =
        object : ResultOverlay.Callbacks {
            override fun onCopyOriginal(text: String) = Unit
            override fun onCopyTranslation(text: String) = Unit

            override fun onAnalyse(text: String) {
                asked.add(text)
            }

            override fun onRepick() = Unit
            override fun onRetryOffline() = Unit
            override fun onImageToggle() = Unit
            override fun onAskAi(text: String) = Unit
            override fun onDictionaryPicked(title: String?) = Unit
            override fun onDismiss() = Unit
        }

    /** The panel's button rows, in the order they appear; one entry per row container. */
    private fun buttonRows(root: ViewGroup): List<List<TextView>> {
        val rows = ArrayList<List<TextView>>()

        fun walk(group: ViewGroup) {
            val own = ArrayList<TextView>()
            for (index in 0 until group.childCount) {
                val child = group.getChildAt(index)
                if (child is TextView && child.tag == ACTION_BUTTON_TAG && child.visibility == View.VISIBLE) {
                    own.add(child)
                }
            }
            if (own.isNotEmpty()) rows.add(own)
            for (index in 0 until group.childCount) {
                val child = group.getChildAt(index)
                if (child is ViewGroup) walk(child)
            }
        }

        walk(root)
        return rows
    }

    /** The action grid's rows, each as the buttons it holds. */
    private fun gridRows(root: ViewGroup): List<List<TextView>> {
        val grid = findGrid(root) ?: error("the panel has no action grid")
        val rows = ArrayList<List<TextView>>()
        for (index in 0 until grid.childCount) {
            val row = grid.getChildAt(index) as ViewGroup
            val buttons = ArrayList<TextView>()
            for (cell in 0 until row.childCount) {
                val button = row.getChildAt(cell) as? TextView ?: continue
                if (button.visibility == View.VISIBLE) buttons.add(button)
            }
            rows.add(buttons)
        }
        return rows
    }

    private fun findGrid(group: ViewGroup): ViewGroup? {
        for (index in 0 until group.childCount) {
            val child = group.getChildAt(index)
            if (child.tag == ActionGrid.GRID_TAG) return child as ViewGroup
            if (child is ViewGroup) findGrid(child)?.let { return it }
        }
        return null
    }

    /** Where [view]'s bottom edge sits, in [root]'s coordinates. */
    private fun bottomWithin(root: View, view: View): Int {
        var bottom = view.height
        var current: View? = view
        while (current != null && current !== root) {
            bottom += current.top
            current = current.parent as? View
        }
        return bottom
    }
}
