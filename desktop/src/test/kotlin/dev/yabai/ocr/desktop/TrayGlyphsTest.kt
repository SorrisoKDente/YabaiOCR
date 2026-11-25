package dev.yabai.ocr.desktop

import java.awt.Color
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tray menu lost its colours on Windows: the rows carried emoji, which Swing paints in a single
 * ink colour. These tests pin the replacement — an icon drawn by the app for every row, in colour —
 * and the installer fix that lets an update replace the files of a running tray app.
 */
class TrayGlyphsTest {

    @Test
    fun `every tray row keeps its label without the emoji prefix`() {
        val labels = listOf(
            "\uD83D\uDDA5\uFE0F Capturar Tela Inteira (OCR)",
            "\u2702\uFE0F Capturar Área (OCR)",
            "\uD83D\uDD0D Busca Rápida de Dicionário",
            "\uD83D\uDCAC Modo Hover [Ativo]",
            "\uD83D\uDCD6 Gerenciar Dicionários",
            "\u2699\uFE0F Ajustes e Configurações",
            "\u2715 Sair do Yabai OCR",
        )
        assertEquals(
            listOf(
                "Capturar Tela Inteira (OCR)",
                "Capturar Área (OCR)",
                "Busca Rápida de Dicionário",
                "Modo Hover [Ativo]",
                "Gerenciar Dicionários",
                "Ajustes e Configurações",
                "Sair do Yabai OCR",
            ),
            labels.map(::withoutEmojiPrefix),
        )
    }

    @Test
    fun `a label that is already plain text is returned unchanged`() {
        assertEquals("Snippets", withoutEmojiPrefix("Snippets"))
        assertEquals("Trechos", withoutEmojiPrefix("  Trechos  "))
    }

    @Test
    fun `each glyph is painted with a visible colour of its own`() {
        val colours = TrayGlyph.values().map { glyph ->
            val image = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
            val g = image.createGraphics()
            TrayGlyphIcon(glyph).paintIcon(null, g, 0, 0)
            g.dispose()

            val pixels = (image.raster.dataBuffer as DataBufferInt).data
            val inked = pixels.filter { alphaOf(it) > 0 }
            assertTrue("$glyph painted almost nothing: ${inked.size} pixels", inked.size > 20)

            val reference = trayGlyphColor(glyph)
            // Antialiasing gives many shades; the untouched accent has to be there, and the ink has
            // to be chromatic, otherwise the row is the same grey silhouette as before.
            assertTrue(
                "$glyph does not use its accent ${Integer.toHexString(reference.rgb)}: ${inked.distinct()}",
                inked.any { it == reference.rgb },
            )
            val saturation = maxOf(reference.red, reference.green, reference.blue) -
                minOf(reference.red, reference.green, reference.blue)
            assertTrue("$glyph is too grey to read as a colour", saturation >= 40)
            reference.rgb
        }
        assertEquals("two rows share the same colour", colours.size, colours.distinct().size)
    }

    /**
     * R52: the updates row used to borrow the dictionary glyph, so two different rows showed the same
     * picture. It has its own arrow now, and the two drawings are not the same thing.
     */
    @Test
    fun `the updates row draws its own glyph, not the dictionaries one`() {
        val tray = File("src/main/kotlin/dev/yabai/ocr/desktop/SystemTrayManager.kt").readText()
        val updatesRow = tray.substringAfter("createTrayButton(DesktopStrings.trayUpdates")
            .substringBefore("{")
        assertTrue("the updates row lost its glyph: $updatesRow", updatesRow.contains("TrayGlyph.UPDATE"))
        assertFalse(
            "the updates row still uses the dictionaries icon: $updatesRow",
            updatesRow.contains("TrayGlyph.BOOK"),
        )
        assertFalse(
            "the update glyph is the book drawing under another name",
            samePixels(painted(TrayGlyph.UPDATE), painted(TrayGlyph.BOOK)),
        )
    }

    /**
     * R52: the shipped PNGs were left with the old artwork while the packed Windows icon had the new
     * one, so whichever surface fell back to the PNG (the Help window, the Linux entry) showed a
     * different icon from the tray.
     */
    @Test
    fun `the raster icons carry the artwork the Windows icon packs`() {
        val current = AppIconGenerator.icoFrame(resource("ic_scan_windows.ico").readBytes())
            ?: error("the Windows icon has no readable frame")

        for (name in listOf("ic_scan_linux.png", "ic_scan_windows.png")) {
            val png = ImageIO.read(resource(name))
            assertEquals(
                "$name has another size than the packed icon",
                current.width to current.height,
                png.width to png.height,
            )
            assertTrue("$name still carries the old artwork", samePixels(current, png))
        }
    }

    @Test
    fun `the glyph icon reports the size Swing asks for`() {
        val icon = TrayGlyphIcon(TrayGlyph.MONITOR)
        assertEquals(16, icon.iconWidth)
        assertEquals(16, icon.iconHeight)
    }

