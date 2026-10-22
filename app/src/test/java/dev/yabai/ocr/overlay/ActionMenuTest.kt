package dev.yabai.ocr.overlay

import android.content.Context
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import dev.yabai.ocr.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Tests for the bubble's action menu.
 *
 * The menu used to be four rows of text in a card at the bottom of the screen. It is now a row of
 * icons under the bubble: nothing on screen says what each one does, so the wiring — which icon is
 * which action, and where the row ends up — is exactly the part that has to be pinned down here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w320dp-h640dp-mdpi")
class ActionMenuTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    /** The wording the menu used to show lives on in the content description. */
    @Test
    fun `every action is an icon with an accessible name`() {
        val strip = menuWith().buildMenuStrip()
        assertEquals(
            listOf(
                context.getString(R.string.overlay_full_screen),
                context.getString(R.string.overlay_select_area),
                context.getString(R.string.overlay_kanji),
                context.getString(R.string.overlay_dictionary),
                context.getString(R.string.overlay_settings),
                context.getString(R.string.overlay_close_service),
            ),
            items(strip).map { it.contentDescription?.toString() },
        )
    }

    /** Six actions next to each other, none of them hidden. */
    @Test
    fun `the strip shows six actions in a row`() {
        val strip = menuWith().buildMenuStrip()
        assertEquals(6, strip.childCount)
        strip.measure(0, 0)
        for (item in items(strip)) {
            assertTrue("an action has no size", item.layoutParams.width > 0)
            when (item) {
                is ImageView -> assertTrue("an icon has no drawing", item.drawable != null)
                is TextView -> assertEquals("漢", item.text.toString())
                else -> error("the strip holds something that is not an action")
            }
        }
    }

    /** The kanji action is the character itself, and says so out loud for a screen reader. */
    @Test
    fun `the kanji action is drawn as the character`() {
        val strip = menuWith().buildMenuStrip()

        val glyph = items(strip)[2] as TextView

        assertEquals("漢", glyph.text.toString())
        assertEquals(context.getString(R.string.overlay_kanji), glyph.contentDescription.toString())
    }

    /** Each icon has to run its own action — swapping two of them would be invisible otherwise. */
    @Test
    fun `each icon runs its own action`() {
        val expected = listOf("full screen", "region", "kanji", "dictionary", "settings", "stop")
        for ((index, name) in expected.withIndex()) {
            val asked = ArrayList<String>()
            val strip = menuWith(asked).buildMenuStrip()

            items(strip)[index].performClick()

            assertEquals("icon $index", listOf(name), asked)
        }
    }

    /** Under the bubble when there is room, above it when there is not, and never off the screen. */
    @Test
    fun `the strip stays on screen wherever the bubble is`() {
        val menu = menuWith()
        val screen = context.resources.displayMetrics

        for (bubbleX in listOf(0, 160, screen.widthPixels)) {
            for (bubbleY in listOf(0, 200, screen.heightPixels)) {
                val strip = menu.buildMenuStrip()
                val (x, y) = menu.menuAnchor(bubbleX, bubbleY, strip)

                assertTrue("x=$x for a bubble at $bubbleX", x >= 0)
                assertTrue(
                    "x=$x, width=${strip.measuredWidth}, screen=${screen.widthPixels}",
                    x + strip.measuredWidth <= screen.widthPixels,
                )
                assertTrue("y=$y for a bubble at $bubbleY", y >= 0)
                assertTrue(
                    "y=$y, height=${strip.measuredHeight}, screen=${screen.heightPixels}",
                    y + strip.measuredHeight <= screen.heightPixels,
                )
            }
        }
    }

    @Test
    fun `the strip is placed below the bubble, and above it when it does not fit`() {
        val menu = menuWith()
        val screen = context.resources.displayMetrics

        val below = menu.buildMenuStrip()
        val (_, underY) = menu.menuAnchor(bubbleX = 100, bubbleY = 100, strip = below)
        assertTrue("expected the strip under the bubble, got y=$underY", underY > 100)

        val above = menu.buildMenuStrip()
        val bubbleY = screen.heightPixels - 60
        val (_, overY) = menu.menuAnchor(bubbleX = 100, bubbleY = bubbleY, strip = above)
        assertTrue("expected the strip above the bubble, got y=$overY", overY < bubbleY)
    }

    // --- helpers --------------------------------------------------------------------------------

    private fun menuWith(asked: MutableList<String> = ArrayList()): OverlayController =
        OverlayController(context).apply { callbacks = RecordingCallbacks(asked) }

    /** The strip's actions, in the order they are drawn. */
    private fun items(strip: ViewGroup): List<android.view.View> =
        (0 until strip.childCount).map { strip.getChildAt(it) }
}
