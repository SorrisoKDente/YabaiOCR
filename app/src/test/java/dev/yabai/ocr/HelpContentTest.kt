package dev.yabai.ocr

import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The help screen has to explain the actions the bubble offers, not only the settings.
 *
 * The camera (R46d) and the bubble's own gestures are the newest way in, and a manual that stops at
 * the engine comparison leaves them undiscoverable. This guard inflates the real screen and requires
 * every entry it names to be on it.
 *
 * R49 added the button reference as the first item (each button with its icon on the left), retired
 * the manga-ocr wording in favour of PP-OCRv6 and dropped the explanation of where the camera
 * controls sit when the phone is sideways — the user can see that on the screen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w320dp-h640dp-mdpi")
class HelpContentTest {

    @Test
    fun `the help screen explains the camera and the bubble's gestures`() {
        val activity = Robolectric.buildActivity(HelpActivity::class.java).setup().get()
        val shown = texts(activity.findViewById(android.R.id.content)).map { it.text.toString() }

        val entries = listOf(
            R.string.help_tip_camera_title,
            R.string.help_tip_camera_desc,
            R.string.help_tip_bubble_title,
            R.string.help_tip_bubble_desc,
        )
        for (id in entries) {
            val label = activity.getString(id)
            assertTrue(
                "the help screen does not show «${label.take(40)}»",
                label in shown,
            )
        }
    }

    @Test
    fun `the button reference opens the manual and lists every bubble button`() {
        val activity = Robolectric.buildActivity(HelpActivity::class.java).setup().get()
        val shown = texts(activity.findViewById(android.R.id.content)).map { it.text.toString() }

        val buttonsTitle = activity.getString(R.string.help_buttons_title)
        val firstTip = activity.getString(R.string.help_tip_area_title)
        assertTrue("the help screen has no button reference", buttonsTitle in shown)
        assertTrue(
            "the button reference is not the first item of the help",
            shown.indexOf(buttonsTitle) < shown.indexOf(firstTip),
        )

        val buttons = listOf(
            R.string.help_button_full_screen_title to R.string.help_button_full_screen_desc,
            R.string.help_button_region_title to R.string.help_button_region_desc,
            R.string.help_button_camera_title to R.string.help_button_camera_desc,
            R.string.help_button_kanji_title to R.string.help_button_kanji_desc,
            R.string.help_button_dictionary_title to R.string.help_button_dictionary_desc,
            R.string.help_button_settings_title to R.string.help_button_settings_desc,
            R.string.help_button_stop_title to R.string.help_button_stop_desc,
        )
        for ((nameId, descId) in buttons) {
            assertTrue(
                "the button reference lost «${activity.getString(nameId)}»",
                activity.getString(nameId) in shown,
            )
            assertTrue(
                "the button reference lost the explanation of «${activity.getString(nameId)}»",
                activity.getString(descId) in shown,
            )
        }

        // Each row draws its icon at the left; the kanji row uses the 漢 glyph, so six vectors.
        val list = activity.findViewById<LinearLayout>(R.id.helpButtonList)
        assertEquals("the button reference lost a row", buttons.size, list.childCount)
        val icons = ArrayList<ImageView>()
        images(list, icons)
        assertEquals("the button reference lost an icon", 6, icons.count { it.drawable != null })
    }

    @Test
    fun `the engine help names PP-OCRv6 and no engine the app no longer has`() {
        val activity = Robolectric.buildActivity(HelpActivity::class.java).setup().get()
        val engines = activity.getString(R.string.help_tip_engines_desc)

        assertTrue("the engine help does not mention PP-OCRv6", engines.contains("PP-OCRv6"))
        assertTrue(
            "the engine help still offers manga-ocr",
            !engines.contains("manga-ocr", ignoreCase = true),
        )
    }

    @Test
    fun `the camera help describes the actions, not where the controls sit when the phone turns`() {
        val activity = Robolectric.buildActivity(HelpActivity::class.java).setup().get()
        val camera = activity.getString(R.string.help_tip_camera_desc)

        assertTrue("the camera help lost «Cancelar»", camera.contains("Cancel", ignoreCase = true))
        val removed = listOf("mesmo quando a tela gira", "giram com o quadro", "even when the screen turns", "the controls do not")
        assertTrue(
            "the camera help still explains where the shutter goes when the screen turns",
            removed.none { camera.contains(it, ignoreCase = true) },
        )
    }

    private fun texts(view: View): List<TextView> {
        val found = ArrayList<TextView>()
        if (view is TextView) found.add(view)
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) found.addAll(texts(view.getChildAt(index)))
        }
        return found
    }

    private fun images(view: View, found: MutableList<ImageView>) {
        if (view is ImageView) found.add(view)
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) images(view.getChildAt(index), found)
        }
    }
}
