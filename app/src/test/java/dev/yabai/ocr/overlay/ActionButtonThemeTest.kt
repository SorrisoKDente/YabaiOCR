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
 * «Desligado» in the dictionary screen became white on white — unreadable.
 *
 * These tests build the real button after switching the theme the same way the app does, and check
 * that its label takes the theme's text colour and stays readable over the fill it is painted on.
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
    fun `in the light theme the label is the dark text colour, not white`() {
        applyTheme(AppThemeChoice.LIGHT)

        val button = context.actionButton("Verificar atualizações")

        assertEquals(context.getColor(R.color.text_primary), button.currentTextColor)
        assertTrue("the label is still white", button.currentTextColor != Color.WHITE)
        assertReadable(button.currentTextColor, context.actionButtonBackground)
    }

    @Test
    fun `in the dark theme the label is the light text colour and stays readable`() {
        applyTheme(AppThemeChoice.DARK)

        val button = context.actionButton("Verificar atualizações")

        assertEquals(context.getColor(R.color.text_primary), button.currentTextColor)
        assertReadable(button.currentTextColor, context.actionButtonBackground)
    }

    @Test
    fun `the plain fill follows the theme instead of a fixed white`() {
        applyTheme(AppThemeChoice.LIGHT)
        val light = context.actionButtonBackground
        applyTheme(AppThemeChoice.DARK)
        val dark = context.actionButtonBackground

        assertEquals(context.getColor(R.color.card_bg), dark)
        assertTrue("the fill is the same in both themes", light != dark)
    }

    private fun assertReadable(text: Int, fill: Int) {
        val behind = context.getColor(R.color.panel_bg)
        val ratio = contrast(text, blend(fill, behind))
        assertTrue("button contrast is ${"%.2f".format(ratio)}", ratio >= 4.5)
    }

    /** [card_bg] is translucent, so what the user sees is it composited over [panel_bg]. */
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
