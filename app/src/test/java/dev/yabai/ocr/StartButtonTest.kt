package dev.yabai.ocr

import android.view.View
import android.widget.Button
import dev.yabai.ocr.service.FloatingOcrService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The label of the start button has to fit inside the button (R33a).
 *
 * With the service running the button says «OCR flutuante ativo — toque para reabrir os ajustes»,
 * which is long enough to wrap. The layout gave it a fixed 56 dp height, so on the phone the second
 * line was cut in half — the sentence ended at «…reabrir os» and «ajustes» was clipped.
 *
 * The check is the text's own layout against the button's height: a fixed height fails it as soon as
 * the label needs one more line than that height can hold, whatever the font scale of the phone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w320dp-h640dp-mdpi")
class StartButtonTest {

    @Test
    fun `the running label is not clipped`() {
        FloatingOcrService.isRunning = true
        try {
            val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
            val content = activity.findViewById<View>(android.R.id.content)

            content.measure(
                View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY),
            )
            content.layout(0, 0, content.measuredWidth, content.measuredHeight)

            val button = activity.findViewById<Button>(R.id.btnStart)
            assertEquals(
                activity.getString(R.string.start_button_running),
                button.text.toString(),
            )

            val text = button.layout
            assertNotNull("the button never laid its text out", text)
            val lastLine = text.getLineBottom(text.lineCount - 1)
            val room = button.height - button.paddingTop - button.paddingBottom

            assertTrue(
                "the «${button.text}» label needs $lastLine px but the button only has $room: " +
                    "the last line is cut",
                lastLine <= room,
            )
        } finally {
            FloatingOcrService.isRunning = false
        }
    }
}
