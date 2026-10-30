package dev.yabai.ocr

import android.content.Context
import android.widget.CheckBox
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
 * dictionary the text as typed or without its `（しょくいん）` — and the switch for it lives on the
 * dictionaries screen. What the panel shows is not this function's business: it keeps the typed text
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
    fun `the choice on the dictionaries screen is saved`() {
        AppSettings(context).stripSearchReadings = false
        val activity = Robolectric.buildActivity(DictionaryActivity::class.java).setup().get()

        val check = activity.window.decorView
            .findViewWithTag<CheckBox>(DictionaryActivity.STRIP_READINGS_TAG)
        assertNotNull("the dictionaries screen has no such option", check)
        assertFalse("the option does not show the default", check.isChecked)

        check.isChecked = true

        assertTrue("the option did not save the choice", AppSettings(activity).stripSearchReadings)
    }
}
