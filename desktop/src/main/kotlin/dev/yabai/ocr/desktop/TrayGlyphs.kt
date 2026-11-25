package dev.yabai.ocr.desktop

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Ellipse2D
import java.awt.geom.Line2D
import java.awt.geom.RoundRectangle2D
import javax.swing.Icon

/**
 * The glyphs of the tray menu.
 *
 * The rows used to carry colour emoji in their label, and Swing draws emoji in a single ink colour —
 * on Windows the menu came out as flat silhouettes. Every glyph is drawn here with Java2D instead, so
 * its colour is ours and does not depend on a colour-emoji font the JVM cannot use.
 */
internal enum class TrayGlyph { MONITOR, SCISSORS, MAGNIFIER, BUBBLE, BOOK, UPDATE, GEAR, EXIT }

/** Accent of each glyph, matched to the card colours of the app. */
internal fun trayGlyphColor(glyph: TrayGlyph): Color = when (glyph) {
    TrayGlyph.MONITOR -> Color(0x4C, 0x8D, 0xFF)
    TrayGlyph.SCISSORS -> Color(0xFF, 0x9F, 0x43)
    TrayGlyph.MAGNIFIER -> Color(0xA7, 0x7B, 0xFF)
    TrayGlyph.BUBBLE -> Color(0x3D, 0xDC, 0x97)
    TrayGlyph.BOOK -> Color(0xFF, 0xC0, 0x48)
    TrayGlyph.UPDATE -> Color(0xA8, 0xE0, 0x4C)
    TrayGlyph.GEAR -> Color(0x35, 0xC4, 0xE0)
    TrayGlyph.EXIT -> Color(0xFF, 0x6B, 0x6B)
}

/** A coloured menu glyph, sized for a tray row. */
internal class TrayGlyphIcon(
    private val glyph: TrayGlyph,
    private val size: Int = 16,
) : Icon {
    override fun getIconWidth(): Int = size
    override fun getIconHeight(): Int = size
    override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) =
        paintTrayGlyph(g, glyph, x, y, size)
}

/** Draws [glyph] in a [size]×[size] square whose top-left corner is ([x], [y]). */
internal fun paintTrayGlyph(g: Graphics, glyph: TrayGlyph, x: Int, y: Int, size: Int) {
    val g2 = g.create() as Graphics2D
    try {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
        g2.translate(x, y)
        g2.color = trayGlyphColor(glyph)
        val s = size.toFloat()
        g2.stroke = BasicStroke(
            maxOf(1.4f, s * 0.11f),
            BasicStroke.CAP_ROUND,
            BasicStroke.JOIN_ROUND,
        )
        when (glyph) {
            TrayGlyph.MONITOR -> {
                g2.draw(RoundRectangle2D.Float(s * 0.10f, s * 0.16f, s * 0.80f, s * 0.52f, s * 0.16f, s * 0.16f))
                g2.draw(Line2D.Float(s * 0.50f, s * 0.68f, s * 0.50f, s * 0.82f))
                g2.draw(Line2D.Float(s * 0.30f, s * 0.84f, s * 0.70f, s * 0.84f))
            }
            TrayGlyph.SCISSORS -> {
                g2.draw(Line2D.Float(s * 0.30f, s * 0.20f, s * 0.78f, s * 0.74f))
                g2.draw(Line2D.Float(s * 0.70f, s * 0.20f, s * 0.22f, s * 0.74f))
                g2.fill(Ellipse2D.Float(s * 0.06f, s * 0.64f, s * 0.26f, s * 0.26f))
                g2.fill(Ellipse2D.Float(s * 0.68f, s * 0.64f, s * 0.26f, s * 0.26f))
            }
            TrayGlyph.MAGNIFIER -> {
                g2.draw(Ellipse2D.Float(s * 0.12f, s * 0.12f, s * 0.52f, s * 0.52f))
                g2.draw(Line2D.Float(s * 0.62f, s * 0.62f, s * 0.88f, s * 0.88f))
            }
            TrayGlyph.BUBBLE -> {
                g2.draw(RoundRectangle2D.Float(s * 0.08f, s * 0.14f, s * 0.84f, s * 0.56f, s * 0.30f, s * 0.30f))
                g2.draw(Line2D.Float(s * 0.34f, s * 0.68f, s * 0.26f, s * 0.90f))
            }
            TrayGlyph.BOOK -> {
                g2.draw(RoundRectangle2D.Float(s * 0.08f, s * 0.18f, s * 0.38f, s * 0.64f, s * 0.10f, s * 0.10f))
                g2.draw(RoundRectangle2D.Float(s * 0.54f, s * 0.18f, s * 0.38f, s * 0.64f, s * 0.10f, s * 0.10f))
            }
            TrayGlyph.UPDATE -> {
                // The arrow the menu row used to carry as an emoji, over the line an install lands on:
                // an update is downloaded and replaces the current version (R52).
                g2.draw(Line2D.Float(s * 0.50f, s * 0.74f, s * 0.50f, s * 0.26f))
                g2.draw(Line2D.Float(s * 0.28f, s * 0.46f, s * 0.50f, s * 0.24f))
                g2.draw(Line2D.Float(s * 0.72f, s * 0.46f, s * 0.50f, s * 0.24f))
                g2.draw(Line2D.Float(s * 0.24f, s * 0.84f, s * 0.76f, s * 0.84f))
            }
            TrayGlyph.GEAR -> {
                g2.draw(Ellipse2D.Float(s * 0.22f, s * 0.22f, s * 0.56f, s * 0.56f))
                for (tooth in 0 until 8) {
                    val angle = tooth * Math.PI / 4.0
                    val cx = s / 2f + (Math.cos(angle) * s * 0.42).toFloat()
                    val cy = s / 2f + (Math.sin(angle) * s * 0.42).toFloat()
                    g2.fill(Ellipse2D.Float(cx - s * 0.06f, cy - s * 0.06f, s * 0.12f, s * 0.12f))
                }
            }
            TrayGlyph.EXIT -> {
                g2.draw(Line2D.Float(s * 0.22f, s * 0.22f, s * 0.78f, s * 0.78f))
                g2.draw(Line2D.Float(s * 0.78f, s * 0.22f, s * 0.22f, s * 0.78f))
            }
        }
    } finally {
        g2.dispose()
    }
}

/**
 * The label without its leading emoji.
 *
 * The tray rows now draw [TrayGlyphIcon] beside the text, and the emoji the strings still carry for
 * the Linux native menu would be a second, monochrome icon in front of the coloured one.
 */
internal fun withoutEmojiPrefix(text: String): String {
    val trimmed = text.trim()
    val firstLetter = trimmed.indexOfFirst { it.isLetterOrDigit() }
    return if (firstLetter > 0) trimmed.substring(firstLetter) else trimmed
}
