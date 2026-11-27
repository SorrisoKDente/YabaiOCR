package dev.yabai.ocr

import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import com.google.android.material.tabs.TabLayout
import dev.yabai.ocr.update.AppUpdate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

/**
 * «Baixar» in the update notice opens «Sobre» and starts the download there.
 *
 * The notice used to be an `AlertDialog` whose positive button called the download and closed, so
 * the progress bar — which lives in the «Sobre» tab — was never on screen while the file arrived.
 * Both halves of the fix are wired in one listener, and either one can be dropped without anything
 * else noticing: without the tab switch the reader lands on whatever page was open, and without the
 * download the new page shows nothing happening. This test watches both.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w320dp-h640dp-mdpi")
class UpdateDialogDownloadTest {

    private val update = AppUpdate(
        hasUpdate = true,
        latestVersion = "1.2.0",
        currentVersion = "1.2.0",
        releaseUrl = "https://github.com/SorrisoKDente/YabaiOCR/releases/tag/v1.2.0",
        releaseNotes = "Notas desta versão.",
        apkUrl = "https://github.com/SorrisoKDente/YabaiOCR/releases/download/v1.2.0/yabai-ocr-1.2.0.apk",
    )

    @Test
    fun `the download button opens About and starts the download`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

        val requests = mutableListOf<Pair<String, String>>()
        activity.apkDownloader = { _, url, version ->
            requests += url to version
            42L
        }
        setPendingUpdate(activity)
        openNotice(activity)

        val tabs = activity.findViewById<TabLayout>(R.id.settingsTabs)
        assertEquals("the test starts on the General tab", 0, tabs.selectedTabPosition)

        val dialog = ShadowDialog.getLatestDialog()
        assertTrue("the update notice was not shown", dialog.isShowing)

        dialog.findViewById<Button>(R.id.dialogUpdateDownload).performClick()

        assertFalse("the notice stayed over the page that shows the download", dialog.isShowing)
        assertEquals(
            "«Baixar» did not take the reader to «Sobre», where the progress bar is",
            3,
            tabs.selectedTabPosition,
        )
        assertEquals(
            "the About page is still hidden after selecting its tab",
            View.VISIBLE,
            activity.findViewById<View>(R.id.pageAbout).visibility,
        )

        assertEquals(
            "the release's APK was not handed to the downloader",
            listOf(update.apkUrl!! to "1.2.0"),
            requests,
        )
        assertEquals(
            "About shows no progress bar to watch",
            View.VISIBLE,
            activity.findViewById<ProgressBar>(R.id.appUpdateProgress).visibility,
        )
        val status = activity.findViewById<TextView>(R.id.appUpdateStatus)
        assertEquals("About says nothing about the download", View.VISIBLE, status.visibility)
        assertEquals(
            activity.getString(R.string.update_downloading),
            status.text.toString(),
        )
        assertFalse(
            "the download button is still enabled while the file is on its way",
            activity.findViewById<Button>(R.id.btnDownloadUpdate).isEnabled,
        )
    }

    /** The notify-the-user flow starts with the action that has to work, not with the checker. */
    private fun setPendingUpdate(activity: MainActivity) {
        MainActivity::class.java
            .getDeclaredField("pendingUpdate")
            .apply { isAccessible = true }
            .set(activity, update)
    }

    private fun openNotice(activity: MainActivity) {
        MainActivity::class.java
            .getDeclaredMethod("showUpdateFoundDialog", AppUpdate::class.java)
            .apply { isAccessible = true }
            .invoke(activity, update)
    }
}
