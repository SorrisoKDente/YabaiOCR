package dev.yabai.ocr.desktop

import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Colours that are not part of Material's [androidx.compose.material.Colors] but are used by more
 * than one window: field fills, status text and tinted containers.
 *
 * Every entry has a light and a dark value, so a window never has to hardcode `Color.White` or a
 * dark hex for text. Hardcoding those is what made the «Padrão» buttons invisible on the light
 * theme: the text stayed white while the surface behind it became white too.
 */
internal data class YabaiPalette(
    /** Fill of an input/field or a "console" box. */
    val field: Color,
    /** Border of a field. */
    val fieldBorder: Color,
    /** Subtle chip behind a suggestion. */
    val chip: Color,
    /** Text on [chip]. */
    val chipText: Color,
    /**
     * Fill of a filled button. Separate from Material's `primary`, which is also used as an accent
     * on top of the background: a fill needs to be dark enough for [onButton], an accent needs to
     * be light enough for the dark background, and one colour cannot be both.
     */
    val button: Color,
    /** Label on [button]. */
    val onButton: Color,
    val success: Color,
    val successContainer: Color,
    val danger: Color,
    val dangerContainer: Color,
    val warning: Color,
    val info: Color,
)

private val LightPalette = YabaiPalette(
    field = Color(0xFFEFF1F5),
    fieldBorder = Color(0xFFD5DAE3),
    chip = Color(0xFFE8EBF1),
    chipText = Color(0xFF2B4FC4),
    button = Color(0xFF3D6BF3),
    onButton = Color.White,
    success = Color(0xFF2E7D32),
    successContainer = Color(0xFFE8F5E9),
    danger = Color(0xFFC62828),
    dangerContainer = Color(0xFFFFEBEE),
    warning = Color(0xFFA15C00),
    info = Color(0xFF1565C0),
)

private val DarkPalette = YabaiPalette(
    field = Color(0xFF141620),
    fieldBorder = Color(0xFF2E3344),
    chip = Color(0xFF232736),
    chipText = Color(0xFF7C9BFF),
    button = Color(0xFF3B63D9),
    onButton = Color.White,
    success = Color(0xFF81C784),
    successContainer = Color(0xFF1B382B),
    danger = Color(0xFFFFB4AB),
    dangerContainer = Color(0xFF3A1C1C),
    warning = Color(0xFFE5A93C),
    info = Color(0xFF81D4FA),
)

/** The palette that matches the current [MaterialTheme]. */
internal fun paletteFor(darkTheme: Boolean): YabaiPalette = if (darkTheme) DarkPalette else LightPalette

@Composable
internal fun yabaiPalette(): YabaiPalette = paletteFor(!MaterialTheme.colors.isLight)

/** WCAG 2.1 relative contrast between two opaque colours. Used by the regression test. */
internal fun contrastRatio(foreground: Color, background: Color): Double {
    val light = maxOf(foreground.luminance(), background.luminance())
    val dark = minOf(foreground.luminance(), background.luminance())
    return (light + 0.05) / (dark + 0.05)
}

@Composable
fun YabaiDesktopTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colors = desktopColors(darkTheme),
        content = content
    )
}

/** The Material colours of the app, in one place so the contrast test checks what the windows use. */
internal fun desktopColors(darkTheme: Boolean): androidx.compose.material.Colors {
    val palette = paletteFor(darkTheme)
    return if (darkTheme) {
        darkColors(
            background = Color(0xFF12141A),
            surface = Color(0xFF1B1E28),
            primary = Color(0xFF4C78FF),
            primaryVariant = Color(0xFF3D6BF3),
            secondary = Color(0xFFA0A6B8),
            onPrimary = Color.White,
            onBackground = Color.White,
            onSurface = Color.White,
            error = palette.danger,
            onError = Color(0xFF3A1C1C),
        )
    } else {
        lightColors(
            background = Color(0xFFF4F5F8),
            surface = Color(0xFFFFFFFF),
            primary = Color(0xFF3D6BF3),
            primaryVariant = Color(0xFF2B4FC4),
            secondary = Color(0xFF5A6275),
            onPrimary = Color.White,
            onBackground = Color(0xFF1C1C1E),
            onSurface = Color(0xFF1C1C1E),
            error = palette.danger,
            onError = Color.White,
        )
    }
}
