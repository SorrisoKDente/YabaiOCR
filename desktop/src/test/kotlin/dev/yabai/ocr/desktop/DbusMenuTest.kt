package dev.yabai.ocr.desktop

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.freedesktop.dbus.Marshalling
import org.freedesktop.dbus.types.UInt32
import org.freedesktop.dbus.types.Variant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DbusMenuTest {

    private fun menu(onClick: (Int) -> Unit = {}): DbusMenu = DbusMenu {
        listOf(
            StatusMenuItem(1, "Capturar", action = { onClick(1) }),
            StatusMenuItem(2, "Hover", action = { onClick(2) }),
            StatusMenuItem(10, separator = true),
            StatusMenuItem(3, "Sair", action = { onClick(3) }),
        )
    }

    @Test
    fun `the root layout exposes every action and separator`() {
        val layout = menu().GetLayout(0, 1, emptyList())

        assertEquals(0, layout.id)
        assertEquals("submenu", layout.properties["children-display"]?.value)
        assertEquals(4, layout.children.size)

        val first = layout.children[0].value as DbusMenuPayload
        assertEquals(1, first.id)
        assertEquals("Capturar", first.properties["label"]?.value)
        assertEquals("standard", first.properties["type"]?.value)

        val separator = layout.children[2].value as DbusMenuPayload
        assertEquals(10, separator.id)
        assertEquals("separator", separator.properties["type"]?.value)
        assertFalse(separator.properties.containsKey("label"))
    }

    @Test
    fun `properties and group properties read the current labels`() {
        val dbusMenu = menu()

        assertEquals("Hover", dbusMenu.GetProperty(2, "label").value)
        assertEquals("separator", dbusMenu.GetProperty(10, "type").value)
        assertEquals(3, dbusMenu.GetGroupProperties(listOf(1, 2, 3), emptyList()).size)
        assertTrue(dbusMenu.GetProperty(999, "label").value == "")
    }

    @Test
    fun `clicking an item dispatches the action on the awt thread`() {
        val clicked = mutableListOf<Int>()
        val latch = CountDownLatch(1)
        val dbusMenu = menu { id ->
            clicked.add(id)
            latch.countDown()
        }

        dbusMenu.Event(2, "clicked", Variant(""), UInt32(0))

        assertTrue(latch.await(2, TimeUnit.SECONDS))
        assertEquals(listOf(2), clicked)
    }

    @Test
    fun `separator and unknown events do nothing`() {
        val clicked = mutableListOf<Int>()
        val dbusMenu = menu { clicked.add(it) }

        dbusMenu.Event(10, "clicked", Variant(""), UInt32(0))
        dbusMenu.Event(999, "clicked", Variant(""), UInt32(0))
        dbusMenu.Event(1, "hovered", Variant(""), UInt32(0))

        Thread.sleep(100)
        assertTrue(clicked.isEmpty())
    }

    @Test
    fun `the dbus methods declare every out argument the protocol requires`() {
        // GetLayout returns (u revision, (ia{sv}av) layout); AboutToShowGroup returns two ai arrays.
        // Returning a single struct compiles and passes a naive client test, but Plasma rejects it.
        assertEquals(
            listOf("u", "(ia{sv}av)"),
            Marshalling.getDBusType(DbusMenuLayout::class.java).toList(),
        )
        assertEquals(
            listOf("ai", "ai"),
            Marshalling.getDBusType(DbusMenuAboutToShow::class.java).toList(),
        )
    }

    @Test
    fun `about to show forces a fresh layout and child layouts are empty`() {
        val dbusMenu = menu()

        assertTrue(dbusMenu.AboutToShow(0))
        assertFalse(dbusMenu.AboutToShow(1))
        assertTrue(dbusMenu.GetLayout(1, 1, emptyList()).children.isEmpty())
        assertEquals(listOf(0), dbusMenu.AboutToShowGroup(listOf(0, 1)).updatesNeeded)
    }
}
