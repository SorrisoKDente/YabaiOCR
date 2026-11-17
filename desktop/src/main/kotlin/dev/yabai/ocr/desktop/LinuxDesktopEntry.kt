package dev.yabai.ocr.desktop

import java.io.File

/**
 * Builds the `Exec` field of a Desktop Entry.
 *
 * The field is not a shell: the Desktop Entry specification says arguments are double-quoted and
 * that `"`, backtick, `$` and `\` must be escaped inside the quotes. Always quoting also handles
 * paths with spaces, which is the common case for an AppImage under `~/Applications`.
 */
internal fun desktopEntryArgument(value: String): String {
    val escaped = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("`", "\\`")
        .replace("$", "\\$")
    return "\"$escaped\""
}

internal fun desktopEntryExec(executable: String, args: List<String> = emptyList()): String =
    (listOf(executable) + args).joinToString(" ") { desktopEntryArgument(it) }

/**
 * `WM_CLASS` AWT gives the window: the toolkit derives it from the main class, with `.` replaced by
 * `-` (checked with `xprop` on a running app: `dev-yabai-ocr-desktop-MainKt`).
 *
 * GNOME links a running window to its menu entry by this value, so the entry has to declare it —
 * without `StartupWMClass` the shell cannot tell which entry the window belongs to and draws its
 * generic gear instead of the app icon. Deriving it from the running command keeps the entry right
 * if the entry point is ever renamed, instead of carrying a copy of the name here.
 */
internal fun wmClassForMainClass(command: String?): String? {
    val main = command?.trim()?.substringBefore(' ')?.takeIf { it.isNotBlank() } ?: return null
    // `java -jar something.jar` says nothing about a class name, and neither does a blank command.
    if (main.endsWith(".jar", ignoreCase = true)) return null
    return main.replace('.', '-')
}

/** [wmClassForMainClass] for the process that is running now. */
internal fun runningWmClass(): String? = wmClassForMainClass(System.getProperty("sun.java.command"))

internal fun autostartDesktopEntry(executable: String, wmClass: String?): String = buildString {
    appendLine("[Desktop Entry]")
    appendLine("Type=Application")
    appendLine("Name=Yabai OCR")
    appendLine("Comment=Leitor de japonês com OCR")
    appendLine("Exec=${desktopEntryExec(executable, listOf("--minimized"))}")
    appendLine("Icon=yabai-ocr")
    appendLine("Terminal=false")
    appendLine("Categories=Utility;")
    if (wmClass != null) appendLine("StartupWMClass=$wmClass")
    appendLine("X-GNOME-Autostart-enabled=true")
}

/** Names the installed PNG in that folder, so a theme lookup has something to find. */
internal const val DEFAULT_ICON_NAME = "yabai-ocr"

/**
 * KWin only lets a caller use `org.kde.KWin.ScreenShot2` when the desktop entry whose `Exec` matches
 * the caller's binary declares this interface. Without it every capture is refused with
 * `Error.NoAuthorized`, the app falls back to the Screenshot portal and Plasma presents a permission
 * window per hover.
 */
internal const val KWIN_SCREENSHOT_INTERFACE = "org.kde.KWin.ScreenShot2"

/**
 * The menu entry that carries the window's icon and the KWin capture authorization.
 *
 * [iconPath] is the installed PNG. It goes in as an **absolute path** rather than the theme name:
 * the theme name is resolved through the desktop's icon cache, and a cache that predates the file —
 * or a cache that never indexed `~/.local/share/icons`, which is what the Steam Deck showed — leaves
 * the taskbar with a placeholder while the tray, which receives the pixels themselves, already
 * shows the new icon. Without [iconPath] the theme name stays, which is what a plain `Icon=` entry
 * looks like on a desktop that resolves it fine.
 */
internal fun applicationDesktopEntry(
    executable: String,
    wmClass: String?,
    iconPath: String? = null,
): String = buildString {
    appendLine("[Desktop Entry]")
    appendLine("Type=Application")
    appendLine("Name=Yabai OCR")
    appendLine("Comment=Leitor de japonês com OCR")
    appendLine("Exec=${desktopEntryExec(executable)}")
    appendLine("Icon=${iconPath ?: DEFAULT_ICON_NAME}")
    appendLine("Terminal=false")
    appendLine("Categories=Utility;")
    appendLine("StartupNotify=true")
    if (wmClass != null) appendLine("StartupWMClass=$wmClass")
    appendLine("X-KDE-DBUS-Restricted-Interfaces=$KWIN_SCREENSHOT_INTERFACE")
}

/**
 * Hidden entry that exists only to authorize silent KWin captures.
 *
 * KWin compares the entry's `Exec` with `/proc/<pid>/exe`, so the executable here must be the binary
 * actually running — for an AppImage that is the extracted runtime under `/tmp/.mount_*`, not the
 * `.AppImage` file the user launches. That is why this entry is separate from the menu entry: the
 * menu entry must stay usable, while this one is rewritten on every start.
 */
internal fun screenshotAuthorizationDesktopEntry(executable: String): String = buildString {
    appendLine("[Desktop Entry]")
    appendLine("Type=Application")
    appendLine("Name=Yabai OCR (captura silenciosa)")
    appendLine("Exec=${desktopEntryExec(executable)}")
    appendLine("NoDisplay=true")
    appendLine("Terminal=false")
    appendLine("X-KDE-DBUS-Restricted-Interfaces=$KWIN_SCREENSHOT_INTERFACE")
}

