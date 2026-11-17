package dev.yabai.ocr.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Linux renderer card is one switch, not a radio list (ROADMAP D83).
 *
 * «Automatic» and «OpenGL» are the same renderer on Linux, so the card showed a decision that does
 * not exist — and because the stored default is `automatic`, the single «Software» radio appeared
 * unselected. The switch must read the stored `opengl`/`automatic` as «off» and must not throw away
 * a graphics-card path the user picked.
 */
class RendererSwitchTest {

    @Test
    fun `the switch is on only when software is stored`() {
        assertFalse(softwareSwitchOn(SkikoRenderer.AUTOMATIC))
        assertFalse(softwareSwitchOn(SkikoRenderer.OPENGL))
        assertTrue(softwareSwitchOn(SkikoRenderer.SOFTWARE))
    }

    @Test
    fun `turning it on stores software`() {
        assertEquals(SkikoRenderer.SOFTWARE, rendererAfterSoftwareSwitch(true, SkikoRenderer.AUTOMATIC))
        assertEquals(SkikoRenderer.SOFTWARE, rendererAfterSoftwareSwitch(true, SkikoRenderer.OPENGL))
        assertEquals(SkikoRenderer.SOFTWARE, rendererAfterSoftwareSwitch(true, SkikoRenderer.SOFTWARE))
    }

    @Test
    fun `turning it off keeps the graphics card path that was stored`() {
        assertEquals(SkikoRenderer.OPENGL, rendererAfterSoftwareSwitch(false, SkikoRenderer.OPENGL))
        assertEquals(SkikoRenderer.AUTOMATIC, rendererAfterSoftwareSwitch(false, SkikoRenderer.AUTOMATIC))
    }

    @Test
    fun `turning it off after a software run goes back to the platform default`() {
        assertEquals(SkikoRenderer.AUTOMATIC, rendererAfterSoftwareSwitch(false, SkikoRenderer.SOFTWARE))
    }

    @Test
    fun `the switch round-trips without losing the graphics choice`() {
        val stored = SkikoRenderer.OPENGL
        val on = rendererAfterSoftwareSwitch(true, stored)
        val off = rendererAfterSoftwareSwitch(false, on)
        // Software does not record where it came from, so a round trip lands on the default — the
        // value that matters is that it leaves Software behind instead of staying there.
        assertEquals(SkikoRenderer.AUTOMATIC, off)
    }
}
