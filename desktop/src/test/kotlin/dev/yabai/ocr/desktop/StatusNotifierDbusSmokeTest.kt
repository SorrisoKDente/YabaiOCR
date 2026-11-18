package dev.yabai.ocr.desktop

import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.exceptions.DBusException
import org.freedesktop.dbus.interfaces.DBus
import org.freedesktop.dbus.interfaces.Properties
import org.freedesktop.dbus.types.UInt32
import org.freedesktop.dbus.types.Variant
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
            val menuAction = CountDownLatch(1)
            tray = StatusNotifierTray(
                onActivate = {},
                onSecondaryActivate = {},
                onContextMenu = { x, y ->
                    menu.set(x to y)
                    menuCalled.countDown()
                },
                menuItems = {
                    listOf(StatusMenuItem(1, "Test action", action = { menuAction.countDown() }))
                },
                trayBusName = service,
                watcherService = watcherService,
            )

            // Read lastError only after the call: the message argument used to be evaluated first,
            // which reported "start failed: null" and hid the real cause.
            val started = tray.start()
            assertTrue("start failed: ${tray.lastError}", started)
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
                assertEquals(SNI_MENU_PATH, (all["Menu"]?.value as DBusPath).path)
                // KDE checks the concrete signatures; `av` would make it ignore the tray icon.
                assertEquals("a(iiay)", all["IconPixmap"]?.sig)
                assertEquals("(sa(iiay)ss)", all["ToolTip"]?.sig)

                // GNOME's AppIndicator reads each property individually with Properties.Get and
                // tells GDBus to expect `(v)`. dbus-java's client unwraps the variant here, so the
                // wire type is asserted by `every property is readable through Properties Get as a
                // variant` (the reply must be a Variant) and by `dbus-send` in the E2E notes.
                assertEquals(TRAY_HOVER_OFF_ICON_NAME, properties.Get<Any>(SNI_INTERFACE_NAME, "IconName"))
                assertEquals("Yabai OCR", properties.Get<Any>(SNI_INTERFACE_NAME, "Title"))

                val dbusMenu = client.getRemoteObject(service, SNI_MENU_PATH, DbusMenuDbus::class.java)
                val layout = dbusMenu.GetLayout(0, 1, emptyList())
                assertEquals(1, layout.children.size)
                dbusMenu.Event(1, "clicked", Variant(""), UInt32(0))
                assertTrue("DBusMenu action was not delivered", menuAction.await(5, TimeUnit.SECONDS))

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
            // dbus-java releases the name when the connection closes, which on a busy real session
            // bus can take longer than the few hundred milliseconds this used to allow.
            while (owned && attempts < 100) {
                owned = dbus.NameHasOwner(service)
                if (owned) {
                    Thread.sleep(50)
                    attempts++
                }
            }
            assertFalse("failed start leaked the SNI bus name", owned)
        } finally {
            probe.close()
        }
    }

    @Test
    fun `a dbus menu marshals its layout and dispatches the clicked event on the real bus`() {
        assumeTrue(System.getenv("DBUS_SESSION_BUS_ADDRESS") != null)

        val server = DBusConnectionBuilder.forSessionBus().build()
        val client = DBusConnectionBuilder.forSessionBus().build()
        val clicked = CountDownLatch(1)
        try {
            val path = "/dev/yabai/test/DbusMenu"
            server.exportObject(
                path,
                DbusMenu(path) {
                    listOf(StatusMenuItem(1, "Test action", action = { clicked.countDown() }))
                },
            )

            val menu = client.getRemoteObject(server.uniqueName, path, DbusMenuDbus::class.java)
            val layout = menu.GetLayout(0, 1, emptyList())

            assertEquals(UInt32(0), layout.revision)
            assertEquals(0, layout.id)
            assertEquals(1, layout.children.size)
            assertEquals("(ia{sv}av)", layout.children.single().sig)

            val aboutToShow = menu.AboutToShowGroup(listOf(0, 99))
            assertEquals(listOf(0), aboutToShow.updatesNeeded)
            assertTrue(aboutToShow.idErrors.isEmpty())

            menu.Event(1, "clicked", Variant(""), UInt32(0))
            assertTrue(clicked.await(5, TimeUnit.SECONDS))
        } finally {
            client.close()
            server.close()
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
