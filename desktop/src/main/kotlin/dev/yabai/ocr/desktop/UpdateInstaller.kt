package dev.yabai.ocr.desktop

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.TimeUnit

/**
 * Variables the AppImage runtime exports for the mount it created.
 *
 * A child that inherits them starts the new runtime against the *previous* mount, which vanishes as
 * soon as this process exits — the restart that seemed to do nothing. The same cleanup keeps a host
 * helper (the menu cache rebuild) from loading libraries from the AppImage mount.
 */
internal val appImageRuntimeVariables = setOf(
    "APPIMAGE",
    "APPDIR",
    "APPDIR_LIBRARY_PATH",
    "APPIMAGE_EXTRACT_AND_RUN",
    "ARGV0",
    "OWD",
    "LD_LIBRARY_PATH",
)

/** What happened to a downloaded update. */
internal sealed interface UpdateOutcome {
    /** The running AppImage was replaced; the new file — under the release's own name — is ready. */
    data class Replaced(val appImage: Path) : UpdateOutcome

    /** A separate installer was downloaded and started. */
    data class InstallerLaunched(val installer: Path) : UpdateOutcome
}

/** What [UpdateInstaller.install] is about to do, decided before a single byte is written. */
internal sealed interface UpdatePlan {
    /** An AppImage build: the file at [running] is swapped for the download. */
    data class AppImage(val running: Path) : UpdatePlan

    /** Windows and macOS: an installer is downloaded to [path] and started. */
    data class Installer(val path: Path) : UpdatePlan
}

/**
 * Downloads a release and puts it in place, so the update button actually updates.
 *
 * A running AppImage can be replaced on Linux: the file is renamed aside (not overwritten, which
 * fails with ETXTBSY while it runs), the download takes its name and the app restarts. A build that
 * is not an AppImage — `gradle run`, an unpacked distribution — has no single file to swap, so it is
 * left to the browser on purpose.
 */
internal object UpdateInstaller {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /** The AppImage runtime exports the path of the file it is running from. */
    fun appImagePath(environment: Map<String, String>): Path? =
        environment["APPIMAGE"]?.takeIf { it.isNotBlank() }?.let { Path.of(it) }

    /** File name for a download URL, without the query string. */
    fun fileNameFor(url: String): String {
        val withoutQuery = url.substringBefore('?').substringBefore('#')
        return withoutQuery.substringAfterLast('/').takeIf { it.isNotBlank() } ?: "update.bin"
    }

    /**
     * File the downloaded release is written to.
     *
     * The asset already carries the version in its name (`Yabai_OCR-1.1.0-x86_64.AppImage`). Writing
     * it under the *running* file's name is what made an updated app keep the old version in its
     * file name, so the release keeps its own.
     */
    fun installedPath(running: Path, downloadUrl: String): Path =
        running.resolveSibling(fileNameFor(downloadUrl))

    /**
     * Where the download is written. Never over the running file: the new build must not sit on it
     * until the swap, so a same-name asset goes to a side file first.
     */
    fun updateTarget(running: Path, downloadUrl: String): Path {
        val installed = installedPath(running, downloadUrl)
        return if (installed == running) running.resolveSibling(running.fileName.toString() + ".update")
        else installed
    }

    fun relaunchEnvironment(environment: Map<String, String>): Map<String, String> =
        environment.filterKeys { it !in appImageRuntimeVariables }

    fun plan(family: OsFamily, downloadUrl: String, environment: Map<String, String>): UpdatePlan? = when (family) {
        OsFamily.LINUX -> appImagePath(environment)?.let(UpdatePlan::AppImage)
        OsFamily.WINDOWS, OsFamily.MACOS ->
            UpdatePlan.Installer(Path.of(System.getProperty("java.io.tmpdir"), fileNameFor(downloadUrl)))
        else -> null
    }

    /**
     * Downloads [url] to [target], reporting percent (null when the size is unknown).
     *
     * The file is written next to its destination and moved into place only when the download
     * completes, so an interrupted transfer never leaves a half-written update to be run.
     */
    fun download(url: String, target: Path, onProgress: (Int?) -> Unit = {}): Path {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "YabaiOCR-Desktop")
            .build()

        val partial = target.resolveSibling(target.fileName.toString() + ".part")
        Files.deleteIfExists(partial)
        target.parent?.let { Files.createDirectories(it) }

