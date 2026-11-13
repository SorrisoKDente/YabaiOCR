package dev.yabai.ocr.desktop

import java.awt.Color
import java.awt.image.BufferedImage
import org.freedesktop.dbus.annotations.DBusBoundProperty
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
    fun `x11 windows and macos keep the awt tray`() {
        assertEquals(TrayBackend.AWT, trayBackendFor(OsFamily.LINUX, SessionType.X11))
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
    }

    @Test
    fun `the icon pixmap property declares the concrete struct array type`() {
        // A raw List return type loses its generic argument in reflection, so dbus-java would
        // advertise `av`. KDE ignores an IconPixmap that is not `a(iiay)` and the tray would show
        // no icon. The explicit annotation type is what makes Marshalling emit the struct array.
        val method = StatusNotifierItemDbus::class.java.getMethod("getIconPixmap")
        val annotation = method.getAnnotation(DBusBoundProperty::class.java)

        assertTrue("IconPixmap must declare an explicit type", annotation.type.java.isArray)
        assertEquals(StatusNotifierIconPixmap::class.java, annotation.type.java.componentType)
    }

    private fun assertArrayEquals(expected: ByteArray, actual: ByteArray) {
        assertEquals(expected.toList(), actual.toList())
    }
}
