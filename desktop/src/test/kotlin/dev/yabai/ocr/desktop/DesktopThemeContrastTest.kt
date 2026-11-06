package dev.yabai.ocr.desktop

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the contrast of the desktop theme.
 *
 * The theme toggle exists since the light palette was added, and windows used to paint labels with
 * a fixed `Color.White`. That is invisible on a light surface: the «Padrão» buttons next to each
 * hotkey looked empty. The palette below is the single place those colours come from, so checking
 * it here is checking every window that uses it.
 */
class DesktopThemeContrastTest {

    private val minimumBodyContrast = 4.5

    private fun assertReadable(name: String, color: Color, background: Color) {
        val ratio = contrastRatio(color, background)
        assertTrue(
            "$name has contrast ${"%.2f".format(ratio)} below $minimumBodyContrast",
            ratio >= minimumBodyContrast,
        )
    }

    @Test
    fun `every semantic text colour is readable on the surface and the background of its theme`() {
        for (darkTheme in listOf(false, true)) {
            val theme = if (darkTheme) "dark" else "light"
            val colors = desktopColors(darkTheme)
            val palette = paletteFor(darkTheme)

            for (background in listOf(colors.surface, colors.background)) {
                assertReadable("$theme onSurface", colors.onSurface, background)
                assertReadable("$theme secondary", colors.secondary, background)
                assertReadable("$theme success", palette.success, background)
                assertReadable("$theme danger", palette.danger, background)
                assertReadable("$theme warning", palette.warning, background)
                assertReadable("$theme info", palette.info, background)
            }
            assertReadable("$theme chipText", palette.chipText, palette.chip)
        }
    }

    @Test
    fun `the light and the dark palette are actually different`() {
        val light = paletteFor(darkTheme = false)
        val dark = paletteFor(darkTheme = true)

        assertTrue(light.field != dark.field)
        assertTrue(light.success != dark.success)
        assertTrue(light.warning != dark.warning)
    }

    @Test
    fun `the filled button label is readable in both themes`() {
        for (darkTheme in listOf(false, true)) {
            val theme = if (darkTheme) "dark" else "light"
            val palette = paletteFor(darkTheme)
            val ratio = contrastRatio(palette.onButton, palette.button)
            // A button label is 14 sp: not WCAG's "large text", so it needs the full 4.5:1.
            assertTrue("$theme filled button has contrast ${"%.2f".format(ratio)}", ratio >= 4.5)
        }
    }

    @Test
    fun `the accent stays visible as a non-text element on the background`() {
        for (darkTheme in listOf(false, true)) {
            val theme = if (darkTheme) "dark" else "light"
            val colors = desktopColors(darkTheme)
            val ratio = contrastRatio(colors.primary, colors.background)
            // Used by radio buttons, checkboxes and progress bars: WCAG asks 3:1 for UI parts.
            assertTrue("$theme accent has contrast ${"%.2f".format(ratio)}", ratio >= 3.0)
        }
    }

    @Test
    fun `a fixed white label would be unreadable on the light theme`() {
        assertEquals(1.0, contrastRatio(Color.White, Color.White), 0.001)
        assertTrue(contrastRatio(Color.White, desktopColors(darkTheme = false).surface) < 1.1)
        assertTrue(contrastRatio(Color.White, desktopColors(darkTheme = true).surface) > 10.0)
    }
}
