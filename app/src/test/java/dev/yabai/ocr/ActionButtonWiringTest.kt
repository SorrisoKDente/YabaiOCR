package dev.yabai.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import dev.yabai.ocr.core.dict.DictionaryCompiler
import dev.yabai.ocr.overlay.ACTION_BUTTON_TAG
import dev.yabai.ocr.overlay.ActionGrid
import dev.yabai.ocr.overlay.OverlayController
import dev.yabai.ocr.overlay.ResultOverlay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * R46c, where the colour is decided: the grey buttons of the app become the accent, «Remover» the
 * desktop's red, and the engine priority list keeps only the arrows the dictionary list already uses.
 *
 * [dev.yabai.ocr.overlay.ActionButtonThemeTest] owns the colours themselves; this file checks that
 * the two screens actually go through that button, which is what a revert would quietly undo.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "pt-rBR")
class ActionButtonWiringTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    /**
     * The priority list used to say «▲ Subir» / «▼ Descer» on small grey buttons. The glyph alone is
     * what the dictionary list shows, and the words move to the content description — a screen
     * reader still hears them, the row stops being two thirds text.
     */
    @Test
    fun `the engine priority list uses the accent arrows and no words`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val buttons = activity.texts()

        val arrows = buttons.filter { it.text.toString() in setOf("↑", "↓") }
        assertEquals("every engine should offer both moves, except at the ends: $arrows", 4, arrows.size)
        for (arrow in arrows) {
            assertEquals(
                "«${arrow.text}» is not the panel's button, so it is still a grey Material button",
                ACTION_BUTTON_TAG,
                arrow.tag,
            )
        }
        assertEquals(
            listOf(context.getString(R.string.dictionary_up), context.getString(R.string.dictionary_down))
                .sorted(),
            arrows.map { it.contentDescription.toString() }.distinct().sorted(),
        )
        assertTrue(
            "the list still spells the direction out: ${buttons.map { it.text }}",
            buttons.none { it.text.contains("Subir") || it.text.contains("Descer") },
        )
    }

    /**
     * «Remover» is red wherever it appears: it is the only button of the app that throws a dictionary
     * away. The row in «Disponíveis» turns into the same «Remover» once the dictionary is installed,
     * and it kept the accent blue — which read as another «Baixar» (R47b).
     */
    @Test
    fun `every remove button is the danger fill`() {
        // The catalog's own id, so the dictionary also appears under «Disponíveis» — the row that
        // used to paint «Remover» blue.
        install("jmdict-en", title = "JMdict", source = "JMdict (inglês)")

        val activity = Robolectric.buildActivity(DictionaryActivity::class.java).setup().get()
        val removes = activity.texts().filter {
            it.text.toString() == context.getString(R.string.dictionary_delete)
        }
        assertTrue(
            "the installed card and the available row should both offer «Remover»: " +
                activity.texts().map { it.text },
            removes.size >= 2,
        )
        for (remove in removes) {
            assertEquals(
                "«Remover» is not the panel's button",
                ACTION_BUTTON_TAG,
                remove.tag,
            )
            val fill = (remove.background as? GradientDrawable)?.color?.defaultColor
            assertEquals("«Remover» is not red: ${remove.parent}", context.getColor(R.color.warn), fill)
        }
    }

    /**
     * R47c: the engine priority list drew «↑↓» as one block because the two buttons had no gap.
     * They are two moves and have to look like the dictionary list's pair.
     */
    @Test
    fun `the engine priority arrows are two separated buttons`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val arrows = activity.texts().filter { it.text.toString() in setOf("↑", "↓") }
        val down = arrows.filter { it.text.toString() == "↓" }

        assertTrue("the priority list has no down arrow: ${arrows.map { it.text }}", down.isNotEmpty())
        for (arrow in down) {
            val params = arrow.layoutParams as? LinearLayout.LayoutParams
            assertNotNull("the down arrow has no row params", params)
            assertTrue(
                "the two arrows are still glued together: marginStart=${params!!.marginStart}",
                params.marginStart > 0,
            )
        }
    }

    /**
     * R47g: «Fechar» is red and always the bottom-left action of the result card, wherever the other
     * buttons end up — the way out has to be in the same place every time. R50: it starts the last
     * row, sharing it instead of owning one, so a sideways phone keeps the dictionary text.
     */
    @Test
    fun `the result close is red and is the bottom-left action`() {
        val panel = ResultOverlay(context).apply {
            showResult(
                text = "職員",
                meta = "Google Lens · 1200 ms · ja",
                notes = emptyList(),
                showLines = false,
                segmentLines = emptyList(),
                canRepick = true,
                image = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888),
                canAskAi = false,
                canRetakePhoto = true,
            )
        }
        val grid = panel.findGrid()
        assertNotNull("the result card has no action grid: ${panel.texts().map { it.text }}", grid)

        val lastRow = grid!!.getChildAt(grid.childCount - 1) as ViewGroup
        val close = lastRow.getChildAt(0) as TextView
        assertEquals(
            "«Fechar» does not start the last row: ${lastRow.texts().map { it.text }}",
            context.getString(R.string.result_close),
            close.text.toString(),
        )
        assertEquals(
            "«Fechar» is not red",
            context.getColor(R.color.warn),
            (close.background as? GradientDrawable)?.color?.defaultColor,
        )
    }

    /**
     * The panel's «✕» paints no fill of its own, so it cannot inherit the accent's white: on the
     * light panel that was a white cross on a white card.
     */
    @Test
    fun `the result panel's close cross is readable on the light panel`() {
        val theme = lightTheme()
        val panel = ResultOverlay(theme).apply {
            showResult(
                text = "職員",
                meta = "Google Lens · 1200 ms · ja",
                notes = emptyList(),
                showLines = false,
                segmentLines = emptyList(),
                canRepick = false,
                canAskAi = false,
            )
        }

        val cross = panel.texts().firstOrNull { it.text.toString() == "✕" }
        assertNotNull("the panel has no close cross: ${panel.texts().map { it.text }}", cross)
        assertEquals(theme.getColor(R.color.text_primary), cross!!.currentTextColor)
        assertNotEquals("the cross is white on the light panel", Color.WHITE, cross.currentTextColor)
    }

    /**
     * «Encerrar o OCR flutuante?» is a question whose safe answer must stay the loud one: red on the
     * accent would be red on blue (unreadable) *and* would make closing the easy choice.
     */
    @Test
    fun `the close confirmation keeps its red on the panel, not on the accent`() {
        val theme = lightTheme()
        val answers = OverlayController(theme).buildCloseConfirm().texts()
            .filter { (it as? View)?.tag == ACTION_BUTTON_TAG }

        assertEquals(
            listOf("Cancelar", "Encerrar"),
            answers.map { it.text.toString() },
        )
        assertEquals(theme.getColor(R.color.text_primary), answers[0].currentTextColor)
        assertEquals(theme.getColor(R.color.warn), answers[1].currentTextColor)
        for (answer in answers) {
            assertEquals(
                "«${answer.text}» was filled, so its text sits on the accent",
                0,
                (answer.background as? GradientDrawable)?.color?.defaultColor,
            )
        }
    }

    // --- helpers --------------------------------------------------------------------------------

    /** The light theme, whatever mode the test run booted in. */
    private fun lightTheme(): Context {
        RuntimeEnvironment.setQualifiers("+notnight")
        return ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_YabaiOcr)
    }

    /** A compiled dictionary as the repository finds it: `meta.json` is the whole contract. */
    private fun install(id: String, title: String, source: String) {
        val directory = File(context.filesDir, "dictionaries/$id")
        directory.mkdirs()
        File(directory, "meta.json").writeText(
            """
            {
              "title": "$title",
              "revision": "2025-01-01",
              "format": 3,
              "termCount": 120000,
              "kanjiCount": 0,
              "compiledWith": ${DictionaryCompiler.FORMAT_VERSION},
              "sourceName": "$source"
            }
            """.trimIndent(),
        )
    }

    /** Every [TextView] of the screen or panel, in tree order. */
    private fun android.app.Activity.texts(): List<TextView> = (window.decorView as ViewGroup).texts()

    /** The action grid of the panel, found by its tag — the panel builds its views in code. */
    private fun View.findGrid(): ActionGrid? {
        if (this is ActionGrid) return this
        if (this is ViewGroup) {
            for (index in 0 until childCount) getChildAt(index).findGrid()?.let { return it }
        }
        return null
    }

    private fun View.texts(): List<TextView> {
        val found = ArrayList<TextView>()
        if (this is TextView) found.add(this)
        if (this is ViewGroup) {
            for (index in 0 until childCount) found.addAll(getChildAt(index).texts())
        }
        return found
    }
}
