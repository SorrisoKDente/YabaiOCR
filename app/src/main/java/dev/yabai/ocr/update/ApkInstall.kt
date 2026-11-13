package dev.yabai.ocr.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

/**
 * Downloads an APK and hands it to the system installer.
 *
 * Both steps belong to the system on Android: the download goes through `DownloadManager` (it keeps
 * going if the screen is off, and the user sees it in the notification shade) and installation is
 * the package-installer activity, which asks for confirmation. An app can never install a package
 * silently, so this is as automatic as the platform allows.
 */
object ApkInstall {

    /** Must match the authority declared for the provider in the manifest. */
    private const val AUTHORITY_SUFFIX = ".updates"
    private const val DIRECTORY = "updates"

    /** Enqueues the download and returns its id, which is the key of the completion broadcast. */
    fun downloadApk(context: Context, url: String, version: String): Long {
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

    /** Where [downloadApk] puts the file; the same path the provider exposes. */
    fun downloadedApk(context: Context, version: String): File =
        File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "$DIRECTORY/${fileName(version)}")

    fun fileName(version: String): String = "yabai-ocr-$version.apk"

    /** Android 8+ only installs packages from apps the user has allowed. */
    fun canInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    /** Opens the system installer for a downloaded APK; false when there is nothing to install. */
    fun install(context: Context, apk: File): Boolean {
        if (!apk.exists()) return false
        return try {
            val uri = FileProvider.getUriForFile(context, context.packageName + AUTHORITY_SUFFIX, apk)
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (_: Throwable) {
            false
        }
    }

    /** The «install unknown apps» screen for this app, where the user allows the installer. */
    fun openInstallSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:" + context.packageName),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }
}
