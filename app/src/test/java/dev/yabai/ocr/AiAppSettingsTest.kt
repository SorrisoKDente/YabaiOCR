package dev.yabai.ocr

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import android.widget.Button
import android.widget.ListView
import androidx.appcompat.app.AlertDialog
import dev.yabai.ocr.ai.AskAi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

/**
 * What the settings screen offers when the user fixes the app that answers «Perguntar à IA» (R18).
 *
 * The panel has offered only AI and translation apps since R11, with a footer to the full list; the
 * settings offered the raw package-manager answer, so «Gmail», «Bluetooth» and «Converter para JPEG»
 * came before ChatGPT. This is the regression: the same phone, the same list, one criterion.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "b+pt+BR")
class AiAppSettingsTest {

    private fun resolve(label: String, packageName: String) = ResolveInfo().apply {
        activityInfo = ActivityInfo().apply {
            this.packageName = packageName
            name = "$packageName.Main"
            nonLocalizedLabel = label
        }
    }

    /** A phone with one AI app and one app that only shares text. */
    @Suppress("DEPRECATION")
    private fun phone(): MainActivity {
        val manager = shadowOf(RuntimeEnvironment.getApplication().packageManager)
        val share = Intent(Intent.ACTION_SEND).setType(AskAi.MIME_TEXT)
        manager.addResolveInfoForIntent(share, resolve("ChatGPT", "com.openai.chatgpt"))
        manager.addResolveInfoForIntent(share, resolve("Gmail", "com.google.android.gm"))
        return Robolectric.buildActivity(MainActivity::class.java).setup().get()
    }

    private fun open(activity: MainActivity): AlertDialog {
        assertEquals("the phone's apps were not found", 2, AskAi.targets(activity).size)
        activity.findViewById<Button>(R.id.btnAiApp).performClick()
        val dialog = ShadowDialog.getLatestDialog() as? AlertDialog
        assertNotNull("the settings did not open the app list", dialog)
        return dialog!!
    }

    private fun items(dialog: AlertDialog): List<String> {
        val list = dialog.listView
        return (0 until list.count).map { list.adapter.getItem(it).toString() }
    }

    /** Taps a row of the list, the way a finger does — the dialog's own click handler runs. */
    private fun click(dialog: AlertDialog, index: Int) {
        val list: ListView = dialog.listView
        list.performItemClick(null, index, list.adapter.getItemId(index))
    }

    @Test
    fun `the settings offer the same short list as the panel`() {
        assertEquals(
            listOf("Automático (só apps de IA)", "ChatGPT", "Mostrar todos (2)"),
            items(open(phone())),
        )
    }

    @Test
    fun `the footer opens every app and can go back to the short list`() {
        val activity = phone()
        val short = open(activity)

        click(short, 2)

        val all = ShadowDialog.getLatestDialog() as AlertDialog
        assertEquals(
            listOf("Automático (só apps de IA)", "ChatGPT", "Gmail", "Só os de IA"),
            items(all),
        )

        // And the footer goes back to where it came from.
        click(all, 3)

        assertEquals(
            listOf("Automático (só apps de IA)", "ChatGPT", "Mostrar todos (2)"),
            items(ShadowDialog.getLatestDialog() as AlertDialog),
        )
    }

    @Test
    fun `picking an app stores its package, not its name`() {
        val activity = phone()
        val short = open(activity)

        click(short, 2)
        click(ShadowDialog.getLatestDialog() as AlertDialog, 2)

        assertEquals("com.google.android.gm", AppSettings(activity).aiPackage)
    }
}
