package dev.yabai.ocr

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Insets
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.EditText
import dev.yabai.ocr.service.LookupOverlayService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings

/**
 * The dictionary opened by another app (R35e).
 *
 * A reader that hands a word over — ReadEra's «Aplicativo de dicionário», the share sheet, the
 * «Processar texto» menu — cannot use the floating bubble: the bubble needs the capture permission
 * and a running service. [LookupActivity] is the door for that, and what it has to get right is small
 * and testable: take the word from the intent it was opened with, put it in the search box, and be
 * declared for the intents those apps send. The lookup itself is the panel's, already covered by the
 * dictionary tests.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w320dp-h640dp-mdpi")
class LookupIntentTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun `ACTION_PROCESS_TEXT opens the search with the word`() {
        val activity = open(
            Intent(context, LookupActivity::class.java)
                .setAction(Intent.ACTION_PROCESS_TEXT)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_PROCESS_TEXT, "波及" as CharSequence),
        )

        assertEquals("the word did not reach the search box", "波及", field(activity).text.toString())
    }

    @Test
    fun `ACTION_SEND opens the search with the shared word`() {
        val activity = open(
            Intent(context, LookupActivity::class.java)
                .setAction(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, "波及" as CharSequence),
        )

        assertEquals("the shared word did not reach the search box", "波及", field(activity).text.toString())
    }

    @Test
    fun `the word is trimmed and a foreign action carries nothing`() {
        val processText = Intent(context, LookupActivity::class.java)
            .setAction(Intent.ACTION_PROCESS_TEXT)
            .putExtra(Intent.EXTRA_PROCESS_TEXT, "  波及\n" as CharSequence)

        assertEquals("波及", LookupActivity.incomingText(processText))
        assertEquals(
            "",
            LookupActivity.incomingText(
                Intent(Intent.ACTION_VIEW).putExtra(Intent.EXTRA_TEXT, "波及"),
            ),
        )
        assertEquals("", LookupActivity.incomingText(null))
    }

    /**
     * The point of the round: ReadEra builds its list by asking which apps accept text. Both doors
     * have to reach this screen, or the app is simply not on the list.
     */
    @Test
    fun `both text doors reach the lookup screen`() {
        fun names(action: String): List<String> {
            val intent = Intent(action).setType("text/plain").addCategory(Intent.CATEGORY_DEFAULT)
            return context.packageManager.queryIntentActivities(intent, 0)
                .map { it.activityInfo.name }
        }

        assertTrue(
            "PROCESS_TEXT does not reach the lookup screen: ${names(Intent.ACTION_PROCESS_TEXT)}",
            names(Intent.ACTION_PROCESS_TEXT).any { it.endsWith("LookupActivity") },
        )
        assertTrue(
            "SEND does not reach the lookup screen: ${names(Intent.ACTION_SEND)}",
            names(Intent.ACTION_SEND).any { it.endsWith("LookupActivity") },
        )
    }

    @Test
    fun `an intent without text still opens a usable search box`() {
        val activity = open(Intent(context, LookupActivity::class.java))

        assertEquals("the box should start empty", "", field(activity).text.toString())
        assertNotNull("the search field is not on screen", field(activity).parent)
    }

    /**
     * The screen draws edge to edge, as Android 16 asks. The bars have to become padding, or the
     * header of the search sits under the clock and the last word under the gesture bar.
     */
    @Test
    fun `the screen keeps its content off the system bars`() {
        val activity = open(Intent(context, LookupActivity::class.java))
        val host = activity.window.decorView.findViewWithTag<ViewGroup>(LookupActivity.HOST_TAG)
        assertNotNull("the lookup screen has no host view", host)

        host!!.dispatchApplyWindowInsets(
            WindowInsets.Builder()
                .setInsets(WindowInsets.Type.statusBars(), Insets.of(0, 63, 0, 0))
                .setInsets(WindowInsets.Type.navigationBars(), Insets.of(0, 0, 0, 48))
                .build(),
        )

        assertEquals("the status bar was not padded away", 63, host.paddingTop)
        assertEquals("the navigation bar was not padded away", 48, host.paddingBottom)
    }

    @Test
    fun `with the overlay permission the word goes to the floating panel`() {
        ShadowSettings.setCanDrawOverlays(true)
        val intent = Intent(context, LookupActivity::class.java)
            .setAction(Intent.ACTION_PROCESS_TEXT)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_PROCESS_TEXT, "波及" as CharSequence)

        val activity = Robolectric.buildActivity(LookupActivity::class.java, intent).setup().get()

        assertTrue("the doorway stayed on screen", activity.isFinishing)
        val started = shadowOf(RuntimeEnvironment.getApplication()).peekNextStartedService()
        assertNotNull("no service was started", started)
        assertEquals(
            "the word did not go to the panel",
            "波及",
            started!!.getStringExtra(LookupOverlayService.EXTRA_TEXT),
        )
    }

    /**
     * The doorway must not share a task with the app's main screen (R38).
     *
     * ReadEra opens the intent with `FLAG_ACTIVITY_NEW_TASK`; with the package's affinity Android
     * put the doorway in the task the app already had — the one holding `MainActivity` — and brought
     * that whole task forward. When the doorway finished, the screen behind the panel was the Yabai
     * screen, not the reader. Alone in a task, the doorway is removed with it and the reader returns.
     */
    @Test
    fun `a porta nao divide a tarefa com a tela do app`() {
        val doorway = activityInfo(LookupActivity::class.java)
        val main = activityInfo(MainActivity::class.java)

        assertNotEquals(
            "the doorway sits in the app's task, so the reader is replaced by the Yabai screen",
            main.taskAffinity,
            doorway.taskAffinity,
        )
    }

    // --- helpers --------------------------------------------------------------------------------

    private fun activityInfo(name: Class<*>): android.content.pm.ActivityInfo =
        context.packageManager.getActivityInfo(ComponentName(context, name), 0)

    private fun open(intent: Intent): LookupActivity {
        val activity = Robolectric.buildActivity(LookupActivity::class.java, intent).setup().get()
        shadowOf(Looper.getMainLooper()).idle()
        return activity
    }

    private fun field(activity: LookupActivity): EditText {
        val found = ArrayList<EditText>()
        fun walk(group: ViewGroup) {
            for (index in 0 until group.childCount) {
                when (val child = group.getChildAt(index)) {
                    is EditText -> found.add(child)
                    is ViewGroup -> walk(child)
                }
            }
        }
        walk(activity.window.decorView as ViewGroup)
        assertTrue("the lookup screen has no search field", found.isNotEmpty())
        return found.first()
    }
}
