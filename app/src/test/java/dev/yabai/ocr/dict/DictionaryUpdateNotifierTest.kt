package dev.yabai.ocr.dict

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/**
 * A periodic check with no notification is a background request the user never learns about (R43).
 *
 * It also must not become a daily nuisance: the same set of versions is announced once, and the
 * notification goes away when the updates do. And without the notification permission the check
 * still runs and records its result — there is simply no tray to put it in, so the key is not burned
 * and the first check after the permission is granted can still announce it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DictionaryUpdateNotifierTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val manager: NotificationManager
        get() = context.getSystemService(NotificationManager::class.java)

    private fun notifications(): List<Notification> = shadowOf(manager).allNotifications

    private fun titleOf(notification: Notification): String =
        notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()

    private fun textOf(notification: Notification): String =
        notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()

    @Before
    fun setUp() {
        shadowOf(manager).setNotificationsEnabled(true)
    }

    @Test
    fun `an update posts one notification that names the dictionaries`() {
        DictionaryUpdateNotifier(context).show(
            listOf(
                UpdateResult("jmdict-en", UpdateState.AVAILABLE, remoteLabel = "30/10/2026"),
                UpdateResult("ggjz-pt", UpdateState.AVAILABLE, remoteLabel = "2026.10.30"),
                UpdateResult("kanjidic-en", UpdateState.CURRENT),
            ),
        )

        val notification = notifications().single()
        assertTrue(
            "the title should say how many updates there are: ${titleOf(notification)}",
            titleOf(notification).contains("2"),
        )
        assertTrue(
            "the body should name the dictionaries, not their ids: ${textOf(notification)}",
            textOf(notification).contains("JMdict (inglês)") &&
                textOf(notification).contains("Jitendex em português (ggjz.org)"),
        )
        assertTrue(
            "a notification for dictionaries belongs to the dictionary channel",
            shadowOf(manager).notificationChannels.any { it.id == "dictionary_updates" },
        )
    }

    @Test
    fun `the same versions are announced only once`() {
        val notifier = DictionaryUpdateNotifier(context)
        val results = listOf(UpdateResult("jmdict-en", UpdateState.AVAILABLE, remoteLabel = "30/10/2026"))
        notifier.show(results)
        assertNotNull(notifications().singleOrNull())

        // The user swiped it away. The next periodic check finds the same version, which is not
        // news: re-posting it every day would be the phone nagging about nothing.
        manager.cancelAll()
        notifier.show(results)

        assertTrue("the same versions must not come back every check", notifications().isEmpty())
        assertEquals(
            "jmdict-en:30/10/2026",
            UpdateStateStore(context).notifiedKey(),
        )
    }

    @Test
    fun `when everything is current the notification is removed and the key forgotten`() {
        val notifier = DictionaryUpdateNotifier(context)
        notifier.show(listOf(UpdateResult("jmdict-en", UpdateState.AVAILABLE, remoteLabel = "30/10/2026")))
        assertEquals(1, notifications().size)

        notifier.show(
            listOf(
                UpdateResult("jmdict-en", UpdateState.CURRENT),
                UpdateResult("ggjz-pt", UpdateState.FAILED, message = "sem rede"),
            ),
        )

        assertTrue("a resolved update should not stay in the tray", notifications().isEmpty())
        assertEquals("", UpdateStateStore(context).notifiedKey())

        // The next real update has to be announced again: forgetting the key is what allows it.
        notifier.show(listOf(UpdateResult("jmdict-en", UpdateState.AVAILABLE, remoteLabel = "30/10/2026")))
        assertEquals(1, notifications().size)
    }

    @Test
    fun `without the permission nothing is posted and the key is kept for later`() {
        shadowOf(manager).setNotificationsEnabled(false)
        val notifier = DictionaryUpdateNotifier(context)
        val results = listOf(UpdateResult("jmdict-en", UpdateState.AVAILABLE, remoteLabel = "30/10/2026"))

        notifier.show(results)

        assertTrue(notifications().isEmpty())
        assertEquals(
            "burning the key here would hide the update after the permission is granted",
            "",
            UpdateStateStore(context).notifiedKey(),
        )

        shadowOf(manager).setNotificationsEnabled(true)
        notifier.show(results)
        assertEquals(1, notifications().size)
    }
}
