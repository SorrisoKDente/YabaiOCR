package dev.yabai.ocr.service

import android.content.Context
import android.content.Intent
import dev.yabai.ocr.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Closing the «OCR ativo» notification has to end the capture (R24).
 *
 * From Android 13 the notification of a foreground service can be swiped away, and the capture
 * session it advertises does not survive as something the next tap can use: the user is left with a
 * bubble that answers «falha ao capturar» forever. Two things hold the contract down here — the
 * notification itself turns its dismissal into the same stop as its «Parar» action, and the stop
 * action really stops the service.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CaptureNotificationTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    private val service: FloatingOcrService by lazy {
        Robolectric.buildService(FloatingOcrService::class.java).create().get()
    }

    @Test
    fun `dismissing the notification asks the service to stop`() {
        val notification = service.buildNotification()

        val dismissed = notification.deleteIntent
        assertNotNull("the notification cannot be dismissed into a stop", dismissed)
        assertEquals(
            "dismissing the notification did not ask for a stop",
            FloatingOcrService.ACTION_STOP,
            shadowOf(dismissed).savedIntent.action,
        )
    }

    @Test
    fun `the notification offers its own «Parar»`() {
        val notification = service.buildNotification()
        val title = context.getString(R.string.notification_stop)

        val stop = notification.actions.orEmpty().firstOrNull { it.title.toString() == title }
        assertNotNull("the notification has no «$title» action", stop)
        assertEquals(
            FloatingOcrService.ACTION_STOP,
            shadowOf(stop!!.actionIntent).savedIntent.action,
        )
    }

    @Test
    fun `the stop action ends the service`() {
        service.onStartCommand(Intent(FloatingOcrService.ACTION_STOP), 0, 1)

        assertTrue("the service kept running after being asked to stop", shadowOf(service).isStoppedBySelf)
    }
}
