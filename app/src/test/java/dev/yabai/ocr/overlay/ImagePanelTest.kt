package dev.yabai.ocr.overlay

import android.content.Context
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import dev.yabai.ocr.R
import dev.yabai.ocr.util.dp
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Tests for the captured image next to the recognised text.
 *
 * The image is what makes an OCR mistake visible: the panel says 淀 or 泥 and the page says which. It
 * costs width, though, and the thing it takes width from is the editable text — so what these tests
 * hold down is that the picture is beside the text, that it is only offered when there is one, and
 * that turning it off gives the width back.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w320dp-h640dp-mdpi")
class ImagePanelTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun `the image sits beside the recognised text, not above it`() {
        val panel = layout(panel(crop(200, 600), showImage = true), 320)
        val picture = picture(panel)!!
        val text = editor(panel)

        assertEquals("the picture is not in the text's row", text.parent, picture.parent)
        assertTrue("the picture is not to the left of the text", picture.right <= text.left)
        assertTrue("the picture is wider than its cap: ${picture.width}", picture.width <= context.dp(120))
        assertTrue("the picture is taller than its cap: ${picture.height}", picture.height <= context.dp(200))

        // Shrunk to fit, never stretched: a column of manga that came out 200x600 stays 1:3. The
        // padding around the picture is not part of the picture.
        val drawnWidth = picture.width - picture.paddingStart - picture.paddingEnd
        val drawnHeight = picture.height - picture.paddingTop - picture.paddingBottom
        val wanted = 200f / 600f
        val got = drawnWidth.toFloat() / drawnHeight.toFloat()
        assertTrue("the picture was distorted: $got instead of $wanted", abs(got - wanted) < 0.02f)
    }

    /** The text is what is edited to fix an OCR mistake; it only pays width when the user asks. */
    @Test
    fun `without the image the text keeps the whole width`() {
        val cases = listOf(
            "hidden" to panel(crop(200, 600), showImage = false),
            "not captured" to panel(null, showImage = true),
        )
        for ((what, panel) in cases) {
            layout(panel, 320)
            assertNull("$what: the panel is showing an image", picture(panel))
            assertEquals(
                "$what: the text does not fill the card",
                card(panel).width - card(panel).paddingStart - card(panel).paddingEnd,
                editor(panel).width,
            )
        }
    }

    @Test
    fun `the image can be turned on and off while the panel is open`() {
        val panel = panel(crop(200, 600), showImage = false)
        val full = layout(panel, 320).let { editor(it).width }

        panel.setImageShown(true)
        layout(panel, 320)

        val picture = picture(panel)
        assertTrue("no image appeared", picture != null)
        assertTrue("the text did not give up any width", editor(panel).width < full)
        assertEquals("the image took the text with it", SENTENCE, editor(panel).text.toString())
        assertTrue("the translation was thrown away", translationShown(panel))

        panel.setImageShown(false)
        layout(panel, 320)

        assertNull("the image stayed on screen", picture(panel))
        assertEquals("the text did not get its width back", full, editor(panel).width)
        assertEquals("the text changed", SENTENCE, editor(panel).text.toString())
    }

    @Test
    fun `the image is offered as a button only when there is one`() {
        val imageLabel = context.getString(R.string.result_image)
        val withImage = labels(layout(panel(crop(200, 600), showImage = false), 320))
        val without = labels(layout(panel(null, showImage = true), 320))

        assertEquals(
            listOf(
                "✕",
                context.getString(R.string.result_repick),
                imageLabel,
                context.getString(R.string.result_retry_offline),
                context.getString(R.string.result_close),
                context.getString(R.string.result_copy_original),
                context.getString(R.string.result_copy_translation),
            ),
            withImage,
        )
        assertTrue("the panel with no capture still offers the picture button", without.none { it == imageLabel })
    }

    @Test
    fun `the image button asks the app to remember the choice`() {
        val panel = layout(panel(crop(200, 600), showImage = false), 320)
        val asked = ArrayList<String>()
        panel.callbacks = panelCallbacks(asked)

        buttons(panel).first { it.text == context.getString(R.string.result_image) }.performClick()

        assertEquals(listOf("image"), asked)
    }

    /** A tall capture used to be able to take the bottom of the panel off the screen. */
    @Test
    fun `a very tall capture does not push the panel off the screen`() {
        val panel = layout(panel(crop(300, 3000), showImage = true), 320)

        assertTrue("the image is taller than its cap", picture(panel)!!.height <= context.dp(200))
        assertTrue(
            "the panel wants ${panel.measuredHeight}px but only ${freeHeight()}px fit",
            panel.measuredHeight <= freeHeight(),
        )
    }

    // --- the panel under test -------------------------------------------------------------------

    private fun panel(image: Bitmap?, showImage: Boolean): ResultOverlay = ResultOverlay(context).apply {
        showResult(
            text = SENTENCE,
            meta = "Google Lens · 2348 ms · ja",
            notes = emptyList(),
            offlineAvailable = true,
            showLines = false,
            segmentLines = emptyList(),
            canRepick = true,
            image = image,
            showImage = showImage,
        )
        setTranslation(ResultOverlay.TranslationState.Done(TRANSLATION))
    }

    /** A frame of [width] x [height], which is all the layout needs of one. */
    private fun crop(width: Int, height: Int): Bitmap =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

    // --- helpers --------------------------------------------------------------------------------

    private fun card(root: ViewGroup): LinearLayout = root.getChildAt(0) as LinearLayout

    /** The captured image beside the text, or null when the panel is not showing one. */
    private fun picture(root: ViewGroup): ImageView? {
        val row = editor(root).parent as ViewGroup
        for (index in 0 until row.childCount) {
            val child = row.getChildAt(index)
            if (child is ImageView) return child
        }
        return null
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

    private fun translationShown(root: ViewGroup): Boolean =
        root.findViewWithText(TRANSLATION) != null

    private fun ViewGroup.findViewWithText(wanted: String): View? {
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (child is TextView && child.text.toString() == wanted) return child
            if (child is ViewGroup) child.findViewWithText(wanted)?.let { return it }
        }
        return null
    }

    /** Every button of the panel, in the order they appear. */
    private fun buttons(root: ViewGroup): List<TextView> {
        val found = ArrayList<TextView>()
        fun walk(group: ViewGroup) {
            for (index in 0 until group.childCount) {
                val child = group.getChildAt(index)
                if (child is TextView && child.tag == ACTION_BUTTON_TAG && child.visibility == View.VISIBLE) {
                    found.add(child)
                }
                if (child is ViewGroup) walk(child)
            }
        }
        walk(root)
        return found
    }

    private fun labels(root: ViewGroup): List<String> = buttons(root).map { it.text.toString() }

    private fun panelCallbacks(asked: MutableList<String>): ResultOverlay.Callbacks =
        object : ResultOverlay.Callbacks {
            override fun onCopyOriginal(text: String) = Unit
            override fun onCopyTranslation(text: String) = Unit
            override fun onAnalyse(text: String) = Unit
            override fun onRepick() = Unit
            override fun onRetryOffline() = Unit

            override fun onImageToggle() {
                asked.add("image")
            }

            override fun onDismiss() = Unit
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

    private companion object {
        const val SENTENCE = "しょくいんネオの職員さんに聞いたら非童帝くんいうから…いしゅつ外出したって"
        const val TRANSLATION = "Perguntei ao funcionário e ele disse que saiu por aí."
    }
}
