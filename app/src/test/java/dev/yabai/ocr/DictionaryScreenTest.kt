package dev.yabai.ocr

import android.content.Context
import android.view.ViewGroup
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import dev.yabai.ocr.core.dict.DictionaryCompiler
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
import org.robolectric.shadows.ShadowDialog
import java.io.File

/**
 * The dictionaries screen has to say which dictionary is which, and which ones are stale (R22, R26).
 *
 * Both complaints are about the same thing: the list was unreadable. The title comes from inside the
 * archive and is shared by every variant of a dictionary, so two rows could read «JMdict» and
 * nothing else; and a dictionary imported by an older version keeps the definitions it was compiled
 * with, which the user only found out because they had been told by hand.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w320dp-h640dp-mdpi")
class DictionaryScreenTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    /** A compiled dictionary as the repository finds it on disk: `meta.json` is the whole contract. */
    private fun install(id: String, title: String, source: String, compiledWith: Int, terms: Int, kanji: Int = 0) {
        val directory = File(context.filesDir, "dictionaries/$id")
        directory.mkdirs()
        File(directory, "meta.json").writeText(
            """
            {
              "title": "$title",
              "revision": "2025-01-01",
              "format": 3,
              "termCount": $terms,
              "kanjiCount": $kanji,
              "compiledWith": $compiledWith,
              "sourceName": "$source"
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `the list says where each dictionary came from and warns about the old format`() {
        install("jmdict-en", title = "JMdict", source = "JMdict (inglês)", compiledWith = DictionaryCompiler.FORMAT_VERSION - 1, terms = 120_000)
        install("kanjidic-pt", title = "KANJIDIC", source = "KANJIDIC (português)", compiledWith = DictionaryCompiler.FORMAT_VERSION, terms = 0)

        val activity = Robolectric.buildActivity(DictionaryActivity::class.java).setup().get()
        val texts = labels(activity.window.decorView as ViewGroup).map { it.text.toString() }

        assertTrue(
            "the screen does not say which «JMdict» this is: $texts",
            texts.any { it.contains("Origem: JMdict (inglês)") },
        )
        assertTrue(
            "the screen does not say which «KANJIDIC» this is: $texts",
            texts.any { it.contains("Origem: KANJIDIC (português)") },
        )

        val banner = texts.firstOrNull { it.startsWith("Compilados por uma versão antiga") }
        assertNotNull("no banner about the old format: $texts", banner)
        assertTrue(
            "the banner does not name the stale dictionary: $banner",
            banner!!.contains("JMdict (inglês)"),
        )
        assertFalse(
            "the banner blames a dictionary that is current: $banner",
            banner.contains("KANJIDIC"),
        )
    }

    @Test
    fun `the kanji card's dictionary can be chosen among those with kanji data`() {
        install("jmdict-en", title = "JMdict", source = "JMdict (inglês)", compiledWith = DictionaryCompiler.FORMAT_VERSION, terms = 120_000)
        install(
            "kanjidic-en",
            title = "KANJIDIC",
            source = "KANJIDIC (inglês)",
            compiledWith = DictionaryCompiler.FORMAT_VERSION,
            terms = 0,
            kanji = 13_000,
        )
        install(
            "kanjidic-pt",
            title = "KANJIDIC",
            source = "KANJIDIC (português)",
            compiledWith = DictionaryCompiler.FORMAT_VERSION,
            terms = 0,
            kanji = 2_000,
        )

        val activity = Robolectric.buildActivity(DictionaryActivity::class.java).setup().get()
        val all = context.getString(R.string.dictionary_kanji_all)
        val button = labels(activity.window.decorView as ViewGroup).first { it.text.toString() == all }
        button.performClick()

        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        assertEquals(
            "the chooser should offer «todos» plus each dictionary with kanji data, and no JMdict",
            listOf(
                all,
                "KANJIDIC · KANJIDIC (inglês)",
                "KANJIDIC · KANJIDIC (português)",
            ),
            items(dialog),
        )

        click(dialog, 2)

        assertEquals("kanjidic-pt", AppSettings(activity).kanjiDictionary)
        val after = labels(activity.window.decorView as ViewGroup).map { it.text.toString() }
        assertTrue(
            "the screen should now say which dictionary answers the kanji card: $after",
            after.contains("KANJIDIC · KANJIDIC (português)"),
        )
    }

    @Test
    fun `without a kanji dictionary the card's choice is not offered`() {
        install("jmdict-en", title = "JMdict", source = "JMdict (inglês)", compiledWith = DictionaryCompiler.FORMAT_VERSION, terms = 120_000)

        val activity = Robolectric.buildActivity(DictionaryActivity::class.java).setup().get()
        val texts = labels(activity.window.decorView as ViewGroup).map { it.text.toString() }

        assertFalse(
            "a chooser over nothing would be one more broken control: $texts",
            texts.contains(context.getString(R.string.dictionary_kanji_all)),
        )
    }

    private fun items(dialog: AlertDialog): List<String> {
        val list = dialog.listView
        return (0 until list.count).map { list.adapter.getItem(it).toString() }
    }

    private fun click(dialog: AlertDialog, index: Int) {
        val list: ListView = dialog.listView
        list.performItemClick(null, index, list.adapter.getItemId(index))
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
}
