package dev.yabai.ocr.ai

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Tests for handing the recognised text to an AI app the user already has.
 *
 * Nothing here talks to a model: what is handed over is a share, and everything this class decides is
 * decided from strings and intents — which is the whole point of doing it this way. What the tests
 * hold down is the question that gets written, that the text reaches the other app untouched, that
 * the list of apps is the phone's list and not ours, and that a choice pointing at an app which is no
 * longer there ends up at the chooser instead of nowhere.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w320dp-h640dp-mdpi")
class AskAiTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    // --- the question ---------------------------------------------------------------------------

    @Test
    fun `the question is the prompt and then the text`() {
        assertEquals(
            "Traduza para português e explique: 職員さんに聞いた",
            AskAi.request(AskAi.DEFAULT_PROMPT, "職員さんに聞いた"),
        )
    }

    /** An empty prompt is a choice, not a broken setting: the app then sends nothing of its own. */
    @Test
    fun `an empty prompt sends the text alone`() {
        assertEquals("職員さんに聞いた", AskAi.request("", "職員さんに聞いた"))
        assertEquals("職員さんに聞いた", AskAi.request("   ", "職員さんに聞いた"))
    }

    /** Whatever the user dragged in around the two is not part of the question. */
    @Test
    fun `whitespace around the prompt and the text is dropped`() {
        assertEquals("Explique: 職員", AskAi.request("  Explique:  ", "\n職員 \n"))
    }

    /** The prompt and the text are two things: exactly one space joins them, however it was typed. */
    @Test
    fun `the prompt and the text are never glued together`() {
        assertEquals("Explique: 職員", AskAi.request("Explique:", "職員"))
        assertEquals("Explique: 職員", AskAi.request("Explique: ", "職員"))
    }

    /**
     * A sentence is not always one line: the line breaks the user is looking at are part of what they
     * are asking about, and flattening them would change the question.
     */
    @Test
    fun `the text keeps its own line breaks`() {
        assertEquals("Explique: 職員\nさん", AskAi.request("Explique:", "職員\nさん"))
    }

    /** With no text there is no question, so there is nothing to send. */
    @Test
    fun `an empty text is nothing to send`() {
        assertEquals("", AskAi.request(AskAi.DEFAULT_PROMPT, "   "))
        assertEquals("", AskAi.request("", ""))
    }

    // --- the share ------------------------------------------------------------------------------

    @Test
    fun `the question leaves as a share of plain text`() {
        val intent = AskAi.sendIntent("Explique: 職員", "com.openai.chatgpt")

        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals(PLAIN_TEXT, intent.type)
        assertEquals("Explique: 職員", intent.getStringExtra(Intent.EXTRA_TEXT))
        assertEquals("com.openai.chatgpt", intent.getPackage())
    }

    /** No chosen app means the system asks, so the intent is addressed to nobody in particular. */
    @Test
    fun `without a chosen app the text goes to the chooser`() {
        val direct = AskAi.sendIntent("職員", null)
        assertNull("the intent was addressed to an app", direct.getPackage())

        val chooser = AskAi.chooserIntent("職員", "Perguntar a uma IA")
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        val wrapped = chooser.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        assertEquals(Intent.ACTION_SEND, wrapped?.action)
        assertEquals("職員", wrapped?.getStringExtra(Intent.EXTRA_TEXT))
    }

    @Test
    fun `a chosen app is tried first, and the chooser is still there behind it`() {
        val candidates = AskAi.candidates("職員", "com.openai.chatgpt", "Perguntar a uma IA")

        assertEquals(2, candidates.size)
        assertEquals("com.openai.chatgpt", candidates[0].getPackage())
        assertEquals(Intent.ACTION_CHOOSER, candidates[1].action)
        assertEquals(
            "職員",
            candidates[1].getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)?.getStringExtra(Intent.EXTRA_TEXT),
        )
    }

    /**
     * The whole reason the chooser is always on the list: a remembered package can be uninstalled
     * between two captures, and the question must still have somewhere to go.
     */
    @Test
    fun `with no choice there is still something to open`() {
        val candidates = AskAi.candidates("職員", "", "Perguntar a uma IA")

        assertEquals(1, candidates.size)
        assertEquals(Intent.ACTION_CHOOSER, candidates[0].action)
    }

    // --- the list of apps -----------------------------------------------------------------------

    @Test
    fun `the list is the phone's, with our own app left out`() {
        val manager = shadowOf(context.packageManager)
        manager.addResolveInfoForIntent(textIntent(), app("ChatGPT", "com.openai.chatgpt"))
        manager.addResolveInfoForIntent(textIntent(), app("Yabai OCR", context.packageName))
        manager.addResolveInfoForIntent(textIntent(), app("Gemini", "com.google.android.apps.bard"))

        assertEquals(
            listOf("com.openai.chatgpt", "com.google.android.apps.bard"),
            AskAi.targets(context).map { it.packageName },
        )
        assertEquals(
            listOf("ChatGPT", "Gemini"),
            AskAi.targets(context).map { it.label },
        )
    }

    /** Two activities of the same app that both take text are one choice, not two. */
    @Test
    fun `an app appears once however many activities it offers`() {
        val all = listOf(
            AskAi.Target("ChatGPT", "com.openai.chatgpt"),
            AskAi.Target("ChatGPT", "com.openai.chatgpt"),
        )

        assertEquals(listOf("com.openai.chatgpt"), AskAi.order(all, "dev.yabai.ocr").map { it.packageName })
    }

    /** The order the system lists them in is not the order a person reads them in. */
    @Test
    fun `the list is sorted by name`() {
        val all = listOf(
            AskAi.Target("copilot", "com.microsoft.copilot"),
            AskAi.Target("Bard", "com.google.android.apps.bard"),
            AskAi.Target("ChatGPT", "com.openai.chatgpt"),
        )

        assertEquals(
            listOf("Bard", "ChatGPT", "copilot"),
            AskAi.order(all, "dev.yabai.ocr").map { it.label },
        )
    }

    // --- the name shown next to the choice -------------------------------------------------------

    @Test
    fun `the chosen app is named the way the phone names it`() {
        val installed = listOf(AskAi.Target("ChatGPT", "com.openai.chatgpt"))

        assertEquals("ChatGPT", AskAi.label("com.openai.chatgpt", installed, NO_CHOICE))
        assertEquals(NO_CHOICE, AskAi.label("", installed, NO_CHOICE))
    }

    /**
     * A package that is not installed any more has no name to show. Falling back to «ask every time»
     * is not a prettier lie: it is what the app will actually do, because the chooser answers instead.
     */
    @Test
    fun `a package that is gone reads as no choice`() {
        val installed = listOf(AskAi.Target("ChatGPT", "com.openai.chatgpt"))

        assertEquals(NO_CHOICE, AskAi.label("com.gone.app", installed, NO_CHOICE))
    }

    @Test
    fun `no installed app at all leaves nothing to choose`() {
        assertEquals(emptyList<AskAi.Target>(), AskAi.targets(context))
        assertTrue(AskAi.order(emptyList(), "dev.yabai.ocr").isEmpty())
    }

    // --- helpers --------------------------------------------------------------------------------

    private fun textIntent(): Intent = Intent(Intent.ACTION_SEND).setType(PLAIN_TEXT)

    /** An entry in the package manager's answer, the way it would describe an installed app. */
    private fun app(label: String, packageName: String): ResolveInfo = ResolveInfo().apply {
        nonLocalizedLabel = label
        activityInfo = ActivityInfo().apply {
            this.packageName = packageName
            name = "$packageName.MainActivity"
        }
    }

    private companion object {
        const val NO_CHOICE = "Perguntar sempre"

        /**
         * The MIME type the other app is promised, written out rather than taken from [AskAi]: it is
         * not a name this app is free to change, it is what every app that takes a shared string
         * declares, and comparing it against our own constant would follow it wherever it went.
         */
        const val PLAIN_TEXT = "text/plain"
    }
}
