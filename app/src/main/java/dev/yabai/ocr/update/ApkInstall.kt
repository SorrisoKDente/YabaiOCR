package dev.yabai.ocr.update

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import java.io.File
import kotlinx.coroutines.delay

/**
 * Downloads an update APK and leaves it in the phone's Downloads folder.
 *
 * The app deliberately stops there: installing is the system package installer's job, and handing it
 * the file means the user has to grant «install unknown apps» first. Downloading needs no such
 * permission, so «Baixar» is all this offers — the user opens the APK when they choose to.
 *
 * The download goes through `DownloadManager` (it keeps going if the screen is off, and the user sees
 * it in the notification shade).
 */
object ApkInstall {

    private const val DIRECTORY = "updates"

    /** The byte counters of a download, as DownloadManager reports them. */
    internal data class ApkDownloadProgress(val downloadedBytes: Long, val totalBytes: Long) {
        /** 0–100, or null while the server has not reported the total size. */
        val percent: Int?
            get() = if (totalBytes > 0) {
                ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
            } else {
                null
            }
    }

    /** Enqueues the download and returns its id, which is the key of the completion broadcast. */
    fun downloadApk(context: Context, url: String, version: String): Long {
        // DownloadManager refuses to overwrite an existing destination, and a second «Baixar» after a
        // failed attempt would otherwise end in ERROR_FILE_ALREADY_EXISTS with nothing to show.
        downloadedApk(context, version).delete()

        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("Yabai OCR $version")
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(
                context,
                Environment.DIRECTORY_DOWNLOADS,
                "$DIRECTORY/${fileName(version)}",
            )

        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return manager.enqueue(request)
    }

    /** Where [downloadApk] puts the file. */
    fun downloadedApk(context: Context, version: String): File =
        File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "$DIRECTORY/${fileName(version)}")

    fun fileName(version: String): String = "yabai-ocr-$version.apk"

    /**
     * Bytes downloaded so far, or null when there is nothing left to watch.
     *
     * DownloadManager has no progress callback: the screen polls this while the transfer runs and
     * stops on the completion broadcast. A finished, failed or missing download returns null, so the
     * bar cannot outlive the file.
     */
    internal fun progress(context: Context, downloadId: Long): ApkDownloadProgress? {
        if (downloadId < 0) return null
        return try {
            val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            manager.query(DownloadManager.Query().setFilterById(downloadId))?.use { cursor ->
                if (!cursor.moveToFirst()) return null
                val status = cursor.getInt(
                    cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS),
                )
                if (status == DownloadManager.STATUS_SUCCESSFUL || status == DownloadManager.STATUS_FAILED) {
                    return null
                }
                ApkDownloadProgress(
                    downloadedBytes = cursor.getLong(
                        cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR),
                    ),
                    totalBytes = cursor.getLong(
                        cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES),
                    ),
                )
            }
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Reads the first visible progress row, retrying a missing one.
     *
     * DownloadManager can answer null for a moment right after the enqueue, and also after the
     * transfer ended. Giving up on the first null left the bar at «Baixando…» without a percentage
     * for the whole download; [maxMisses] bounds the wait, so a finished download does not keep the
     * coroutine alive.
     */
    internal suspend fun awaitProgress(
        maxMisses: Int = 10,
        retryDelayMs: Long = 400L,
        sample: suspend () -> ApkDownloadProgress?,
    ): ApkDownloadProgress? {
        var misses = 0
        while (true) {
            sample()?.let { return it }
            if (++misses > maxMisses) return null
            delay(retryDelayMs)
        }
    }

    /** True when [downloadId] ended successfully, so the status can say «downloaded» instead of «failed». */
    fun isDownloaded(context: Context, downloadId: Long): Boolean {
        if (downloadId < 0) return false
        return try {
            val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            manager.query(DownloadManager.Query().setFilterById(downloadId))?.use { cursor ->
                cursor.moveToFirst() &&
                    cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) ==
                    DownloadManager.STATUS_SUCCESSFUL
            } ?: false
        } catch (_: Throwable) {
            false
        }
    }
}
