package dev.yabai.ocr.service

import android.app.Notification
import android.content.Context
import android.content.Intent
import dev.yabai.ocr.R
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

/**
 * Closing the «OCR ativo» notification has to end the capture (R24), and its other two actions have
 * to do what they say: pause the reader without ending it, and bring it back (R33c).
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

    @Test
    fun `the active notification offers «Pausar»`() {
        val pause = action(service.buildNotification(), R.string.notification_pause)

        assertNotNull("the notification has no way to pause the reader", pause)
        assertEquals(
            FloatingOcrService.ACTION_PAUSE,
            shadowOf(pause!!.actionIntent).savedIntent.action,
        )
    }

    @Test
    fun `pausing keeps the service and turns the notification into «Retomar»`() {
        service.onStartCommand(Intent(FloatingOcrService.ACTION_PAUSE), 0, 1)

        assertTrue("the reader did not pause", FloatingOcrService.isPaused)
        assertFalse("pausing ended the service", shadowOf(service).isStoppedBySelf)

        val notification = service.buildNotification()
        assertEquals(
            context.getString(R.string.notification_title_paused),
            shadowOf(notification).contentTitle.toString(),
        )
        val resume = action(notification, R.string.notification_resume)
        assertNotNull("the paused notification has no way back", resume)
        assertEquals(
            FloatingOcrService.ACTION_RESUME,
            shadowOf(resume!!.actionIntent).savedIntent.action,
        )
    }

    @Test
    fun `resuming offers «Pausar» again`() {
        service.onStartCommand(Intent(FloatingOcrService.ACTION_PAUSE), 0, 1)
        service.onStartCommand(Intent(FloatingOcrService.ACTION_RESUME), 0, 1)

        assertFalse("the reader stayed paused", FloatingOcrService.isPaused)
        assertNotNull(
            "the notification did not go back to «Pausar»",
            action(service.buildNotification(), R.string.notification_pause),
        )
    }

    @Test
    fun `destroying the service clears the paused state`() {
        service.onStartCommand(Intent(FloatingOcrService.ACTION_PAUSE), 0, 1)
        assertTrue(FloatingOcrService.isPaused)

        service.onStartCommand(Intent(FloatingOcrService.ACTION_STOP), 0, 1)
        service.onDestroy()

        assertFalse("a destroyed reader is still marked paused", FloatingOcrService.isPaused)
    }

    private fun action(notification: Notification, titleRes: Int): Notification.Action? {
        val title = context.getString(titleRes)
        return notification.actions.orEmpty().firstOrNull { it.title.toString() == title }
    }
}