    @Test
    fun `the Windows installer terminates the tray app before replacing its files`() {
        val script = File("package/windows/installer.iss")
        assertTrue("installer script not found at ${script.absolutePath}", script.isFile)
        val text = script.readText()

        // The app hides its window instead of quitting, so Restart Manager alone never ends it.
        assertTrue(
            "Inno's polite close cannot end a tray app; it must not be relied on",
            text.contains("CloseApplications=no"),
        )
        assertTrue(
            "the installer has to end the running process before copying files",
            text.contains("taskkill.exe") && text.contains("/F"),
        )
        assertTrue(
            "the kill must run before the files are written, not after",
            text.contains("function PrepareToInstall("),
        )
    }

    @Test
    fun `the uninstaller terminates the tray app before deleting the files`() {
        val text = File("package/windows/installer.iss").readText()

        // Inno deletes the files after firing usUninstall, so the kill has to sit in that step; a
        // [UninstallRun] entry would be too late for the jars the app still has open.
        val step = text.substringAfter("procedure CurUninstallStepChanged(", "")
        assertTrue("the uninstaller has no CurUninstallStepChanged step", step.isNotEmpty())
        assertTrue(
            "the uninstaller only kills in the usUninstall step",
            step.contains("CurUninstallStep = usUninstall"),
        )
        assertTrue(
            "the usUninstall step has to end the running app",
            step.contains("TerminateRunningApp"),
        )
    }

    @Test
    fun `the executable name the installer kills is the one the packaging produces`() {
        val packaged = File("build.gradle.kts").readText()
        val name = Regex("""packageName = if \(isWindowsHost\) "([^"]+)"""")
            .find(packaged)
            ?.groupValues
            ?.get(1)
        assertEquals("Yabai OCR", name)

        val exe = "$name.exe"
        val text = File("package/windows/installer.iss").readText()
        // taskkill matches on the image name only, so it has to be exactly the packaged one.
        assertTrue(
            "the installer does not kill $exe",
            text.contains("/IM \"$exe\" /F"),
        )
        assertTrue("the installer does not launch $exe", text.contains("{app}\\$exe"))
    }

    @Test
    fun `the uninstaller deletes the autostart entry the app writes`() {
        val source = File("src/main/kotlin/dev/yabai/ocr/desktop/WindowsAutostart.kt").readText()
        val valueName = Regex("""APP_NAME = "([^"]+)"""")
            .find(source)
            ?.groupValues
            ?.get(1)
        assertEquals("YabaiOCR", valueName)
        // The Kotlin literal escapes the separators; the registry key needs the plain form.
        val key = Regex("""REG_KEY = "([^"]+)"""")
            .find(source)
            ?.groupValues
            ?.get(1)
            ?.replace("\\\\", "\\")
        assertEquals("Software\\Microsoft\\Windows\\CurrentVersion\\Run", key)

        val text = File("package/windows/installer.iss").readText()
        // Whole declarations, not `contains`: `...\\RunOnce` contains `...\\Run`, so a substring
        // check would accept a key the app never writes.
        assertTrue(
            "the installer does not declare the Run key the app writes ($key)",
            text.contains("AutostartRunKey = '$key';"),
        )
        assertTrue(
            "the installer constants still say $valueName",
            text.contains("AutostartValueName = '$valueName';"),
        )
        // Windows keeps the StartupApproved flag beside the Run value; a leftover flag makes a
        // later reinstall show up as disabled in «Startup apps».
        assertTrue(
            "the StartupApproved flag is not declared",
            text.contains(
                "AutostartApprovedKey = " +
                    "'Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\StartupApproved\\Run';",
            ),
        )

        val body = text.substringAfter("procedure RemoveAutostartEntry;", "")
        assertTrue("there is no RemoveAutostartEntry procedure", body.isNotEmpty())
        assertTrue(
            "the autostart value must be deleted from the Run key",
            body.contains("RegDeleteValue(HKCU, AutostartRunKey, AutostartValueName)"),
        )

        // Inno deletes files after usUninstall, and the leftover entry points there — so the entry
        // has to be gone in that same step, not after the files.
        val step = text.substringAfter("procedure CurUninstallStepChanged(", "")
        assertTrue(
            "the uninstaller has to call RemoveAutostartEntry",
            step.contains("RemoveAutostartEntry"),
        )
    }

    private fun painted(glyph: TrayGlyph): BufferedImage {
        val image = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        TrayGlyphIcon(glyph).paintIcon(null, g, 0, 0)
        g.dispose()
        return image
    }

    private fun samePixels(a: BufferedImage, b: BufferedImage): Boolean =
        a.width == b.width && a.height == b.height &&
            (0 until a.width).all { x -> (0 until a.height).all { y -> a.getRGB(x, y) == b.getRGB(x, y) } }

    private fun resource(name: String): File =
        sequenceOf(File("src/main/resources/$name"), File("desktop/src/main/resources/$name"))
            .firstOrNull { it.isFile } ?: error("$name not found from ${File(".").absolutePath}")

    private fun alphaOf(argb: Int): Int = (argb ushr 24) and 0xFF

    private val Color.rgb: Int get() = (red shl 16) or (green shl 8) or blue
}
