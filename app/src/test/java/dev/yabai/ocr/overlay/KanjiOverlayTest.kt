package dev.yabai.ocr.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import dev.yabai.ocr.R
import dev.yabai.ocr.core.lens.PixelBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The kanji reader: pointing at a character on the frozen page (R17).
 *
 * What has to be true here is what a finger does: the strip is found under the point, the character
 * inside it is the one at that position, and «BACK» leaves the reader. The dictionary part of the
 * card is [KanjiCardTextTest]'s; this is the geometry and the gestures.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w320dp-h640dp-mdpi")
class KanjiOverlayTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    /** Six characters of 40 px each, down the page: 腹 is at 100..140, 減 at 180..220. */
    private val column = Rect(100, 100, 140, 340)

    private fun overlay(
        onKanji: (Int, Int) -> Unit = { _, _ -> },
        onDismiss: () -> Unit = {},
    ): KanjiOverlay {
        val page = Bitmap.createBitmap(320, 640, Bitmap.Config.ARGB_8888)
        return KanjiOverlay(context).apply {
            this.onKanjiTapped = onKanji
            this.onDismiss = onDismiss
            show(listOf(KanjiPiece("腹が減っては", column, vertical = true)), page)
            measure(
                View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY),
            )
            layout(0, 0, 320, 640)
        }
    }

    private fun touch(view: View, action: Int, x: Float, y: Float) {
        val event = MotionEvent.obtain(0L, 10L, action, x, y, 0)
        view.dispatchTouchEvent(event)
        event.recycle()
    }

    private fun tap(view: View, x: Float, y: Float) {
        touch(view, MotionEvent.ACTION_DOWN, x, y)
        touch(view, MotionEvent.ACTION_UP, x, y)
    }

    @Test
    fun `a tap on a character says which piece and which character`() {
        var picked: Pair<Int, Int>? = null
        val reader = overlay(onKanji = { piece, index -> picked = piece to index })

        tap(reader, 120f, 200f)

        assertEquals(0 to 2, picked)
    }

    @Test
    fun `the tapped character is outlined where it is printed`() {
        val reader = overlay()

        tap(reader, 120f, 200f)

        assertEquals(PixelBox(100, 180, 40, 40), reader.highlighted)
    }

    /** With the page zoomed 2× from the top-left corner, a tap at 240,400 is page 120,200. */
    @Test
    fun `a tap lands on the character it covers after zooming`() {
        var picked: Pair<Int, Int>? = null
        val reader = overlay(onKanji = { piece, index -> picked = piece to index })

        reader.zoomBy(2f, 0f, 0f)
        tap(reader, 240f, 400f)

        assertEquals(0 to 2, picked)
    }

    /** Regression R23: a window shorter than the capture must still point at the printed character. */
    @Test
    fun `a tap lands on the character when the window is shorter than the page`() {
        var picked: Pair<Int, Int>? = null
        val reader = KanjiOverlay(context).apply {
            onKanjiTapped = { piece, index -> picked = piece to index }
            onDismiss = {}
            show(
                listOf(KanjiPiece("\u8179\u304c\u6e1b\u3063\u3066\u306f", column, vertical = true)),
                Bitmap.createBitmap(320, 640, Bitmap.Config.ARGB_8888),
            )
            measure(
                View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
            )
            layout(0, 0, 320, 600)
        }

        // View 175 is page 186.7 in a 600-tall window: the third cell (180..220), not the second.
        tap(reader, 120f, 175f)

        assertEquals(0 to 2, picked)
    }

    /**
     * Regression R23: the mark is painted where the page under the finger is.
     *
     * Same stretch as the picker: a shorter window draws the page squeezed, and a mark drawn with the
     * page's own pixels would outline a character two rows away from the one that was tapped.
     */
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test
    fun `the mark is drawn over the character the finger is on`() {
        val reader = KanjiOverlay(context).apply {
            onKanjiTapped = { _, _ -> }
            onDismiss = {}
            show(
                listOf(KanjiPiece("\u8179\u304c\u6e1b\u3063\u3066\u306f", column, vertical = true)),
                Bitmap.createBitmap(320, 640, Bitmap.Config.ARGB_8888),
            )
            measure(
                View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
            )
            layout(0, 0, 320, 600)
        }

        // View 248 is page 264.5: the fifth character (page 260..300), drawn at 243..281.
        tap(reader, 120f, 248f)
        val shot = Bitmap.createBitmap(320, 600, Bitmap.Config.ARGB_8888)
        shot.eraseColor(Color.WHITE)
        reader.draw(Canvas(shot))

        val unmarked = shot.getPixel(120, 150)
        assertNotEquals(
            "the mark is not drawn where the finger is",
            unmarked,
            shot.getPixel(120, 250),
        )
    }

    @Test
    fun `a tap away from every strip picks nothing and keeps the reader open`() {        var picked: Pair<Int, Int>? = null
        var dismissed = false
        val reader = overlay({ piece, index -> picked = piece to index }, { dismissed = true })

        tap(reader, 300f, 600f)

        assertNull(picked)
        assertTrue("missing a strip must not close the reader", !dismissed)
    }

    @Test
    fun `a drag on a zoomed page picks nothing`() {
        var picked: Pair<Int, Int>? = null
        val reader = overlay(onKanji = { piece, index -> picked = piece to index })

        reader.zoomBy(2f, 0f, 0f)
        touch(reader, MotionEvent.ACTION_DOWN, 250f, 250f)
        touch(reader, MotionEvent.ACTION_MOVE, 150f, 150f)
        touch(reader, MotionEvent.ACTION_UP, 150f, 150f)

        assertNull(picked)
    }

    /** Before a kanji is tapped there is no card, and so no ✕ on it: the reader needs its own. */
    @Test
    fun `the reader can be closed before any kanji is found`() {
        var dismissed = false
        val reader = overlay(onDismiss = { dismissed = true })

        val close = labels(reader).firstOrNull {
            it.contentDescription == context.getString(R.string.result_close)
        }
        assertNotNull("the reader has no way out but BACK", close)
        close!!.performClick()

        assertTrue(dismissed)
    }

    @Test
    fun `BACK closes the reader`() {
        var dismissed = false
        val reader = overlay({ _, _ -> }, { dismissed = true })

        reader.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
        reader.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))

        assertTrue(dismissed)
    }

    @Test
    fun `the card shows the reading, the dictionary readings and the compounds`() {
        val reader = overlay()

        reader.showCard(
            KanjiCard(
                character = "腹",
                reading = "はら",
                word = "腹が減っては",
                onYomi = "フク",
                kunYomi = "はら",
                meaning = "barriga; estômago",
                compounds = listOf(KanjiWord("腹が減る", "はらがへる")),
            ),
        )

        val text = reader.cardText().orEmpty()

        assertTrue(text, text.contains("腹"))
        assertTrue(text, text.contains("はら"))
        assertTrue(text, text.contains("音: フク"))
        assertTrue(text, text.contains("訓: はら"))
        assertTrue(text, text.contains("barriga"))
        assertTrue(text, text.contains("Nesta tela: 腹が減る はらがへる"))
    }

    @Test
    fun `a card without a dictionary says so instead of showing empty readings`() {
        val reader = overlay()

        reader.showCard(
            KanjiCard(
                character = "腹",
                reading = "はら",
                word = null,
                onYomi = "",
                kunYomi = "",
                meaning = "",
                compounds = emptyList(),
                note = "Nenhum dicionário de kanji respondeu a este caractere.",
            ),
        )

        val text = reader.cardText().orEmpty()

        assertTrue(text, text.contains("Nenhum dicionário de kanji"))
        assertTrue("no empty 音 row", !text.contains("音:"))
    }

    /** Every label under [root], so a test can find the one the user would tap. */
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

    @Test
    fun `a new page takes the card of the old one away`() {
        val reader = overlay()
        reader.showCard(
            KanjiCard("腹", "はら", null, "フク", "はら", "barriga", emptyList()),
        )
        assertTrue(reader.cardText() != null)

        reader.show(
            listOf(KanjiPiece("腹", column, vertical = true)),
            Bitmap.createBitmap(320, 640, Bitmap.Config.ARGB_8888),
        )

        assertNull(reader.cardText())
        assertNull(reader.highlighted)
    }
}
