package dev.yabai.ocr.overlay

import android.Manifest
import android.content.Context
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import dev.yabai.ocr.CameraActivity
import dev.yabai.ocr.R
import dev.yabai.ocr.service.FloatingOcrService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings
import java.io.File

/**
 * «Cancelar» while the text is being recognised (R48m).
 *
 * The panel's spinner and the camera's frozen photo are the two places a reading is waited for, and
 * neither had a way out: a long page, a stalled engine or a shot taken by mistake left the user
 * watching a spinner, with the only exit being to dismiss the panel — which threw the capture away
 * anyway. The button is one question with three answers: the panel asks its callbacks, the service
 * drops the work in flight, and the camera goes back to its preview.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w320dp-h640dp-mdpi")
class RecognitionCancelTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun `the spinner offers a way out of a reading`() {
        var cancelled = 0
        val panel = ResultOverlay(context).apply { showLoading { cancelled++ } }

        val button = buttons(panel).firstOrNull { it.text.toString() == context.getString(R.string.recognition_cancel) }
        assertNotNull("the spinner has no «Cancelar»: ${buttons(panel).map { it.text }}", button)
        button!!.performClick()

        assertEquals("«Cancelar» did not stop the reading", 1, cancelled)
    }

    /** The panel never stops the reading itself: it asks, and the service is the one holding it. */
    @Test
    fun `the panel's cancel asks the service to stop the reading`() {
        ShadowSettings.setCanDrawOverlays(true)
        val asked = ArrayList<String>()
        val controller = OverlayController(context).apply { callbacks = RecordingCallbacks(asked) }
        controller.showBubble()
        controller.showResultLoading()

        val panel = controller.resultPanel()
        assertNotNull("the panel did not open", panel)
        buttons(panel!!).firstOrNull { it.text.toString() == context.getString(R.string.recognition_cancel) }
            ?.performClick()

        assertEquals(listOf("cancel reading"), asked)
    }

    /**
     * The service half: both doors — the panel's callback and the camera's intent — reach the `Job`
     * the reading runs in, and give the capture back for the next shot.
     */
    @Test
    fun `the service drops the reading it is told to cancel`() {
        val controller = Robolectric.buildService(FloatingOcrService::class.java)
        try {
            val service = controller.create().get()

            fun cancel(ask: (FloatingOcrService) -> Unit) {
                val job = CoroutineScope(SupervisorJob() + Dispatchers.Default).launch { delay(60_000) }
                field(service, "readJob").set(service, job)
                field(service, "busy").set(service, true)

                ask(service)

                assertTrue("the reading kept running after «Cancelar»", job.isCancelled)
                assertFalse(
                    "the capture stayed blocked after «Cancelar»",
                    field(service, "busy").getBoolean(service),
                )
            }

            cancel { it.onRecognitionCancelRequested() }
            cancel {
                it.onStartCommand(
                    Intent(it, FloatingOcrService::class.java).setAction(FloatingOcrService.ACTION_CANCEL_READ),
                    0,
                    1,
                )
            }
        } finally {
            runCatching { controller.destroy() }
            FloatingOcrService.isRunning = false
        }
    }

    /**
     * The panel's half: the same cancel that drops the work also takes the spinner down. Stopping a
     * reading without closing the panel would leave the user watching the very spinner they asked
     * to leave, with nothing behind it.
     */
    @Test
    fun `the service closes the spinner when it cancels the reading`() {
        ShadowSettings.setCanDrawOverlays(true)
        val controller = Robolectric.buildService(FloatingOcrService::class.java)
        try {
            val service = controller.create().get()
            val overlays = field(service, "overlays").get(service) as OverlayController
            overlays.showResultLoading()
            assertTrue("the spinner did not open", overlays.isResultVisible())

            val job = CoroutineScope(SupervisorJob() + Dispatchers.Default).launch { delay(60_000) }
            field(service, "readJob").set(service, job)
            field(service, "busy").set(service, true)

            service.onRecognitionCancelRequested()

            assertFalse("the spinner stayed on screen after «Cancelar»", overlays.isResultVisible())
        } finally {
            runCatching { controller.destroy() }
            FloatingOcrService.isRunning = false
        }
    }

    /**
     * The camera's half: the wait is abandoned, the service is told to stop, and the preview (and the
     * shutter) are back — a cancel that only hid the card would leave the picture frozen forever.
     */
    @Test
    fun `the camera cancels the reading and goes back to the preview`() {
        FloatingOcrService.isRunning = true
        shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(Manifest.permission.CAMERA)
        val controller = Robolectric.buildActivity(CameraActivity::class.java)
        try {
            val camera = controller.create().get()
            val root = camera.findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as ViewGroup
            val card = text(camera, camera.getString(R.string.camera_reading)).parent as View
            val shutter = (0 until root.childCount)
                .map { root.getChildAt(it) }
                .first { it.contentDescription == camera.getString(R.string.camera_shutter) }
            // The state the shutter puts the screen in: the photo is frozen and the service has it.
            card.visibility = View.VISIBLE
            field(camera, "waitingForReading").set(camera, true)
            field(camera, "handedOff").set(camera, true)

            buttons(root).first { it.text.toString() == camera.getString(R.string.recognition_cancel) }
                .performClick()

            assertEquals(
                "the camera never told the service to stop reading",
                FloatingOcrService.ACTION_CANCEL_READ,
                shadowOf(camera).nextStartedService?.action,
            )
            assertEquals("the wait stayed on the screen", View.GONE, card.visibility)
            assertTrue("the shutter was not armed again", shutter.isEnabled)
            assertEquals("the picture is not back to the preview", View.VISIBLE, root.getChildAt(0).visibility)
        } finally {
            runCatching { controller.destroy() }
            FloatingOcrService.isRunning = false
        }
    }

    /**
     * What the service test above cannot see, because the work is private: every pipeline registers
     * its reading for the cancel, and a cancelled photo read does not announce itself — that notice
     * would close the camera screen the user just sent back to its preview.
     */
    @Test
    fun `every reading is registered for the cancel`() {
        val service = source("app/src/main/java/dev/yabai/ocr/service/FloatingOcrService.kt")
        val pipelines = listOf(
            "private fun requestCapture(" to "private fun recognisePhoto(",
            "private fun recognisePhoto(" to "private fun photoShown(",
            "private fun retryPhoto(" to "private fun beginPhoto(",
        )
        for ((start, end) in pipelines) {
            val body = service.substringAfter(start).substringBefore(end)
            assertTrue("$start does not launch its reading through startReading", body.contains("startReading {"))
            assertFalse("$start launches a reading that «Cancelar» cannot reach", body.contains("scope.launch {"))
        }
        assertTrue(
            "a cancelled reading still tells the camera screen that its reading is on screen",
            service.contains("if (isActive) photoShown()"),
        )
    }

    // --- helpers --------------------------------------------------------------------------------

    /** Every button in this view tree, by the tag [actionButton] puts on them. */
    private fun buttons(view: View): List<TextView> {
        val found = ArrayList<TextView>()
        if (view is TextView && view.tag == ACTION_BUTTON_TAG) found.add(view)
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) found.addAll(buttons(view.getChildAt(index)))
        }
        return found
    }

    private fun text(activity: CameraActivity, label: String): TextView =
        texts(activity.findViewById(android.R.id.content)).first { it.text.toString() == label }

    private fun texts(view: View): List<TextView> {
        val found = ArrayList<TextView>()
        if (view is TextView) found.add(view)
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) found.addAll(texts(view.getChildAt(index)))
        }
        return found
    }

    private fun field(target: Any, name: String): java.lang.reflect.Field =
        target.javaClass.getDeclaredField(name).apply { isAccessible = true }

    private fun source(path: String): String {
        val found = sequenceOf(File(path), File("../$path"))
            .firstOrNull { it.isFile }?.readText().orEmpty()
        assertTrue("$path not found", found.isNotEmpty())
        return found
    }
}
