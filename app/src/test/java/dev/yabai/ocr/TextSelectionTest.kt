package dev.yabai.ocr

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.app.Activity
import android.graphics.Rect
import android.os.Looper
import android.text.Selection
import android.text.Spannable
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import dev.yabai.ocr.overlay.ResultOverlay
import dev.yabai.ocr.overlay.SelectionToolbar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The text menu of the panel: Copiar, Recortar, Colar and Selecionar tudo (R33b).
 *
 * Android's floating toolbar belongs to an activity window. In the service's
 * `TYPE_APPLICATION_OVERLAY` panel it never appeared on the phone, so a long press did nothing —
 * the text was marked selectable, but there was no way to copy a word out of a definition or a
 * translation. The panel draws its own bar in its own window; these tests drive the long press, the
 * selection it makes and each action, plus the rule that BACK closes the bar before the panel.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w320dp-h640dp-mdpi")
class TextSelectionTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    private val clipboard: ClipboardManager
        get() = context.getSystemService(ClipboardManager::class.java)

    @Test
    fun `a long press on the translation selects the word and opens the menu`() {
        val panel = translationPanel("gato preto")
        val text = label(panel, "gato preto")

        longPress(text, "gato preto".indexOf("preto"))

        assertEquals("the long press did not select the word", "preto", selected(text))
        assertNotNull("the text menu did not open", toolbar(panel))
    }

    @Test
    fun `Copiar puts the selected word on the clipboard and closes the menu`() {
        val panel = translationPanel("gato preto")
        val text = label(panel, "gato preto")
        longPress(text, "gato preto".indexOf("preto"))

        action(panel, SelectionToolbar.COPY_TAG).performClick()

        assertEquals(
            "the word did not reach the clipboard",
            "preto",
            clipboard.primaryClip?.getItemAt(0)?.text?.toString(),
        )
        assertNull("the menu stayed up after copying", toolbar(panel))
    }

    @Test
    fun `Recortar takes the word out of the search field`() {
        val panel = searchPanel()
        val editor = editor(panel)
        editor.setText("gato preto")
        layout(panel)

        longPress(editor, 6)
        assertEquals("the long press selected the wrong word", "preto", selected(editor))
        action(panel, SelectionToolbar.CUT_TAG).performClick()

        assertEquals("the word was not cut out", "gato ", editor.text.toString())
    }

    @Test
    fun `Colar writes the clipboard over the selection`() {
        val panel = searchPanel()
        val editor = editor(panel)
        editor.setText("gato preto")
        layout(panel)
        clipboard.setPrimaryClip(ClipData.newPlainText("test", "ねこ"))

        longPress(editor, 6)
        assertEquals("the long press selected the wrong word", "preto", selected(editor))
        action(panel, SelectionToolbar.PASTE_TAG).performClick()

        assertEquals("the clipboard was not pasted", "gato ねこ", editor.text.toString())
    }

    @Test
    fun `Recortar toma a palavra do campo e a deixa na área de transferência`() {
        val panel = searchPanel()
        val editor = editor(panel)
        editor.setText("gato preto")
        layout(panel)
        clipboard.setPrimaryClip(ClipData.newPlainText("test", "antigo"))

        longPress(editor, 6)
        action(panel, SelectionToolbar.CUT_TAG).performClick()

        assertEquals("the word was not cut out", "gato ", editor.text.toString())
        assertEquals(
            "the cut word did not reach the clipboard",
            "preto",
            clipboard.primaryClip?.getItemAt(0)?.text?.toString(),
        )
    }

    @Test
    fun `arrastar a alça do fim estende a seleção até o resto da frase`() {
        val panel = translationPanel("gato preto")
        val text = label(panel, "gato preto")
        longPress(text, 0)
        assertEquals("the long press selected the wrong word", "gato", selected(text))

        dragGrip(grip(panel, SelectionToolbar.END_HANDLE_TAG), panel, text, "gato preto".length)

        assertEquals("the grip did not extend the selection", "gato preto", selected(text))
        assertNotNull("the menu left while the selection grew", toolbar(panel))
    }

    @Test
    fun `arrastar a alça do início pega o que vinha antes da palavra`() {
        val panel = translationPanel("gato preto")
        val text = label(panel, "gato preto")
        longPress(text, 6)
        assertEquals("the long press selected the wrong word", "preto", selected(text))

        dragGrip(grip(panel, SelectionToolbar.START_HANDLE_TAG), panel, text, 0)

        assertEquals("the grip did not extend the selection", "gato preto", selected(text))
    }

    @Test
    fun `as alças vão embora com o menu`() {
        val panel = translationPanel("gato preto")
        longPress(label(panel, "gato preto"), 0)
        assertNotNull("no start grip", gripOrNull(panel, SelectionToolbar.START_HANDLE_TAG))
        assertNotNull("no end grip", gripOrNull(panel, SelectionToolbar.END_HANDLE_TAG))

        action(panel, SelectionToolbar.COPY_TAG).performClick()

        assertNull("the start grip stayed behind", gripOrNull(panel, SelectionToolbar.START_HANDLE_TAG))
        assertNull("the end grip stayed behind", gripOrNull(panel, SelectionToolbar.END_HANDLE_TAG))
    }

    @Test
    fun `a barra sai da frente enquanto a alça anda e volta ao soltar`() {
        val panel = translationPanel("gato preto")
        val text = label(panel, "gato preto")
        longPress(text, 0)
        val bar = toolbar(panel)!!
        val grip = grip(panel, SelectionToolbar.END_HANDLE_TAG)
        val (x, y) = panelPoint(panel, text, "gato preto".length)

        sendToGrip(grip, MotionEvent.ACTION_DOWN, grip.x + grip.width / 2f, grip.y + grip.height / 2f)
        sendToGrip(grip, MotionEvent.ACTION_MOVE, x, y)

        assertEquals("the bar stayed over the text", 0f, bar.alpha, 0.01f)
        assertEquals("the grip vanished under the finger", View.VISIBLE, grip.visibility)

        sendToGrip(grip, MotionEvent.ACTION_UP, x, y)
        assertEquals("the bar did not come back", 1f, bar.alpha, 0.01f)
    }

    @Test
    fun `o menu volta visível depois de fechar no meio do arrasto`() {
        val panel = translationPanel("gato preto")
        val text = label(panel, "gato preto")
        longPress(text, 0)
        val bar = toolbar(panel) as SelectionToolbar
        val grip = grip(panel, SelectionToolbar.END_HANDLE_TAG)
        val (x, y) = panelPoint(panel, text, "gato preto".length)

        sendToGrip(grip, MotionEvent.ACTION_DOWN, grip.x + grip.width / 2f, grip.y + grip.height / 2f)
        sendToGrip(grip, MotionEvent.ACTION_MOVE, x, y)
        // O VOLTAR fecha o menu no meio do gesto; a barra estava transparente naquele instante.
        bar.hide()

        longPress(text, 0)

        assertEquals(
            "the menu came back invisible",
            1f,
            toolbar(panel)?.alpha ?: 0f,
            0.01f,
        )
    }

    @Test
    fun `Selecionar tudo selects the whole text and keeps the menu`() {
        val panel = translationPanel("gato preto")
        val text = label(panel, "gato preto")
        longPress(text, 0)

        action(panel, SelectionToolbar.SELECT_ALL_TAG).performClick()

        assertEquals(0, text.selectionStart)
        assertEquals("gato preto".length, text.selectionEnd)
        assertNotNull("the menu left after «Selecionar tudo»", toolbar(panel))
    }

    @Test
    fun `enquanto o menu está aberto, o toque vai para o botão e não para o véu`() {
        val panel = translationPanel("gato preto")
        // A real window: a tap becomes a click only through a posted runnable, which needs a looper.
        Robolectric.buildActivity(Activity::class.java).setup().get().setContentView(panel)
        layout(panel)
        longPress(label(panel, "gato preto"), 0)
        layout(panel)

        val bar = toolbar(panel)!!
        val copy = action(panel, SelectionToolbar.COPY_TAG)
        val x = bar.x + copy.left + copy.width / 2f
        val y = bar.y + copy.top + copy.height / 2f
        tapAt(panel, x, y)

        assertEquals(
            "the tap on «Copiar» landed behind the menu",
            "gato",
            clipboard.primaryClip?.getItemAt(0)?.text?.toString(),
        )
        assertNull("the menu stayed up after the tap", toolbar(panel))
    }

    @Test
    fun `Copiar copia o que foi selecionado mesmo se a seleção se perder`() {
        val panel = translationPanel("gato preto")
        val text = label(panel, "gato preto")
        longPress(text, "gato preto".indexOf("preto"))
        forgetSelection(text)

        action(panel, SelectionToolbar.COPY_TAG).performClick()

        assertEquals(
            "the word selected by the long press was lost",
            "preto",
            clipboard.primaryClip?.getItemAt(0)?.text?.toString(),
        )
    }

    @Test
    fun `Recortar tira a palavra lembrada quando o campo perde a seleção`() {
        val panel = searchPanel()
        val editor = editor(panel)
        editor.setText("gato preto")
        layout(panel)

        longPress(editor, 6)
        forgetSelection(editor)
        action(panel, SelectionToolbar.CUT_TAG).performClick()

        assertEquals("the word was not cut out", "gato ", editor.text.toString())
    }

    @Test
    fun `Colar escreve sobre a palavra lembrada quando o campo perde a seleção`() {
        val panel = searchPanel()
        val editor = editor(panel)
        editor.setText("gato preto")
        layout(panel)
        clipboard.setPrimaryClip(ClipData.newPlainText("test", "ねこ"))

        longPress(editor, 6)
        forgetSelection(editor)
        action(panel, SelectionToolbar.PASTE_TAG).performClick()

        assertEquals("the clipboard was not pasted", "gato ねこ", editor.text.toString())
    }

    @Test
    fun `the menu stays inside the panel`() {
        val panel = translationPanel("gato preto")
        longPress(label(panel, "gato preto"), 0)

        val bar = toolbar(panel)!!
        assertTrue("the menu hangs off the left edge", bar.x >= 0f)
        assertTrue("the menu hangs off the right edge", bar.x + bar.width <= panel.width)
        assertTrue("the menu hangs off the top", bar.y >= 0f)
        assertTrue("the menu hangs off the bottom", bar.y + bar.height <= panel.height)
    }

    @Test
    fun `BACK closes the menu before the panel`() {
        var dismissals = 0
        val panel = translationPanel("gato preto")
        panel.callbacks = object : ResultOverlay.Callbacks {
            override fun onCopyOriginal(text: String) = Unit
            override fun onCopyTranslation(text: String) = Unit
            override fun onAnalyse(text: String) = Unit
            override fun onRepick() = Unit
            override fun onRetryOffline() = Unit
            override fun onImageToggle() = Unit
            override fun onDictionaryPicked(title: String?) = Unit
            override fun onAskAi(text: String) = Unit
            override fun onDismiss() {
                dismissals++
            }
        }
        longPress(label(panel, "gato preto"), 0)

        panel.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
        panel.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))

        assertNull("BACK left the text menu on screen", toolbar(panel))
        assertEquals("the first BACK closed the panel instead of only the menu", 0, dismissals)
    }

    // --- helpers --------------------------------------------------------------------------------

    private fun translationPanel(text: String): ResultOverlay = ResultOverlay(context).apply {
        showResult(
            text = "職員",
            meta = "Google Lens · 1200 ms · ja",
            notes = emptyList(),
            offlineAvailable = true,
            showLines = false,
            segmentLines = emptyList(),
            canRepick = false,
        )
        setTranslation(ResultOverlay.TranslationState.Done(text))
        layout(this)
    }

    private fun searchPanel(): ResultOverlay = ResultOverlay(context).apply {
        showSearch(titles = emptyList(), selected = null, dictionaryCount = 0)
        layout(this)
    }

    private fun toolbar(panel: View): View? = panel.findViewWithTag(SelectionToolbar.TAG)

    private fun gripOrNull(panel: View, tag: String): View? = panel.findViewWithTag(tag)

    private fun grip(panel: View, tag: String): View {
        val found = gripOrNull(panel, tag)
        assertNotNull("the selection has no «$tag» grip", found)
        return found!!
    }

    /**
     * Drags a grip to the character [offset], through the touch listener the phone uses.
     *
     * The grip is a direct child of the panel, so each event is delivered in the grip's own
     * coordinates — which are recomputed after every move, because the panel keeps the grip centred
     * on the edge it is moving.
     */
    private fun dragGrip(grip: View, panel: View, text: TextView, offset: Int) {
        layout(panel)
        val (x, y) = panelPoint(panel, text, offset)

        sendToGrip(grip, MotionEvent.ACTION_DOWN, grip.x + grip.width / 2f, grip.y + grip.height / 2f)
        sendToGrip(grip, MotionEvent.ACTION_MOVE, x, y)
        sendToGrip(grip, MotionEvent.ACTION_UP, x, y)
    }

    /** The panel point of the character [offset] in [text], in the panel's coordinates. */
    private fun panelPoint(panel: View, text: TextView, offset: Int): Pair<Float, Float> {
        layout(panel)
        val textLayout = text.layout ?: error("the text was never laid out")
        val line = textLayout.getLineForOffset(offset)
        val origin = Rect()
        (panel as ViewGroup).offsetDescendantRectToMyCoords(text, origin)
        val x = origin.left + text.totalPaddingLeft + textLayout.getPrimaryHorizontal(offset)
        val y = origin.top + text.totalPaddingTop +
            (textLayout.getLineTop(line) + textLayout.getLineBottom(line)) / 2f
        return x to y
    }

    private fun sendToGrip(grip: View, action: Int, panelX: Float, panelY: Float) {
        val event = MotionEvent.obtain(0, 0, action, panelX - grip.x, panelY - grip.y, 0)
        grip.dispatchTouchEvent(event)
        event.recycle()
    }

    private fun action(panel: View, tag: String): View {
        val found = panel.findViewWithTag<View>(tag)
        assertNotNull("the menu has no «$tag» action", found)
        return found!!
    }

    private fun editor(panel: ViewGroup): EditText {
        val found = labels(panel).firstOrNull { it is EditText }
        assertNotNull("the search panel has no field", found)
        return found as EditText
    }

    private fun label(root: ViewGroup, text: String): TextView {
        val found = labels(root).firstOrNull { it.text.toString() == text }
        assertNotNull("the panel has no label «$text»", found)
        return found!!
    }

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

    /**
     * A real long press at [index]: the touch records the finger, exactly as a finger down would,
     * and the long click then runs the panel's own listener with that position.
     */
    private fun longPress(view: TextView, index: Int) {
        val textLayout = view.layout
        assertNotNull("the text was never laid out", textLayout)
        val line = textLayout.getLineForOffset(index)
        val x = textLayout.getPrimaryHorizontal(index) + view.totalPaddingLeft
        val y = (textLayout.getLineTop(line) + textLayout.getLineBottom(line)) / 2f +
            view.totalPaddingTop

        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, x, y, 0)
        view.dispatchTouchEvent(down)
        assertTrue("the long press was not taken", view.performLongClick())
        val cancel = MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, x, y, 0)
        view.dispatchTouchEvent(cancel)
        down.recycle()
        cancel.recycle()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun selected(view: TextView): String =
        view.text.subSequence(view.selectionStart, view.selectionEnd).toString()

    /** Drops the selection the way the IME or the window can between the two taps (R35a). */
    private fun forgetSelection(view: TextView) {
        val text = view.text
        if (text is Spannable) Selection.removeSelection(text) else view.clearFocus()
    }

    /** A real tap delivered to the panel, so the layers are hit-tested as on the phone. */
    private fun tapAt(view: View, x: Float, y: Float) {
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, x, y, 0)
        view.dispatchTouchEvent(down)
        val up = MotionEvent.obtain(0, 10, MotionEvent.ACTION_UP, x, y, 0)
        view.dispatchTouchEvent(up)
        down.recycle()
        up.recycle()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun layout(view: View): View {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        return view
    }
}
