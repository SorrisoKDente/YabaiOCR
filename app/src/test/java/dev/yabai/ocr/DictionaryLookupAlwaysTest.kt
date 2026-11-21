package dev.yabai.ocr

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * «Consultar os dicionários instalados» was a switch with no other job: the panel looked the text up
 * when it was on, and showed nothing under «Dicionário» when it was off (R46a).
 *
 * The lookup is part of the result now, so two things have to stay true: the main screen offers no
 * way to turn it off, and no stored value can turn it off either — someone who left the switch
 * unchecked keeps consulting after the update, which is the decision the ROADMAP entry records.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "pt-rBR")
class DictionaryLookupAlwaysTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @After
    fun tearDown() {
        prefs().edit().clear().commit()
    }

    @Test
    fun `the main screen has no switch for the dictionary lookup`() {
        // The old switch, left off: what a phone that used the option carries.
        prefs().edit().putBoolean("dictionary_lookup", false).commit()

        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val texts = activity.window.decorView.textsOf(CheckBox::class.java)

        for (label in LABELS) {
            assertTrue(
                "the main screen still offers «$label»: every checkbox is $texts",
                texts.none { it == label },
            )
        }
    }

    /**
     * The card keeps the two things that say *what* is consulted: how many dictionaries are
     * installed and the button that manages them. Removing the switch must not empty the card.
     */
    @Test
    fun `the dictionary card still says what is installed`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

        val summary = activity.findViewById<TextView>(R.id.dictionarySummary)
        assertTrue(
            "the card lost its summary: «${summary.text}»",
            summary.text.isNotEmpty(),
        )
        assertEquals(
            activity.getString(R.string.dictionary_manage_button),
            activity.findViewById<Button>(R.id.btnManageDictionaries).text.toString(),
        )
    }

    /**
     * No setting can hold the lookup down any more. A reintroduced preference is what a partial
     * revert looks like — the switch would be gone from the screen while an old value still decided
     * whether the panel had a dictionary section at all.
     */
    @Test
    fun `no setting exposes a dictionary lookup toggle`() {
        val toggles = AppSettings::class.java.methods
            .map { it.name.lowercase() }
            .filter { it.contains("dictionary") && it.contains("lookup") }

        assertTrue("AppSettings still exposes a dictionary lookup toggle: $toggles", toggles.isEmpty())
    }

    // --- helpers --------------------------------------------------------------------------------

    private fun prefs() = context.getSharedPreferences(AppSettings.PREFS_NAME, Context.MODE_PRIVATE)

    /** Every label the removed switch was ever written with, in the two languages of the app. */
    private val LABELS get() = listOf(
        "Consultar os dicionários instalados",
        "Query installed dictionaries",
    )

    /** The text of every [type] under this view, in tree order. */
    private fun <T : View> View.textsOf(type: Class<T>): List<String> {
        val found = ArrayList<String>()
        if (type.isInstance(this)) {
            val text = (this as? TextView)?.text?.toString().orEmpty()
            if (text.isNotEmpty()) found.add(text)
        }
        if (this is ViewGroup) {
            for (index in 0 until childCount) found.addAll(getChildAt(index).textsOf(type))
        }
        return found
    }
}
