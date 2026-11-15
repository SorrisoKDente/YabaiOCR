package dev.yabai.ocr.desktop

import java.io.File
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/**
 * One Yabai OCR per user.
 *
 * The app owns the well-known D-Bus name `dev.yabai.Ocr` (hover pointer + fallback shortcuts) and a
 * tray item. Closing the settings window only hides it — the process stays in the tray — so starting
 * the AppImage again while it is "closed" used to create a second process that could not register
 * the name. The second instance then showed «não foi possível publicar o serviço D-Bus» and silently
 * lost hover and every global shortcut, while the first one kept working unseen.
 *
 * A file lock is the guard: the operating system releases it when the process dies, so a crash never
 * leaves the app permanently "already running". The channel stays open for the process lifetime.
 */
internal class InstanceLock(private val path: Path) {

    private var channel: FileChannel? = null
    private var lock: FileLock? = null

    /**
     * True when this process may run.
     *
     * If the lock cannot be created at all (read-only home, odd filesystem), the app is allowed to
     * start: refusing to open because of a lock-file problem would be worse than the duplicate this
     * guards against.
     */
    fun acquire(): Boolean {
        if (lock?.isValid == true) return true
        val opened = try {
            path.parent?.let { Files.createDirectories(it) }
            FileChannel.open(
                path,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
            )
        } catch (_: IOException) {
            return true
        }

        val acquired = try {
            opened.tryLock()
        } catch (_: OverlappingFileLockException) {
            // Another holder inside this same JVM (tests, or a second call in-process).
            null
        } catch (_: IOException) {
            null
        }

        if (acquired == null) {
            runCatching { opened.close() }
            return false
        }

        channel = opened
        lock = acquired
        return true
    }

    fun release() {
        runCatching { lock?.release() }
        runCatching { channel?.close() }
        lock = null
        channel = null
    }

    companion object {
        fun default(): InstanceLock =
            InstanceLock(File(defaultDesktopHome(), "instance.lock").toPath())
    }
}
