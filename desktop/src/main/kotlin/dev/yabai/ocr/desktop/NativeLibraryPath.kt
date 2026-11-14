package dev.yabai.ocr.desktop

import java.io.File

/**
 * Where JNativeHook is allowed to unpack its native library.
 *
 * A packaged app keeps the jars (and the `libJNativeHook-2.2.2.x86_64.so` next to them) inside the
 * installation folder. That folder is writable in an `app-image`, but an AppImage mounts its own
 * contents read-only, and JNativeHook's loader fails with
 *
 * ```
 * UnsatisfiedLinkError: .../lib/app/libJNativeHook-2.2.2.x86_64.so (Read-only file system)
 * ```
 *
 * before the first window exists. Pointing `jnativehook.lib.path` at a per-user cache directory
 * makes it unpack into a place it can write. Windows resolves the bare library name through
 * `java.library.path` (a temp folder it can write), so it keeps the historical path.
 */
internal fun jNativeHookLibraryDirectory(home: File = File(System.getProperty("user.home"))): File =
    File(File(home, ".cache"), "yabai-ocr/native")

/**
 * Sets `jnativehook.lib.path` when this session needs it. Returns the directory it selected, or
 * null when the platform's default stays in place.
 *
 * An existing property is never replaced: `JAVA_TOOL_OPTIONS=-Djnativehook.lib.path=...` is the
 * user (or a packager) deciding, and overriding a decision that already worked would be worse than
 * doing nothing.
 */
internal fun configureJNativeHookLibraryPath(
    isLinux: Boolean,
    existing: String?,
    directory: File,
    setProperty: (String, String) -> Unit = { key, value -> System.setProperty(key, value) },
): File? {
    if (!isLinux || !existing.isNullOrBlank()) return null
    directory.mkdirs()
    if (!directory.isDirectory) return null
    setProperty(JNATIVEHOOK_LIB_PATH_PROPERTY, directory.absolutePath)
    return directory
}

internal const val JNATIVEHOOK_LIB_PATH_PROPERTY = "jnativehook.lib.path"

/** The one call `main` makes; it must run before `GlobalScreen` is touched. */
internal fun configureJNativeHookLibraryPathForThisSession(): File? = configureJNativeHookLibraryPath(
    isLinux = DesktopPlatform.isLinux,
    existing = System.getProperty(JNATIVEHOOK_LIB_PATH_PROPERTY),
    directory = jNativeHookLibraryDirectory(),
)
