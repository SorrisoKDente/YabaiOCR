package dev.yabai.ocr

import android.Manifest
import android.os.Looper
import android.content.Intent
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import dev.yabai.ocr.service.FloatingOcrService
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowSettings

/**
 * Taking back a permission that was already granted (R33d).
 *
 * The two permission buttons only knew how to grant: once the permission was on, «Conceder» stayed
 * there and there was no way in the app to undo it. The button now says «Remover» and asks first,
 * because its neighbour is a one-tap trip to a system screen where the permission is switched off.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w320dp-h640dp-mdpi")
class PermissionRemovalTest {

    @After
    fun clearPauseFlag() {
        // The service is not running in these tests; make sure a previous test's state cannot leak.
        FloatingOcrService.isRunning = false
        FloatingOcrService.isPaused = false
    }

    @Test
    fun `a granted overlay permission turns the button into «Remover»`() {
        ShadowSettings.setCanDrawOverlays(true)
        val activity = phone()

        assertEquals(
            activity.getString(R.string.permission_remove),
            activity.findViewById<Button>(R.id.btnOverlayPermission).text.toString(),
        )
    }

    @Test
    fun `removing the overlay permission asks before opening the system screen`() {
        ShadowSettings.setCanDrawOverlays(true)
        val activity = phone()

        activity.findViewById<Button>(R.id.btnOverlayPermission).performClick()

        val dialog = dialog()
        assertEquals(
            activity.getString(R.string.permission_remove_overlay_message),
            message(dialog),
        )
        assertNull(
            "the system screen opened before the user confirmed",
            shadowOf(activity).nextStartedActivityForResult,
        )

        click(dialog.getButton(AlertDialog.BUTTON_POSITIVE))

        val started = shadowOf(activity).nextStartedActivityForResult?.intent
        assertNotNull("confirming did not open the system screen", started)
        assertEquals(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, started!!.action)
        assertEquals("package:${activity.packageName}", started.dataString)
    }

    @Test
    fun `a granted notification permission turns the button into «Remover»`() {
        shadowOf(RuntimeEnvironment.getApplication())
            .grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val activity = phone()

        assertEquals(
            activity.getString(R.string.permission_remove),
            activity.findViewById<Button>(R.id.btnNotificationPermission).text.toString(),
        )
    }

    @Test
    fun `removing the notification permission asks before opening the system screen`() {
        shadowOf(RuntimeEnvironment.getApplication())
            .grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val activity = phone()

        activity.findViewById<Button>(R.id.btnNotificationPermission).performClick()

        val dialog = dialog()
        assertEquals(
            activity.getString(R.string.permission_remove_notifications_message),
            message(dialog),
        )

        click(dialog.getButton(AlertDialog.BUTTON_POSITIVE))

        val started = shadowOf(activity).nextStartedActivityForResult?.intent
        assertNotNull("confirming did not open the notification settings", started)
        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, started!!.action)
        assertEquals(
            activity.packageName,
            started.getStringExtra(Settings.EXTRA_APP_PACKAGE),
        )
    }

    @Test
    fun `cancelling the question leaves the permission alone`() {
        ShadowSettings.setCanDrawOverlays(true)
        val activity = phone()

        activity.findViewById<Button>(R.id.btnOverlayPermission).performClick()
        click(dialog().getButton(AlertDialog.BUTTON_NEGATIVE))

        assertNull(
            "cancelling still opened the system screen",
            shadowOf(activity).nextStartedActivityForResult,
        )
    }

    @Test
    fun `without the permission the button still grants`() {
        val activity = phone()

        assertEquals(
            activity.getString(R.string.permission_grant),
            activity.findViewById<Button>(R.id.btnOverlayPermission).text.toString(),
        )
        activity.findViewById<Button>(R.id.btnOverlayPermission).performClick()

        val started = shadowOf(activity).nextStartedActivityForResult?.intent
        assertNotNull("the grant button stopped asking for the permission", started)
        assertEquals(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, started!!.action)
    }

    private fun phone(): MainActivity =
        Robolectric.buildActivity(MainActivity::class.java).setup().get()

    /**
     * Clicks a dialog button and lets the click run.
     *
     * AlertDialog's buttons post their listener to the main looper, so in a paused-looper test the
     * listener has not run when [performClick] returns.
     */
    private fun click(button: android.widget.Button) {
        button.performClick()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun dialog(): AlertDialog {
        val latest = ShadowDialog.getLatestDialog() as? AlertDialog
        assertNotNull("the app did not ask before removing the permission", latest)
        return latest!!
    }

    /**
     * The dialog's text. `shadowOf(dialog).title` is empty on appcompat dialogs, and the title is
     * not the part that explains the consequence anyway — the message is.
     */
    private fun message(dialog: AlertDialog): String =
        dialog.findViewById<TextView>(android.R.id.message)?.text?.toString().orEmpty()
}
