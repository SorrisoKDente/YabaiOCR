package dev.yabai.ocr.desktop

import java.io.File

/**
 * KWin's escape hatch for the screenshot authorization check, written as a session environment
 * file.
 *
 * KWin only authorizes `org.kde.KWin.ScreenShot2` for a caller whose `/proc/<pid>/exe` is the `Exec`
 * of a desktop entry living in a root-writable applications directory. Inside an AppImage that path
 * is `/tmp/.mount_yabai-XXXX/opt/yabai-ocr/bin/yabai-ocr` and changes on every launch, so an
 * AppImage can never be authorized: every capture falls back to the portal, which asks for
 * permission each time — the slow hover the user sees.
 *
 * `KWIN_SCREENSHOT_NO_PERMISSION_CHECKS=1` disables that check inside KWin. It is read by the user
 * session at login (`systemd-environment-d-generator` reads `~/.config/environment.d`), so it needs
 * no root and a single relogin; after that every launch keeps it. Turning it off deletes the file,
 * and the check comes back at the next login.
 *
 * The variable is session-wide: it also lets every other process take screenshots without
 * authorization. The Settings card says that before the user opts in.
 */
internal const val KWIN_SCREENSHOT_ENV_KEY = "KWIN_SCREENSHOT_NO_PERMISSION_CHECKS"

/** The environment file `systemd --user` reads at login. */
internal fun kdeCapturePermissionFile(configHome: File = defaultConfigHome()): File =
    File(File(configHome, "environment.d"), "yabai-ocr.conf")

internal fun kdeCapturePermissionContent(): String = buildString {
    appendLine("# Yabai OCR — captura silenciosa no KDE Wayland.")
    appendLine("# O KWin só autoriza org.kde.KWin.ScreenShot2 para um Exec fixo em diretório de")
    appendLine("# root; um AppImage roda de /tmp/.mount_* e nunca casa com a entrada.")
    appendLine("# Esta variável desliga a checagem de autorização do KWin na sessão inteira.")
    appendLine("$KWIN_SCREENSHOT_ENV_KEY=1")
}

internal fun isKdeCapturePermissionEnabled(file: File = kdeCapturePermissionFile()): Boolean =
    runCatching {
        file.isFile && file.readLines().any { it.trim() == "$KWIN_SCREENSHOT_ENV_KEY=1" }
    }.getOrDefault(false)

internal fun setKdeCapturePermission(
    enabled: Boolean,
    file: File = kdeCapturePermissionFile(),
): Boolean = runCatching {
    if (enabled) {
        file.parentFile?.mkdirs()
        val content = kdeCapturePermissionContent()
        if (!file.isFile || file.readText() != content) file.writeText(content)
    } else {
        if (file.isFile) file.delete()
    }
    true
}.getOrDefault(false)

/** Where the card tells the user to look when the variable is not in effect yet. */
internal fun kdeCapturePermissionNeedsRelogin(env: Map<String, String> = System.getenv()): Boolean =
    env[KWIN_SCREENSHOT_ENV_KEY] != "1"