/**
 * Base directory for settings and dictionaries. `YABAI_HOME` exists for automated E2E runs so the
 * real `~/.yabai_ocr` is never touched; without it the historical path is kept.
 */
internal fun defaultDesktopHome(
    env: Map<String, String> = System.getenv(),
    userHome: String = System.getProperty("user.home"),
): File {
    val override = env["YABAI_HOME"]?.trim()
    return if (!override.isNullOrBlank()) File(override) else File(userHome, ".yabai_ocr")
}

internal fun defaultConfigHome(
    env: Map<String, String> = System.getenv(),
    userHome: String = System.getProperty("user.home"),
): File {
    val override = env["XDG_CONFIG_HOME"]?.trim()
    return if (!override.isNullOrBlank()) File(override) else File(userHome, ".config")
}

internal fun defaultDataHome(
    env: Map<String, String> = System.getenv(),
    userHome: String = System.getProperty("user.home"),
): File {
    val override = env["XDG_DATA_HOME"]?.trim()
    return if (!override.isNullOrBlank()) File(override) else File(userHome, ".local/share")
}

/**
 * Ordered executable candidates for a Linux launcher. Pure so the precedence can be tested without
 * a real AppImage: the caller passes the filesystem-dependent extras (AppImages under
 * `~/Applications`) and tests the order.
 */
internal fun linuxExecutableCandidates(
    env: Map<String, String>,
    userHome: String,
    command: String?,
    jpackageAppPath: String?,
    extraCandidates: List<String> = emptyList(),
): List<String> {
    val candidates = mutableListOf<String>()

    fun add(path: String?) {
        val trimmed = path?.trim()
        if (!trimmed.isNullOrEmpty() && !candidates.contains(trimmed)) candidates.add(trimmed)
    }

    // `$APPIMAGE` is inherited by every process the app starts — and by the app itself when it is
    // started from inside another AppImage (Freebuff, a terminal opened from one). Trusting it wrote
    // a menu entry whose `Exec` was another app's AppImage, so the launcher opened the wrong program
    // and the taskbar had no entry to take the icon from (D83).
    if (appImageIsOurs(env, command)) add(env["APPIMAGE"])
    add(jpackageAppPath)

    val commandName = command?.let { File(it).name.lowercase() }
    if (commandName != "java" && commandName != "javaw") {
        add(command)
    }

    add("$userHome/.local/bin/yabai-ocr")
    add("/usr/bin/yabai-ocr")
    add("/opt/yabai-ocr/bin/Yabai OCR")

    extraCandidates.forEach(::add)
    return candidates
}

/**
 * Whether `$APPIMAGE` belongs to the process asking, or was inherited from whoever launched it.
 *
 * The AppImage runtime exports `APPIMAGE` (the file) and `APPDIR` (where it mounted itself). When our
 * binary really came from that mount, [command] lives under [APPDIR] and the pair agrees. When the
 * shell already had `APPIMAGE` set from another AppImage, the paths cannot agree and the value is
 * another program's.
 *
 * Without `APPDIR` there is nothing to compare — older runtimes exported only `APPIMAGE`, and that
 * was never wrong for a run that owned it — so the value is trusted as before.
 */
internal fun appImageIsOurs(env: Map<String, String>, command: String?): Boolean {
    val appImage = env["APPIMAGE"]?.trim().orEmpty()
    if (appImage.isEmpty()) return false
    val appDir = env["APPDIR"]?.trim()?.trimEnd('/').orEmpty()
    if (appDir.isEmpty()) return true
    val running = command?.trim().orEmpty()
    if (running.isEmpty()) return false
    return running.startsWith("$appDir/")
}

/**
 * Finds AppImages the user dropped in the conventional Steam Deck folder.
 */
internal fun appImageCandidates(userHome: String): List<String> {
    val folder = File(userHome, "Applications")
    return folder.listFiles()
        ?.filter { it.isFile && it.name.endsWith(".AppImage", ignoreCase = true) }
        ?.sortedBy { it.name }
        ?.map { it.absolutePath }
        .orEmpty()
}

/**
 * The binary the kernel says is running: `/proc/self/exe` is exactly what KWin's authorization check
 * reads from `/proc/<pid>/exe`. `ProcessHandle.info().command()` is only a fallback because it
 * reports `cmdline[0]`, which an AppImage keeps as the launcher path.
 */
internal fun runningExecutablePath(): String? = runCatching {
    File("/proc/self/exe").canonicalPath
}.getOrNull()?.takeIf { it.isNotBlank() }

internal fun resolveLinuxExecutable(
    env: Map<String, String> = System.getenv(),
    userHome: String = System.getProperty("user.home"),
    command: String? = ProcessHandle.current().info().command().orElse(null),
    jpackageAppPath: String? = System.getProperty("jpackage.app-path"),
): String? = linuxExecutableCandidates(
    env = env,
    userHome = userHome,
    command = command,
    jpackageAppPath = jpackageAppPath,
    extraCandidates = appImageCandidates(userHome),
).firstOrNull { path -> File(path).let { it.isFile && it.canExecute() } }
