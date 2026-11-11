package dev.yabai.ocr.desktop

import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.exceptions.DBusException
import org.freedesktop.dbus.interfaces.DBus
import org.freedesktop.dbus.interfaces.Properties
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNoException
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Integration check against the real session bus. It uses unique bus names, so it can run next to
 * a Plasmashell that already owns `org.kde.StatusNotifierWatcher`; on a machine without a session
 * bus (Windows CI) it is skipped.
 */
class StatusNotifierDbusSmokeTest {

    @Test
    fun `the status notifier registers with the watcher and exports protocol properties`() {
        assumeTrue(System.getenv("DBUS_SESSION_BUS_ADDRESS") != null)

        val token = UUID.randomUUID().toString().replace("-", "")
        val service = "dev.yabai.test.StatusNotifierItem-$token"
        val watcherService = "dev.yabai.test.StatusNotifierWatcher-$token"
        val watcherConnection = DBusConnectionBuilder.forSessionBus().build()
        val watcher = RecordingWatcher()
        var tray: StatusNotifierTray? = null

        try {
            try {
                watcherConnection.requestBusName(watcherService)
            } catch (error: DBusException) {
                // The development sandbox exposes a filtered session bus that refuses arbitrary
                // bus names; the real Plasma session and CI run this integration test for real.
                assumeNoException("session bus does not allow a private watcher name", error)
            }
            watcherConnection.exportObject(SNI_WATCHER_PATH, watcher)

            val menu = AtomicReference<Pair<Int, Int>?>(null)
            val menuCalled = CountDownLatch(1)
            tray = StatusNotifierTray(
                onActivate = {},
                onSecondaryActivate = {},
                onContextMenu = { x, y ->
                    menu.set(x to y)
                    menuCalled.countDown()
                },
                trayBusName = service,
                watcherService = watcherService,
            )

            assertTrue("start failed: ${tray.lastError}", tray.start())
            assertTrue(watcher.registered.await(5, TimeUnit.SECONDS))
            assertEquals(service, watcher.service)

            val client = DBusConnectionBuilder.forSessionBus().build()
            try {
                val properties = client.getRemoteObject(service, SNI_OBJECT_PATH, Properties::class.java)
                val all = properties.GetAll("org.kde.StatusNotifierItem")

                assertEquals("ApplicationStatus", all["Category"]?.value)
                assertEquals("yabai-ocr", all["Id"]?.value)
                assertEquals("Yabai OCR", all["Title"]?.value)
                assertEquals("Active", all["Status"]?.value)
                assertEquals(false, all["ItemIsMenu"]?.value)
                // KDE checks the concrete signatures; `av` would make it ignore the tray icon.
                assertEquals("a(iiay)", all["IconPixmap"]?.sig)
                assertEquals("(sa(iiay)ss)", all["ToolTip"]?.sig)

                val item = client.getRemoteObject(service, SNI_OBJECT_PATH, StatusNotifierItemDbus::class.java)
                item.ContextMenu(17, 29)
                assertTrue(menuCalled.await(5, TimeUnit.SECONDS))
                assertEquals(17 to 29, menu.get())
            } finally {
                client.close()
            }
        } finally {
            tray?.stop()
            watcherConnection.close()
        }
    }

    @Test
    fun `a missing watcher does not leave the bus name owned`() {
        assumeTrue(System.getenv("DBUS_SESSION_BUS_ADDRESS") != null)

        val token = UUID.randomUUID().toString().replace("-", "")
        val service = "dev.yabai.test.StatusNotifierItem-$token"
        val tray = StatusNotifierTray(
            onActivate = {},
            onSecondaryActivate = {},
            onContextMenu = { _, _ -> },
            trayBusName = service,
            watcherService = "dev.yabai.test.MissingWatcher-$token",
        )

        assertFalse(tray.start())

        val probe = DBusConnectionBuilder.forSessionBus().build()
        try {
            val dbus = probe.getRemoteObject("org.freedesktop.DBus", "/org/freedesktop/DBus", DBus::class.java)
            var owned = true
            var attempts = 0
            while (owned && attempts < 20) {
                owned = dbus.NameHasOwner(service)
                if (owned) {
                    Thread.sleep(25)
                    attempts++
                }
            }
            assertFalse("failed start leaked the SNI bus name", owned)
        } finally {
            probe.close()
        }
    }

    private class RecordingWatcher : StatusNotifierWatcherDbus {
        val registered = CountDownLatch(1)

        @Volatile
        var service: String? = null

        override fun getObjectPath(): String = SNI_WATCHER_PATH

        override fun RegisterStatusNotifierItem(service: String) {
            this.service = service
            registered.countDown()
        }
    }
}
