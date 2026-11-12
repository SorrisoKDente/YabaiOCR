package dev.yabai.ocr.ai

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import org.junit.Assert.assertEquals
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
 * longer there ends up at the list instead of nowhere.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w320dp-h640dp-mdpi")
class AskAiTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    // --- the question ---------------------------------------------------------------------------

    @Test
    fun `the question is the prompt and then the text`() {
        assertEquals(
            "Traduza para português e explique a gramática, vocabulário e nuances desta frase em japonês: 職員さんに聞いた",
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

    // --- what a tap does ------------------------------------------------------------------------

    /**
     * The fixed app is opened, with no list in the way.
     *
     * This is the path that has always worked on the phone: `startActivity` called here, from the
     * service, with the package named. «Perguntar sempre» now takes the same road.
     */
    @Test
    fun `a fixed app that is installed is opened directly`() {
        val plan = AskAi.plan("com.openai.chatgpt", APPS)

        assertEquals(AskAi.Plan.Direct("com.openai.chatgpt"), plan)
    }

    @Test
    fun `with nothing fixed the installed apps are offered`() {
        val plan = AskAi.plan("", APPS)

        assertEquals(AskAi.Plan.Choose(APPS), plan)
    }

    /**
     * A remembered package can be uninstalled between two captures. Opening it anyway would fail and
     * be reported as «no app accepts text» on a phone full of apps, so the list takes its place.
     */
    @Test
    fun `a fixed app that was uninstalled falls back to the list`() {
        val plan = AskAi.plan("com.example.gone", APPS)

        assertEquals(AskAi.Plan.Choose(APPS), plan)
    }

    @Test
    fun `a phone with nothing that takes text says so`() {
        assertEquals(AskAi.Plan.Nothing, AskAi.plan("com.openai.chatgpt", emptyList()))
        assertEquals(AskAi.Plan.Nothing, AskAi.plan("", emptyList()))
    }

    /**
     * The panel is taken down by `onOpened`, so it must run exactly when an app opened — not before
     * the attempt, and not after a failure, when the panel is the only thing that can still say what
     * went wrong and still holds the list of apps.
     */
    @Test
    fun `the panel is taken down only after an app opened`() {
        val opened = ArrayList<String>()
        val tried = ArrayList<String>()

        val success = AskAi.attempt(
            candidates = listOf(AskAi.directIntent("職員", "com.openai.chatgpt")),
            start = { tried.add("first"); true },
            onOpened = { opened.add("closed") },
        )

        assertTrue("a successful attempt was reported as a failure", success)
        assertEquals(listOf("first"), tried)
        assertEquals(listOf("closed"), opened)

        opened.clear()
        val failure = AskAi.attempt(
            candidates = listOf(AskAi.directIntent("職員", "com.openai.chatgpt")),
            start = { false },
            onOpened = { opened.add("closed") },
        )

        assertTrue("a failed attempt was reported as a success", !failure)
        assertEquals("the panel was taken down with nothing to show", emptyList<String>(), opened)
    }

    /** The first app that opens ends the line: the rest are not started on top of it. */
    @Test
    fun `an attempt stops at the first share that opens`() {
        val tried = ArrayList<String>()

        AskAi.attempt(
            candidates = listOf(
                AskAi.directIntent("職員", "com.example.gone"),
                AskAi.directIntent("職員", "com.openai.chatgpt"),
            ),
            start = {
                tried.add(it.getPackage().orEmpty())
                it.getPackage() == "com.openai.chatgpt"
            },
        )

        assertEquals(listOf("com.example.gone", "com.openai.chatgpt"), tried)
    }

    /**
     * Every intent the service hands to `startActivity` carries `FLAG_ACTIVITY_NEW_TASK`: Android
     * refuses an activity-free context without it, and a `runCatching` around the exception turned
     * every tap into «no app accepts text».
     */
    @Test
    fun `everything the service starts can be started by a service`() {
        val tried = listOf(
            AskAi.directIntent("職員", "com.openai.chatgpt"),
            AskAi.directIntent("職員", null),
        )

        for (intent in tried) {
            assertTrue(
                "${intent.action} cannot be started from a service without FLAG_ACTIVITY_NEW_TASK",
                intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0,
            )
        }
    }

    /** The share itself says what is sent; the flag belongs to the intent that starts it. */
    @Test
    fun `the share alone carries no launch flag`() {
        val share = AskAi.sendIntent("職員", "com.openai.chatgpt")

        assertEquals(0, share.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
        assertEquals("com.openai.chatgpt", share.getPackage())
        assertEquals("職員", share.getStringExtra(Intent.EXTRA_TEXT))
    }

    // --- the list of apps -----------------------------------------------------------------------

    @Suppress("DEPRECATION")
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

        /** The apps these tests pretend are installed, in the order `targets` would give them. */
        val APPS = listOf(
            AskAi.Target("ChatGPT", "com.openai.chatgpt"),
            AskAi.Target("Gemini", "com.google.android.apps.bard"),
        )

        /**
         * The MIME type the other app is promised, written out rather than taken from [AskAi]: it is
         * not a name this app is free to change, it is what every app that takes a shared string
         * declares, and comparing it against our own constant would follow it wherever it went.
         */
        const val PLAIN_TEXT = "text/plain"
    }
}
