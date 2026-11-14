package dev.yabai.ocr

import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import dev.yabai.ocr.update.ApkInstall
import java.lang.reflect.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
