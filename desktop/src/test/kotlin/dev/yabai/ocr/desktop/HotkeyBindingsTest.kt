package dev.yabai.ocr.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HotkeyBindingsTest {

    private fun bindings() = hotkeyBindings(
        fullScreen = "Ctrl + Shift + O",
        region = "Ctrl + Shift + A",
        quickDict = "Ctrl + Shift + D",
        hover = "Ctrl + Shift + H",
        hoverCycle = "Ctrl + Shift + X",
    )

    @Test
    fun `every action has a stable distinct id and a non-empty description`() {
        val list = bindings()
        val ids = list.map { it.id }

        assertEquals(
            listOf(
                HotkeyIds.FULL_SCREEN,
                HotkeyIds.REGION,
                HotkeyIds.QUICK_DICT,
                HotkeyIds.HOVER_TOGGLE,
                HotkeyIds.HOVER_CYCLE,
            ),
            ids,
        )
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(list.all { it.description.isNotBlank() })
        assertEquals(
            listOf(
                "capture-fullscreen",
                "capture-region",
                "quick-dict",
                "hover-toggle",
                "dict-cycle",
            ),
            ids,
        )
    }

    @Test
    fun `each id maps to exactly one action`() {
        assertEquals(HotkeyAction.FULL_SCREEN, hotkeyActionForId(HotkeyIds.FULL_SCREEN))
        assertEquals(HotkeyAction.REGION, hotkeyActionForId(HotkeyIds.REGION))
        assertEquals(HotkeyAction.QUICK_DICT, hotkeyActionForId(HotkeyIds.QUICK_DICT))
        assertEquals(HotkeyAction.HOVER_TOGGLE, hotkeyActionForId(HotkeyIds.HOVER_TOGGLE))
        assertEquals(HotkeyAction.HOVER_CYCLE, hotkeyActionForId(HotkeyIds.HOVER_CYCLE))
        assertEquals(HotkeyAction.UNKNOWN, hotkeyActionForId("older-portal-id"))
    }

    @Test
    fun `dispatching a shortcut calls only its own action`() {
        val calls = mutableListOf<String>()
        val callbacks = HotkeyCallbacks(
            onFullScreen = { calls.add("full") },
            onRegion = { calls.add("region") },
            onQuickDict = { calls.add("dict") },
            onHoverToggle = { calls.add("hover") },
            onHoverCycle = { calls.add("cycle") },
        )

        listOf(
            HotkeyIds.FULL_SCREEN,
            HotkeyIds.REGION,
            HotkeyIds.QUICK_DICT,
            HotkeyIds.HOVER_TOGGLE,
            HotkeyIds.HOVER_CYCLE,
        ).forEach(callbacks::dispatch)
        callbacks.dispatch("unknown")

        assertEquals(listOf("full", "region", "dict", "hover", "cycle"), calls)
    }

    @Test
    fun `the configured combination is converted to a preferred trigger`() {
        val list = bindings()

        assertEquals("CTRL+SHIFT+O", list[0].preferredTrigger)
        assertEquals("CTRL+SHIFT+A", list[1].preferredTrigger)
        assertEquals("CTRL+SHIFT+D", list[2].preferredTrigger)
        assertEquals("CTRL+SHIFT+H", list[3].preferredTrigger)
        assertEquals("CTRL+SHIFT+X", list[4].preferredTrigger)
    }

    @Test
    fun `portal requests drop the shortcuts the portal cannot represent`() {
        val requests = portalRequests(
            hotkeyBindings(
                fullScreen = "O",
                region = "Ctrl + Shift + A",
                quickDict = "Ctrl + ;",
                hover = "Ctrl + Shift + H",
                hoverCycle = "Shift + Super + X",
            ),
        )

        assertEquals(
            listOf(HotkeyIds.REGION, HotkeyIds.HOVER_TOGGLE, HotkeyIds.HOVER_CYCLE),
            requests.map { it.id },
        )
        assertEquals(
            listOf("CTRL+SHIFT+A", "CTRL+SHIFT+H", "SHIFT+LOGO+X"),
            requests.map { it.preferredTrigger },
        )
        assertNotEquals(requests[0].description, requests[1].description)
    }
}
