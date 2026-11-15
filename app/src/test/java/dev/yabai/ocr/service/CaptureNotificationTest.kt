package dev.yabai.ocr.service

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
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
import org.robolectric.annotation.GraphicsMode

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

    /**
     * Regression: the status bar showed a solid white square where the app's icon should be. A
     * notification icon is drawn from its alpha channel alone, and `ic_scan` — the bubble's picture,
     * right for the bubble, where the whole bitmap is drawn — is an opaque rectangle. The system
     * painted every pixel of it white.
     *
     * The check is on the pixels the system would actually paint, so it catches any future icon that
     * is opaque, not just this one coming back.
     */
    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Suppress("DEPRECATION")
    fun `the status bar icon has transparency to be drawn from`() {
        val notification = service.buildNotification()
        val id = notification.icon
        assertTrue("the notification carries no status bar icon", id != 0)
        assertTrue(
            "the status bar still uses the opaque picture drawable",
            id != R.drawable.ic_scan,
        )

        val icon = context.getDrawable(id)!!
        val size = 96
        icon.setBounds(0, 0, size, size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        icon.draw(Canvas(bitmap))

        var clear = 0
        for (y in 0 until size) {
            for (x in 0 until size) {
                if (Color.alpha(bitmap.getPixel(x, y)) == 0) clear++
            }
        }
        assertTrue(
            "only ${clear}px of ${size * size} are transparent: the system draws the icon as a solid square",
            clear > size * size / 4,
        )
    }

    private fun action(notification: Notification, titleRes: Int): Notification.Action? {
        val title = context.getString(titleRes)
        return notification.actions.orEmpty().firstOrNull { it.title.toString() == title }
    }
}
