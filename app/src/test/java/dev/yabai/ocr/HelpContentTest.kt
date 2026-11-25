package dev.yabai.ocr

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
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

    private fun texts(view: View): List<TextView> {
        val found = ArrayList<TextView>()
        if (view is TextView) found.add(view)
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) found.addAll(texts(view.getChildAt(index)))
        }
        return found
    }
}
