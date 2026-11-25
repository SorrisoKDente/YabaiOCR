package dev.yabai.ocr.overlay

import android.Manifest
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import dev.yabai.ocr.CameraActivity
import dev.yabai.ocr.R
import dev.yabai.ocr.service.FloatingOcrService
import dev.yabai.ocr.util.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * R50: the wait for a reading has to be readable, red and out of the sentence's way.
 *
 * The camera's card was measured to its own content, and «Reconhecendo o texto…» came out cut — in
 * English, «Recogni». It now spans the window, with the label taking whatever the spinner leaves.
 * The way out of the wait is red like every other way out of the app, and it sits at the right end
 * instead of under the text.
 *
 * Robolectric measures text with fake font metrics, so a width compared against `paint.measureText`
 * proves nothing here: what is held down is the structure that makes the cut impossible — a card as
 * wide as the window and a label with more than half of it. Measured to its content, the old card
 * was 78 px wide with a 34 px label on a 320 dp screen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w320dp-h640dp-mdpi")
class ReadingCardTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun `the camera's reading card takes the window width so the label is not cut`() {
        val root = cameraRoot()
        val card = root.findViewWithTag<View>(CameraActivity.LOADING_TAG)
        val label = texts(card).first { it.text.toString() == context.getString(R.string.camera_reading) }

        assertEquals(
            "the card hugging its own label is what cut «Reconhecendo o texto…»",
            root.width - context.dp(2 * 16),
            card.width,
        )
        assertTrue(
            "the label got ${label.width}px of a ${card.width}px card: it is hugging its text again",
            label.width > card.width / 2,
        )
    }

    @Test
    fun `the camera's cancel is red and sits at the right end`() {
        val card = cameraRoot().findViewWithTag<View>(CameraActivity.LOADING_TAG)
        val cancel = texts(card).first { it.text.toString() == context.getString(R.string.recognition_cancel) }

        assertEquals(
            "«Cancelar» is not red",
            context.getColor(R.color.warn),
            (cancel.background as? GradientDrawable)?.color?.defaultColor,
        )
        assertEquals(
            "«Cancelar» does not end at the card's right edge: ${cancel.right} vs ${card.width}",
            card.width - card.paddingRight,
            cancel.right,
        )
    }

    @Test
    fun `the panel's cancel is red and sits at the right end`() {
        val panel = ResultOverlay(context).apply { showLoading { } }
        val metrics = context.resources.displayMetrics
        panel.measure(
            View.MeasureSpec.makeMeasureSpec(metrics.widthPixels, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(metrics.heightPixels, View.MeasureSpec.EXACTLY),
        )
        panel.layout(0, 0, metrics.widthPixels, metrics.heightPixels)

        val cancel = texts(panel).first { it.text.toString() == context.getString(R.string.recognition_cancel) }
        val row = cancel.parent as View
        val label = texts(panel).first { it.text.toString() == context.getString(R.string.recognizing) }

        assertTrue(
            "the panel's label got ${label.width}px of a ${panel.width}px panel",
            label.width > panel.width / 2,
        )
        assertEquals(
            "the panel's «Cancelar» is not red",
            context.getColor(R.color.warn),
            (cancel.background as? GradientDrawable)?.color?.defaultColor,
        )
        assertEquals("«Cancelar» is not at the right end of its row", row.width, cancel.right)
    }

    /** The camera screen with the reading card on screen, laid out at the display's size. */
    private fun cameraRoot(): ViewGroup {
        FloatingOcrService.isRunning = true
        shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(Manifest.permission.CAMERA)
        val activity = Robolectric.buildActivity(CameraActivity::class.java).create().get()
        try {
            val root = activity.findViewById<ViewGroup>(android.R.id.content)
            root.findViewWithTag<View>(CameraActivity.LOADING_TAG).visibility = View.VISIBLE
            val metrics = context.resources.displayMetrics
            root.measure(
                View.MeasureSpec.makeMeasureSpec(metrics.widthPixels, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(metrics.heightPixels, View.MeasureSpec.EXACTLY),
            )
            root.layout(0, 0, metrics.widthPixels, metrics.heightPixels)
            return root
        } finally {
            FloatingOcrService.isRunning = false
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
