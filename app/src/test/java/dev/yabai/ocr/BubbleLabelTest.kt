package dev.yabai.ocr

import android.content.Context
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * R57: «Prender a bolha nas bordas» did not say *which* bubble, and the settings screen has more
 * than one thing that can be pinned. The label names the floating bubble now.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BubbleLabelTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun `the snap setting names the floating bubble`() {
        val label = context.getString(R.string.setting_bubble_on_edge)

        assertTrue("the label does not name the floating bubble: $label", label.contains("flutuante"))
    }
}
