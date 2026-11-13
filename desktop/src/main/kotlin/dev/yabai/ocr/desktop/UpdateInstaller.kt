package dev.yabai.ocr.desktop

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.TimeUnit

/** What happened to a downloaded update. */
internal sealed interface UpdateOutcome {
    /** The running AppImage was replaced; the new file is ready to run. */
    data class Replaced(val appImage: Path) : UpdateOutcome

    /** A separate installer was downloaded and started. */
    data class InstallerLaunched(val installer: Path) : UpdateOutcome

    /** Nothing could be done safely here; the caller should open the release page. */
    data object Manual : UpdateOutcome
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

    fun plan(family: OsFamily, downloadUrl: String?, environment: Map<String, String>): UpdateOutcome? = when {
        downloadUrl.isNullOrBlank() -> null
        family == OsFamily.LINUX -> appImagePath(environment)?.let { UpdateOutcome.Replaced(it) }
        family == OsFamily.WINDOWS || family == OsFamily.MACOS -> {
            val name = fileNameFor(downloadUrl)
            UpdateOutcome.InstallerLaunched(Path.of(System.getProperty("java.io.tmpdir"), name))
        }
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
    fun replaceAppImage(running: Path, downloaded: Path): Path {
        val backup = running.resolveSibling(running.fileName.toString() + ".old")
        Files.deleteIfExists(backup)
        Files.move(running, backup, StandardCopyOption.REPLACE_EXISTING)
        try {
            Files.move(downloaded, running, StandardCopyOption.REPLACE_EXISTING)
        } catch (error: IOException) {
            Files.move(backup, running, StandardCopyOption.REPLACE_EXISTING)
            throw error
        }
        running.toFile().setExecutable(true, false)
        return backup
    }

    /** Starts the replacement build; the caller closes the current process afterwards. */
    fun restart(appImage: Path): Boolean = try {
        ProcessBuilder(appImage.toString()).start()
        true
    } catch (_: Throwable) {
        false
    }

    /**
     * Downloads and installs, in one call: the UI only needs to report progress and the outcome.
     *
     * Returns null when there is no asset to download, so the caller falls back to the browser.
     */
    fun install(
        url: String?,
        family: OsFamily,
        environment: Map<String, String>,
        onProgress: (Int?) -> Unit = {},
    ): UpdateOutcome? {
        val plan = plan(family, url, environment) ?: return null
        val downloadUrl = url!!
        return when (plan) {
            is UpdateOutcome.Replaced -> {
                val target = plan.appImage.resolveSibling(plan.appImage.fileName.toString() + ".update")
                download(downloadUrl, target, onProgress)
                replaceAppImage(plan.appImage, target)
                UpdateOutcome.Replaced(plan.appImage)
            }

            is UpdateOutcome.InstallerLaunched -> {
                download(downloadUrl, plan.installer, onProgress)
                if (family == OsFamily.MACOS) {
                    ProcessBuilder("open", plan.installer.toString()).start()
                } else {
                    ProcessBuilder(plan.installer.toString()).start()
                }
                plan
            }

            UpdateOutcome.Manual -> UpdateOutcome.Manual
        }
    }
}