        // Any failure — refused connection, dropped stream, short body — must leave no file behind,
        // otherwise the next attempt would find and possibly run a half-written update.
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                val body = response.body ?: throw IOException("empty body")
                val total = body.contentLength()
                var written = 0L
                body.byteStream().use { input ->
                    Files.newOutputStream(partial).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var lastPercent = -1
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            written += read
                            val percent = if (total > 0) ((written * 100) / total).toInt() else null
                            if (percent != lastPercent) {
                                lastPercent = percent ?: -1
                                onProgress(percent)
                            }
                        }
                    }
                }
                if (total > 0 && written != total) {
                    throw IOException("incomplete download: $written of $total bytes")
                }
            }
        } catch (error: Throwable) {
            Files.deleteIfExists(partial)
            throw error
        }

        Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING)
        return target
    }

    /**
     * Swaps [downloaded] in over [running], keeping the previous file as `<name>.old`.
     *
     * The rename of the running file comes first because writing over a running AppImage is
     * refused by the kernel. If putting the new file in place fails, the old one is renamed back,
     * so a failed update never leaves the user without a working app.
     */
    fun replaceAppImage(running: Path, downloaded: Path, finalPath: Path = running): Path {
        val backup = running.resolveSibling(running.fileName.toString() + ".old")
        Files.deleteIfExists(backup)
        Files.move(running, backup, StandardCopyOption.REPLACE_EXISTING)
        try {
            // A versioned asset already sits at its final name; only a same-name download still has
            // to be moved into place.
            if (downloaded != finalPath) {
                Files.move(downloaded, finalPath, StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (error: IOException) {
            Files.move(backup, running, StandardCopyOption.REPLACE_EXISTING)
            throw error
        }
        finalPath.toFile().setExecutable(true, false)
        return backup
    }

    /**
     * Starts the replacement build; the caller closes the current process afterwards.
     *
     * The environment is cleaned first ([relaunchEnvironment]): the new AppImage has to create its
     * own mount. A runtime that cannot mount (AppImage type 2 needs FUSE 2) exits a moment after
     * `start()` reports success, which is why the launch is checked before it is called a success —
     * and why the unpacked fallback exists.
     */
    fun restart(appImage: Path, environment: Map<String, String> = System.getenv()): Boolean {
        if (!appImage.toFile().canExecute() && !appImage.toFile().setExecutable(true, false)) {
            return false
        }
        val clean = relaunchEnvironment(environment)
        if (launch(listOf(appImage.toString()), clean)) return true

        // «Nothing opened»: no FUSE. Run the AppImage unpacked instead of giving up silently.
        return launch(listOf(appImage.toString(), "--appimage-extract-and-run"), clean)
    }

    /** True when the process is still running 1.5 s later — the difference between «started» and
     * «the runtime printed an error and left». */
    private fun launch(command: List<String>, environment: Map<String, String>): Boolean = try {
        val builder = ProcessBuilder(command)
        builder.environment().apply {
            clear()
            putAll(environment)
        }
        val process = builder.start()
        process.waitFor(1500, TimeUnit.MILLISECONDS)
        process.isAlive
    } catch (_: Throwable) {
        false
    }

    /**
     * Downloads and installs, in one call: the UI only needs to report progress and the outcome.
     *
     * Returns null when there is nothing safe to install here — no asset, or an unpacked build with
     * no single file to swap — so the caller falls back to the browser.
     */
    fun install(
        url: String?,
        family: OsFamily,
        environment: Map<String, String>,
        onProgress: (Int?) -> Unit = {},
    ): UpdateOutcome? {
        val downloadUrl = url?.takeIf { it.isNotBlank() } ?: return null
        return when (val plan = plan(family, downloadUrl, environment)) {
            is UpdatePlan.AppImage -> {
                val installed = installedPath(plan.running, downloadUrl)
                val target = updateTarget(plan.running, downloadUrl)
                download(downloadUrl, target, onProgress)
                replaceAppImage(plan.running, target, installed)
                UpdateOutcome.Replaced(installed)
            }

            is UpdatePlan.Installer -> {
                download(downloadUrl, plan.path, onProgress)
                if (family == OsFamily.MACOS) {
                    ProcessBuilder("open", plan.path.toString()).start()
                } else {
                    ProcessBuilder(plan.path.toString()).start()
                }
                UpdateOutcome.InstallerLaunched(plan.path)
            }

            null -> null
        }
    }
}
