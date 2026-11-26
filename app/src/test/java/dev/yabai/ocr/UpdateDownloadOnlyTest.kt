package dev.yabai.ocr

import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import dev.yabai.ocr.update.ApkInstall
import java.lang.reflect.Modifier
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * «Baixar» means download, not install.
 *
 * Handing the APK to the package installer drags the «install unknown apps» permission along, and a
 * download that ended in nothing left the screen on «Baixando atualização…» forever. The button now
 * only downloads, the app no longer declares the install permission, and the completion broadcast
 * is accepted from the system's download manager.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w320dp-h640dp-mdpi")
class UpdateDownloadOnlyTest {

    @Test
    fun `the app does not declare the install permission`() {
        val context = RuntimeEnvironment.getApplication()
        val info = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS,
        )
        val permissions = info.requestedPermissions.orEmpty().toList()

        assertFalse(
            "REQUEST_INSTALL_PACKAGES is back: installing needs a permission the app should not ask for",
            permissions.contains(android.Manifest.permission.REQUEST_INSTALL_PACKAGES),
        )
    }

    @Test
    fun `the update downloader offers no install entry point`() {
        val installLike = ApkInstall::class.java.declaredMethods.filter { method ->
            Modifier.isPublic(method.modifiers) &&
                method.name.substringBefore("$").lowercase().let { name ->
                    name == "install" || name.contains("caninstall") || name.contains("installsettings")
                }
        }

        assertEquals(
            "the download helper grew an installer again: $installLike",
            emptyList<Any>(),
            installLike,
        )
    }

    @Test
    fun `the completion receiver accepts the system download broadcast`() {
        // DownloadManager is another process. A NOT_EXPORTED receiver never sees its broadcast, which
        // is what left the status stuck on «Baixando atualização…».
        assertEquals(ContextCompat.RECEIVER_EXPORTED, updateReceiverFlags())
    }

    @Test
    fun `the download button promises a download, not an install`() {
        val label = RuntimeEnvironment.getApplication().getString(R.string.update_download_button)
        val lower = label.lowercase()

        assertFalse("the button still promises an installation: $label", lower.contains("instalar"))
        assertFalse("the button still promises an installation: $label", lower.contains("install"))
    }

    @Test
    fun `the downloaded file is the one the status names`() {
        assertEquals("yabai-ocr-1.0.7.apk", ApkInstall.fileName("1.0.7"))
    }

    @Test
    fun `the progress bar has a percentage only when the total size is known`() {
        assertEquals(0, ApkInstall.ApkDownloadProgress(0, 1_000).percent ?: -1)
        assertEquals(42, ApkInstall.ApkDownloadProgress(420, 1_000).percent ?: -1)
        // A row that overshoots must not draw past the end of the bar.
        assertEquals(100, ApkInstall.ApkDownloadProgress(1_200, 1_000).percent ?: -1)
        // DownloadManager reports -1 until the server sends a Content-Length.
        assertNull(ApkInstall.ApkDownloadProgress(420, -1).percent)
        assertNull(ApkInstall.ApkDownloadProgress(420, 0).percent)
    }

    /**
     * R62: DownloadManager can answer null for a moment right after the enqueue. The bar stopped on
     * that first null and stayed at «Baixando…» without a percentage for the whole transfer.
     */
    @Test
    fun `a missing progress row is retried before the bar gives up`() = runBlocking {
        var samples = 0
        val waits = mutableListOf<Long>()
        val progress = ApkInstall.awaitProgress(
            maxMisses = 2,
            retryDelayMs = 400L,
            wait = { waits += it },
        ) {
            samples++
            if (samples < 3) null else ApkInstall.ApkDownloadProgress(500, 1_000)
        }

        assertEquals("the missing row was not retried", 3, samples)
        assertEquals("the retry must wait between samples", listOf(400L, 400L), waits)
        assertEquals("the first visible sample must reach the bar", 50, progress?.percent ?: -1)
    }

    /** The budget has to end: a download that already finished also answers null. */
    @Test
    fun `a row that never appears gives up after the retry budget`() = runBlocking {
        var samples = 0
        val progress = ApkInstall.awaitProgress(maxMisses = 3, wait = {}) {
            samples++
            null
        }

        assertNull("a row that never appears must stop the poll", progress)
        assertEquals("one try plus three retries", 4, samples)
    }
}
