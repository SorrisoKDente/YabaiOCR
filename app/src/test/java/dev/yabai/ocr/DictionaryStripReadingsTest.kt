package dev.yabai.ocr

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The kana readings a copied word brings to the dictionary box (R35d).
 *
 * The policy lives in [AppSettings.dictionaryQuery] — one place decides whether the box asks the
 * dictionary the text as typed or without its `（しょくいん）` — and the switch for it lives in the
 * **dictionaries card of the main menu**, beside the dictionaries and the button that manages them
 * (R46b; it used to be a card of its own on the dictionaries screen, which is where the switch was
 * least looked for). What the panel shows is not this function's business: it keeps the typed text
 * on screen, so the cleanup can never surprise anyone by changing what they are reading.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w320dp-h640dp-mdpi")
class DictionaryStripReadingsTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun `off by default the search asks exactly what was typed`() {
        val settings = AppSettings(context)

        assertFalse("the cleanup should start off", settings.stripSearchReadings)
        assertEquals(
            "職員（しょくいん）さん",
            settings.dictionaryQuery("職員（しょくいん）さん"),
        )
    }

    @Test
    fun `on, the query loses its readings in both bracket styles`() {
        val settings = AppSettings(context)
        settings.stripSearchReadings = true

        assertEquals("職員さん", settings.dictionaryQuery("職員（しょくいん）さん"))
        assertEquals("職員さん", settings.dictionaryQuery("職員(しょくいん)さん"))
        assertEquals("猫", settings.dictionaryQuery("猫（ねこ）"))
    }

    @Test
    fun `a bracket that is not a reading is still asked`() {
        val settings = AppSettings(context)
        settings.stripSearchReadings = true

        assertEquals("猫(cat)", settings.dictionaryQuery("猫(cat)"))
        assertEquals("猫（ねこ1）", settings.dictionaryQuery("猫（ねこ1）"))
    }

    @Test
    fun `the choice in the main menu is saved`() {
        AppSettings(context).stripSearchReadings = false
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

        val check = activity.findViewById<CheckBox>(R.id.checkStripReadings)
        assertNotNull("the main screen has no such option", check)
        assertFalse("the option does not show the default", check.isChecked)

        check.isChecked = true

        assertTrue("the option did not save the choice", AppSettings(activity).stripSearchReadings)
    }

    /**
     * One switch, in one place: the dictionaries screen must not keep its own copy of the option.
     * Two copies would write the same preference from two screens, and the one on the dictionary
     * screen would look authoritative for the search it no longer owns.
     */
    @Test
    fun `the dictionaries screen no longer offers the option`() {
        val activity = Robolectric.buildActivity(DictionaryActivity::class.java).setup().get()
        val label = activity.getString(R.string.dictionary_strip_readings)
        val hint = activity.getString(R.string.dictionary_strip_readings_hint)

        val texts = activity.window.decorView.texts()
        assertTrue("the dictionaries screen still has «$label»: $texts", label !in texts)
        assertTrue("the dictionaries screen still explains «$hint»: $texts", hint !in texts)
    }

    // --- helpers --------------------------------------------------------------------------------

    /** The text of every [TextView] under this view, in tree order. */
    private fun View.texts(): List<String> {
        val found = ArrayList<String>()
        if (this is TextView && text.isNotEmpty()) found.add(text.toString())
        if (this is ViewGroup) {
            for (index in 0 until childCount) found.addAll(getChildAt(index).texts())
        }
        return found
    }
}
