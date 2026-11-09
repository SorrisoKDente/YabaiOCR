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

internal fun autostartDesktopEntry(executable: String): String = buildString {
    appendLine("[Desktop Entry]")
    appendLine("Type=Application")
    appendLine("Name=Yabai OCR")
    appendLine("Comment=Leitor de japonês com OCR")
    appendLine("Exec=${desktopEntryExec(executable, listOf("--minimized"))}")
    appendLine("Icon=yabai-ocr")
    appendLine("Terminal=false")
    appendLine("Categories=Utility;")
    appendLine("X-GNOME-Autostart-enabled=true")
}

internal fun applicationDesktopEntry(executable: String): String = buildString {
    appendLine("[Desktop Entry]")
    appendLine("Type=Application")
    appendLine("Name=Yabai OCR")
    appendLine("Comment=Leitor de japonês com OCR")
    appendLine("Exec=${desktopEntryExec(executable)}")
    appendLine("Icon=yabai-ocr")
    appendLine("Terminal=false")
    appendLine("Categories=Utility;")
    appendLine("StartupNotify=true")
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

    add(env["APPIMAGE"])
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
