package dev.yabai.ocr.desktop

import java.awt.Color
import java.awt.image.BufferedImage
import org.freedesktop.dbus.annotations.DBusProperties
import org.freedesktop.dbus.annotations.DBusProperty
import org.freedesktop.dbus.types.Variant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusNotifierTrayTest {

    @Test
    fun `linux wayland publishes a status notifier item`() {
        assertEquals(TrayBackend.SNI, trayBackendFor(OsFamily.LINUX, SessionType.WAYLAND))
    }

    @Test
    fun `linux x11 also tries the status notifier item first`() {
        // GNOME (Ubuntu 22.04) draws no XEmbed icon at all: an AWT-first choice left the app with no
        // visible tray while it believed the icon had been added. SNI is what the AppIndicator
        // extension reads; AWT remains the fallback when no watcher answers.
        assertEquals(TrayBackend.SNI, trayBackendFor(OsFamily.LINUX, SessionType.X11))
        assertEquals(TrayBackend.SNI, trayBackendFor(OsFamily.LINUX, SessionType.UNKNOWN))
    }

    @Test
    fun `windows and macos keep the awt tray`() {
        assertEquals(TrayBackend.AWT, trayBackendFor(OsFamily.WINDOWS, SessionType.UNKNOWN))
        assertEquals(TrayBackend.AWT, trayBackendFor(OsFamily.MACOS, SessionType.UNKNOWN))
    }

    @Test
    fun `the bus name follows the KDE convention`() {
        assertEquals("org.kde.StatusNotifierItem-4321-1", statusNotifierBusName(4321))
    }

    @Test
    fun `activate and context menu reach the app callbacks`() {
        var activated = 0
        var secondary = 0
        var menu: Pair<Int, Int>? = null
        val item = StatusNotifierItem(
            onActivate = { activated++ },
            onSecondaryActivate = { secondary++ },
            onContextMenu = { x, y -> menu = x to y },
            currentStatus = { "" },
        )

        item.Activate(10, 20)
        item.SecondaryActivate(11, 21)
        item.ContextMenu(300, 400)

        assertEquals(1, activated)
        assertEquals(1, secondary)
        assertEquals(300 to 400, menu)
    }

    @Test
    fun `the item advertises a dbus menu without forcing the host to call context menu`() {
        val item = StatusNotifierItem(
            onActivate = {},
            onSecondaryActivate = {},
            onContextMenu = { _, _ -> },
            currentStatus = { "" },
        )

        assertEquals(SNI_MENU_PATH, item.getMenu().path)
        assertFalse(item.getItemIsMenu())
    }

    @Test
    fun `the icon is argb32 in network byte order`() {
        val image = BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB)
        image.setRGB(0, 0, 0xFFFF0000.toInt()) // opaque red
        image.setRGB(1, 0, 0x8000FF00.toInt()) // half-transparent green
        image.setRGB(0, 1, 0xFF0000FF.toInt()) // opaque blue
        image.setRGB(1, 1, 0x00000000) // transparent

        val pixmap = statusNotifierPixmap(image, size = 2).single()

        assertEquals(2, pixmap.width)
        assertEquals(2, pixmap.height)
        assertEquals(16, pixmap.data.size)
        assertArrayEquals(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0, 0), pixmap.data.copyOfRange(0, 4))
        assertArrayEquals(byteArrayOf(0x80.toByte(), 0, 0xFF.toByte(), 0), pixmap.data.copyOfRange(4, 8))
        assertArrayEquals(byteArrayOf(0xFF.toByte(), 0, 0, 0xFF.toByte()), pixmap.data.copyOfRange(8, 12))
        assertArrayEquals(byteArrayOf(0, 0, 0, 0), pixmap.data.copyOfRange(12, 16))
    }

    @Test
    fun `the tooltip exposes the current status and a host menu is not forced`() {
        var status = "Pronto"
        val item = StatusNotifierItem(
            onActivate = {},
            onSecondaryActivate = {},
            onContextMenu = { _, _ -> },
            currentStatus = { status },
        )

        assertEquals("Yabai OCR", item.getToolTip().title)
        assertEquals("Pronto", item.getToolTip().subtitle)
        assertFalse(item.getItemIsMenu())

        status = "Modo Hover [Ativo]"
        assertEquals("Modo Hover [Ativo]", item.getToolTip().subtitle)
    }

    @Test
    fun `the item identifies itself as an application status icon`() {
        val item = StatusNotifierItem(
            onActivate = {},
            onSecondaryActivate = {},
            onContextMenu = { _, _ -> },
            currentStatus = { "" },
        )

        assertEquals("ApplicationStatus", item.getCategory())
        assertEquals("yabai-ocr", item.getId())
        assertEquals("Yabai OCR", item.getTitle())
        assertEquals("Active", item.getStatus())
        assertTrue(item.getIconPixmap().isNotEmpty())
        // GNOME's AppIndicator host resolves IconName through the icon theme; empty made it draw
        // its generic gear instead of the app icon. The two installed names carry the status dot.
        assertEquals(TRAY_HOVER_OFF_ICON_NAME, item.getIconName())
        item.setHoverActive(true)
        assertEquals(TRAY_HOVER_ON_ICON_NAME, item.getIconName())
    }

    @Test
    fun `the icon pixmap property declares the concrete struct array type`() {
        // A raw List would be advertised as `av`. KDE ignores an IconPixmap that is not `a(iiay)`
        // and the tray would show no icon. The introspection property type is what documents it.
        val properties = StatusNotifierItemDbus::class.java.getAnnotation(DBusProperties::class.java)
            .value
            .associateBy { it.name }
        val iconPixmap = properties.getValue("IconPixmap")

        assertTrue("IconPixmap must declare an explicit type", iconPixmap.type.java.isArray)
        assertEquals(StatusNotifierIconPixmap::class.java, iconPixmap.type.java.componentType)
        // The wire type of the value itself, which is what the hosts actually read.
        assertEquals("a(iiay)", (testItem().Get<Any>(SNI_INTERFACE_NAME, "IconPixmap") as Variant<*>).sig)
    }

    @Test
    fun `every property is readable through Properties Get as a variant`() {
        // GNOME's AppIndicator reads each SNI property with `Properties.Get` and asks GDBus for a
        // `(v)` reply. dbus-java's @DBusBoundProperty answered with the getter's own type (`s`), so
        // the host rejected the reply and drew its generic gear instead of the app icon.
        val item = testItem()

        val iconName = item.Get<Any>(SNI_INTERFACE_NAME, "IconName")
        assertTrue(
            "Properties.Get must answer with a variant, got ${iconName?.javaClass?.name}",
            iconName is Variant<*>,
        )
        assertEquals(TRAY_HOVER_OFF_ICON_NAME, (iconName as Variant<*>).value)
        assertEquals("s", iconName.sig)

        for (property in listOf("Category", "Id", "IconName", "Title", "Status", "IconPixmap", "ToolTip", "ItemIsMenu", "Menu", "WindowId")) {
            val value = item.Get<Any>(SNI_INTERFACE_NAME, property)
            assertTrue("$property must be a variant", value is Variant<*>)
        }
        assertEquals("o", (item.Get<Any>(SNI_INTERFACE_NAME, "Menu") as Variant<*>).sig)
        assertEquals("i", (item.Get<Any>(SNI_INTERFACE_NAME, "WindowId") as Variant<*>).sig)
        assertEquals("b", (item.Get<Any>(SNI_INTERFACE_NAME, "ItemIsMenu") as Variant<*>).sig)
    }

    @Test
    fun `an unknown property or interface is refused instead of answered`() {
        val item = testItem()

        try {
            item.Get<Any>(SNI_INTERFACE_NAME, "DoesNotExist")
            throw AssertionError("an unknown property must not be answered")
        } catch (expected: org.freedesktop.dbus.exceptions.DBusExecutionException) {
            assertTrue(expected.message.orEmpty().contains("DoesNotExist"))
        }
        try {
            item.GetAll("org.example.NotStatusNotifierItem")
            throw AssertionError("an unknown interface must not be answered")
        } catch (expected: org.freedesktop.dbus.exceptions.DBusExecutionException) {
            assertTrue(expected.message.orEmpty().contains("org.example.NotStatusNotifierItem"))
        }
    }

    @Test
    fun `no property is published as a dbus-java bound getter`() {
        // Root cause of the gear icon: with @DBusBoundProperty, dbus-java answers Properties.Get
        // with the getter's own type (`s`) instead of the required `v`, and GNOME's AppIndicator
        // discards a reply that is not `(v)`. The properties must be served by the explicit
        // Get/GetAll implementation only.
        val bound = StatusNotifierItemDbus::class.java.methods
            .filter { it.isAnnotationPresent(org.freedesktop.dbus.annotations.DBusBoundProperty::class.java) }

        assertTrue(
            "dbus-java would intercept Properties.Get for: ${bound.map { it.name }}",
            bound.isEmpty(),
        )
    }

    @Test
    fun `get all keeps the same variants the hosts read one by one`() {
        val item = testItem()

        val all = item.GetAll(SNI_INTERFACE_NAME)

        assertEquals(10, all.size)
        assertEquals(TRAY_HOVER_OFF_ICON_NAME, all.getValue("IconName").value)
        assertEquals("a(iiay)", all.getValue("IconPixmap").sig)
        assertEquals("(sa(iiay)ss)", all.getValue("ToolTip").sig)
        assertEquals(SNI_MENU_PATH, all.getValue("Menu").value)
    }

    @Test
    fun `the introspection annotation is the single source of the published properties`() {
        // The declaration used to be duplicated in the annotation and in a literal map, so a new
        // property could be introspected and never served (or served with the wrong type). The
        // annotation now drives both; this test fails if they ever drift again.
        val declared = StatusNotifierItemDbus::class.java.getAnnotation(DBusProperties::class.java).value
        val published = testItem().GetAll(SNI_INTERFACE_NAME)

        assertEquals(
            declared.map { it.name }.toSet(),
            published.keys,
        )
        for (property in declared) {
            val expectedSignature = org.freedesktop.dbus.Marshalling
                .getDBusType(arrayOf<java.lang.reflect.Type>(property.type.java))
            assertEquals(
                "the variant of ${property.name} must use the type the introspection declares",
                expectedSignature,
                published.getValue(property.name).sig,
            )
            assertEquals(
                "${property.name} must be readable on its own too",
                published.getValue(property.name).sig,
                (testItem().Get<Any>(SNI_INTERFACE_NAME, property.name) as Variant<*>).sig,
            )
        }
    }

    @Test
    fun `the status notifier icon follows the hover state`() {
        val item = StatusNotifierItem(
            onActivate = {},
            onSecondaryActivate = {},
            onContextMenu = { _, _ -> },
            currentStatus = { "" },
            initialHoverActive = false,
        )

        val inactive = item.getIconPixmap().single()
        assertFalse("the initial state is already off", item.setHoverActive(false))
        assertTrue("turning hover on changes the pixmap", item.setHoverActive(true))
        val active = item.getIconPixmap().single()
        assertFalse("on and off cannot share the same pixels", inactive.data.contentEquals(active.data))
        assertFalse("the same state is not published twice", item.setHoverActive(true))
    }

    @Test
    fun `the status notifier starts with the requested hover state`() {
        val activeItem = StatusNotifierItem(
            onActivate = {},
            onSecondaryActivate = {},
            onContextMenu = { _, _ -> },
            currentStatus = { "" },
            initialHoverActive = true,
        )
        val inactiveItem = StatusNotifierItem(
            onActivate = {},
            onSecondaryActivate = {},
            onContextMenu = { _, _ -> },
            currentStatus = { "" },
            initialHoverActive = false,
        )

        val active = activeItem.getIconPixmap().single()
        val inactive = inactiveItem.getIconPixmap().single()
        assertFalse(
            "an icon created after hover was enabled starts green, not red",
            active.data.contentEquals(inactive.data),
        )
        assertFalse("the initial active state is not a transition", activeItem.setHoverActive(true))
    }

    private fun testItem() = StatusNotifierItem(
        onActivate = {},
        onSecondaryActivate = {},
        onContextMenu = { _, _ -> },
        currentStatus = { "" },
    )

    private fun assertArrayEquals(expected: ByteArray, actual: ByteArray) {
        assertEquals(expected.toList(), actual.toList())
    }
}
