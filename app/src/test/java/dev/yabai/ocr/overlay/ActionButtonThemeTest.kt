package dev.yabai.ocr.overlay

import android.content.Context
import android.graphics.Color
import dev.yabai.ocr.AppSettings
import dev.yabai.ocr.AppThemeChoice
import dev.yabai.ocr.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The action buttons used to be white text on a translucent white fill, which was chosen when the
 * panels were always dark. Once the app gained a light theme, «Verificar atualizações» and
 * «Desligado» in the dictionary screen became white on white — unreadable; the fix gave them the
 * theme's text colour over a translucent grey, which was readable but read as «disabled».
 *
 * R46c makes the plain button the app's accent with a white label, and a destructive action the
 * desktop's red. These tests build the real button after switching the theme the same way the app
 * does, and check both the fill and that the label stays readable on it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ActionButtonThemeTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    /** The same call the settings radio buttons make; it rewrites the app resources' night mode. */
    private fun applyTheme(choice: AppThemeChoice) {
        AppSettings(context).appTheme = choice
    }

    @Test
    fun `the plain button is the accent with a white label in the light theme`() {
        applyTheme(AppThemeChoice.LIGHT)

        val button = context.actionButton("Verificar atualizações")

        assertEquals(context.getColor(R.color.accent), context.actionButtonBackground)
        assertEquals(Color.WHITE, button.currentTextColor)
        assertReadable(button.currentTextColor, context.actionButtonBackground)
    }

    @Test
    fun `the plain button is the accent with a white label in the dark theme`() {
        applyTheme(AppThemeChoice.DARK)

        val button = context.actionButton("Verificar atualizações")

        assertEquals(context.getColor(R.color.accent), context.actionButtonBackground)
        assertEquals(Color.WHITE, button.currentTextColor)
        assertReadable(button.currentTextColor, context.actionButtonBackground)
    }

    /**
     * «Remover» is the one button in a dictionary card that throws the work away, so it cannot look
     * like «↑» and «↓» beside it.
     */
    @Test
    fun `the destructive button is red with a white label`() {
        applyTheme(AppThemeChoice.LIGHT)

        val button = context.actionButton(
            "Remover",
            background = context.getColor(R.color.warn),
        )

        assertEquals(Color.WHITE, button.currentTextColor)
        assertReadable(button.currentTextColor, context.getColor(R.color.warn))
    }

    /**
     * A caller that paints its own fill states its own label colour: the transparent «✕» keeps the
     * theme's text colour instead of inheriting the accent's white and vanishing over a page.
     */
    @Test
    fun `a button with its own fill keeps the theme's text colour`() {
        applyTheme(AppThemeChoice.LIGHT)

        val clear = context.actionButton(
            "✕",
            textColor = context.getColor(R.color.text_primary),
            background = 0x00000000,
        )

        assertEquals(context.getColor(R.color.text_primary), clear.currentTextColor)
    }

    private fun assertReadable(text: Int, fill: Int) {
        val behind = context.getColor(R.color.panel_bg)
        val ratio = contrast(text, blend(fill, behind))
        assertTrue("button contrast is ${"%.2f".format(ratio)}", ratio >= 4.5)
    }

    /** [card_bg] was translucent; the accent is opaque, so this is the identity for it. */
    private fun blend(over: Int, under: Int): Int {
        val alpha = Color.alpha(over) / 255f
        fun channel(a: Int, b: Int) = (a * alpha + b * (1 - alpha)).toInt().coerceIn(0, 255)
        return Color.rgb(
            channel(Color.red(over), Color.red(under)),
            channel(Color.green(over), Color.green(under)),
            channel(Color.blue(over), Color.blue(under)),
        )
    }

    private fun contrast(a: Int, b: Int): Double {
        val la = Color.luminance(a)
        val lb = Color.luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }
}
