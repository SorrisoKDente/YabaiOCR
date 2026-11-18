package dev.yabai.ocr.desktop



import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO



/**

 * "Integrar ao sistema" on Linux: installs the application entry and the PNG icon inside the

 * user's XDG data folder. Nothing is written to `/usr`, so it works on the immutable SteamOS root.

 */

internal class LinuxSystemIntegration(

    private val dataHome: File = defaultDataHome(),

    private val executableResolver: () -> String? = { resolveLinuxExecutable() },

    private val runningExecutable: () -> String? = { runningExecutablePath() },

    private val wmClass: () -> String? = { runningWmClass() },

    private val iconBytes: () -> ByteArray? = {

        LinuxSystemIntegration::class.java

            .getResourceAsStream("/ic_scan_linux.png")

            ?.use { it.readBytes() }

    },

    // The UI injects the real menu refresh; the default keeps unit tests away from the host cache.
    private val menuRefresher: () -> Unit = {},

) {

    val desktopFile: File

        get() = File(File(dataHome, "applications"), "yabai-ocr.desktop")



    val iconFile: File

        get() = File(File(dataHome, "icons/hicolor/256x256/apps"), "$TRAY_ICON_NAME.png")



    val hoverOnIconFile: File

        get() = File(File(dataHome, "icons/hicolor/256x256/apps"), "$TRAY_HOVER_ON_ICON_NAME.png")



    val hoverOffIconFile: File

        get() = File(File(dataHome, "icons/hicolor/256x256/apps"), "$TRAY_HOVER_OFF_ICON_NAME.png")



    val authorizationDesktopFile: File

        get() = File(File(dataHome, "applications"), "yabai-ocr-screenshot.desktop")



    /**

     * Rewrites the hidden entry KWin uses to authorize `org.kde.KWin.ScreenShot2`.

     *

     * This runs on every start because the entry must name the binary that is running right now: an

     * AppImage extracts itself to a new `/tmp/.mount_*` folder each launch, so a stale entry would

     * authorize nothing. Failure is not fatal — capture then goes through the portal, with its

     * permission window.

     */

    fun ensureScreenshotAuthorization(): Boolean {

        val executable = runningExecutable()?.takeIf { it.isNotBlank() } ?: return false

        return runCatching {

            authorizationDesktopFile.parentFile?.mkdirs()

            val content = screenshotAuthorizationDesktopEntry(executable)

            if (!authorizationDesktopFile.isFile || authorizationDesktopFile.readText() != content) {

                authorizationDesktopFile.writeText(content)

            }

            true

        }.getOrDefault(false)

    }



    /**

     * Writes [iconFile] and the two hover theme variants on every start, and keeps an existing menu
     * entry current.

     *

     * The StatusNotifier item names the icon (`IconName`), and the AppIndicator host resolves that

     * name through the icon theme: with no file installed it fell back to its generic gear. The two
     * variants are what make the dot visible on a host that prefers `IconName` over `IconPixmap`.

     *

     * The entry itself is only refreshed when it already exists — creating one is what "Integrar ao

     * sistema" is for — but a stale entry is why the dock kept the gear: it named an older AppImage

     * and predated `StartupWMClass`, so the shell could not link the window to it. The launcher path

     * stays the user-facing one ([executableResolver]); the `/tmp/.mount_*` path of the running

     * AppImage would stop existing when the app closes.

     */

    fun ensureTrayIcon(): Boolean {

        val icon = runCatching(iconBytes).getOrNull()?.takeIf { it.isNotEmpty() } ?: return false

        val iconWritten = writeTrayIcons(icon)



        val executable = executableResolver()?.takeIf { it.isNotBlank() }

        if (executable != null && desktopFile.isFile) {

            runCatching {

                // The installed PNG by absolute path: the theme name alone left the taskbar with a

                // placeholder on this Deck while the tray (which gets pixels) already showed the

                // new icon (D83).

                val content = applicationDesktopEntry(executable, wmClass(), iconFile.absolutePath)

                if (desktopFile.readText() != content) {

                    desktopFile.writeText(content)

                    runCatching { menuRefresher() }

                }

            }

        }



        return iconWritten

    }



    /** Writes both files and rebuilds the menu index so the entry is usable right away. */

    fun install(): Boolean {

        val executable = executableResolver()?.takeIf { it.isNotBlank() } ?: return false

        val icon = runCatching(iconBytes).getOrNull()?.takeIf { it.isNotEmpty() } ?: return false



        return runCatching {

            desktopFile.parentFile?.mkdirs()

            if (!writeTrayIcons(icon)) return@runCatching false

            desktopFile.writeText(applicationDesktopEntry(executable, wmClass(), iconFile.absolutePath))

            runCatching { menuRefresher() }

            true

        }.getOrDefault(false)

    }

    /**
     * Writes the base PNG and the two hover variants used by [StatusNotifierItemDbus.IconName].
     *
     * A fake/truncated icon in a unit test still writes the base; only a readable PNG can produce
     * the variants, and that is not a reason to fail the integration.
     */
    private fun writeTrayIcons(icon: ByteArray): Boolean = runCatching {

        writeIfChanged(iconFile, icon)

        val base = runCatching { ImageIO.read(ByteArrayInputStream(icon)) }.getOrNull()

        if (base != null) {

            writeIfChanged(hoverOnIconFile, pngBytes(createTrayStatusImage(base, hoverActive = true)))

            writeIfChanged(hoverOffIconFile, pngBytes(createTrayStatusImage(base, hoverActive = false)))

        }

        true

    }.getOrDefault(false)

    private fun writeIfChanged(file: File, bytes: ByteArray): Boolean {

        if (file.isFile && file.readBytes().contentEquals(bytes)) return true

        file.parentFile?.mkdirs()

        file.writeBytes(bytes)

        return true

    }

    private fun pngBytes(image: BufferedImage): ByteArray {

        val output = ByteArrayOutputStream()

        ImageIO.write(image, "png", output)

        return output.toByteArray()

    }

}

/**
 * Exactly what «Integrar ao sistema» does, in one place so it can be tested without the UI: write the
 * menu entry and the icon, then bring the autostart entry in line with the binary running now.
 *
 * Autostart is what starts the app at login: a stale entry there brings the older build back, and that
 * build then rewrites the menu entry to itself — the update looked ignored until the next reboot. Only
 * a successful install touches autostart, so a failed one leaves no half-integration behind.
 */
internal fun integrateLinuxSystem(
    integration: LinuxSystemIntegration,
    autostart: Autostart,
    startOnBoot: Boolean,
): Boolean {
    val installed = integration.install()
    if (installed) autostart.reconcile(startOnBoot)
    return installed
}
