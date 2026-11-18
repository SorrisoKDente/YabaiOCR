package dev.yabai.ocr.desktop

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tray icon is the only always-visible part of the app. Hover on/off has to be readable from
 * the pixels alone, because the user asked to know it at a glance, without opening the menu.
 */
class TrayHoverStatusIconTest {

    @Test
    fun `the active badge is green and the inactive one is red with a minus`() {
        val base = solidBase()
        val active = createTrayStatusImage(base, hoverActive = true, size = 128)
        val inactive = createTrayStatusImage(base, hoverActive = false, size = 128)
        val badge = trayHoverBadgeGeometry(128)

        assertEquals(
            "hover on must be the green dot",
            TRAY_HOVER_ACTIVE_COLOR.rgb,
            active.getRGB(badge.centerX, badge.centerY),
        )
        assertEquals(
            "hover off must be the red dot",
            TRAY_HOVER_INACTIVE_COLOR.rgb,
            inactive.getRGB(badge.centerX, badge.centerY - badge.radius / 2),
        )
        assertEquals(
            "hover off must also read as a minus, not only as a colour",
            TRAY_HOVER_SLASH_COLOR.rgb,
            inactive.getRGB(badge.centerX, badge.centerY),
        )
    }

    @Test
    fun `the status dot stays inside the icon at 16 px`() {
        val badge = trayHoverBadgeGeometry(16)

        assertTrue("the badge must not be clipped on the left", badge.centerX - badge.outlineRadius >= 0)
        assertTrue("the badge must not be clipped on the right", badge.centerX + badge.outlineRadius <= 16)
        assertTrue("the badge must not be clipped on the bottom", badge.centerY + badge.outlineRadius <= 16)
        assertTrue("the badge must not be clipped on the top", badge.centerY - badge.outlineRadius >= 0)
    }

    @Test
    fun `the production tray image carries the current hover state`() {
        val active = AppIconGenerator.createTrayImage(hoverActive = true) as BufferedImage
        val inactive = AppIconGenerator.createTrayImage(hoverActive = false) as BufferedImage
        val badge = trayHoverBadgeGeometry(active.width)

        assertEquals(active.width, active.height)
        assertEquals(inactive.width, inactive.height)
        assertEquals(
            TRAY_HOVER_ACTIVE_COLOR.rgb,
            active.getRGB(badge.centerX, badge.centerY),
        )
        assertEquals(
            TRAY_HOVER_SLASH_COLOR.rgb,
            inactive.getRGB(badge.centerX, badge.centerY),
        )
        assertNotEquals(
            "the two states cannot share the same pixels",
            active.getRGB(badge.centerX, badge.centerY),
            inactive.getRGB(badge.centerX, badge.centerY),
        )
    }

    @Test
    fun `both tray backends replace the image when hover changes`() {
        // AWT needs a real display to construct a TrayIcon, so this path cannot be exercised in the
        // headless suite. The source guard keeps the one-line assignment and the initial state from
        // silently disappearing while the generator and the SNI pixmap remain covered above.
        val awt = File("src/main/kotlin/dev/yabai/ocr/desktop/SystemTrayManager.kt").readText()
        assertTrue(
            "AWT must create the first icon with the current hover state",
            awt.contains("val active = isHoverActive()"),
        )
        assertTrue(
            "AWT must hand the current state to the StatusNotifier item",
            awt.contains("initialHoverActive = active"),
        )
        assertTrue(
            "AWT must replace TrayIcon.image when the state changes",
            awt.contains("trayIcon?.image = AppIconGenerator.createTrayImage(active)"),
        )

        val sni = File("src/main/kotlin/dev/yabai/ocr/desktop/StatusNotifierTray.kt").readText()
        assertTrue(
            "SNI must swap the cached pixmap before announcing it",
            sni.contains("if (!item.setHoverActive(active)) return"),
        )
        assertTrue(
            "SNI must emit NewIcon so the host reads the new pixels",
            sni.contains("StatusNotifierItemDbus.NewIcon(SNI_OBJECT_PATH)"),
        )
    }

    private fun solidBase(): BufferedImage =
        BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB).apply {
            val graphics = createGraphics()
            try {
                graphics.color = Color(0x10, 0x12, 0x18)
                graphics.fillRect(0, 0, width, height)
            } finally {
                graphics.dispose()
            }
        }
}
