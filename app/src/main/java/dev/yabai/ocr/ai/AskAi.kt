package dev.yabai.ocr.ai

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

/**
 * Hands the recognised text to an app the user already has, instead of calling a model from here.
 *
 * There is no key, no account and nothing to pay for behind this: the text is copied to the clipboard
 * and sent as an ordinary share, so whatever AI app is on the phone — ChatGPT, Gemini, Copilot,
 * Claude, Perplexity, or one that does not exist yet — can answer it. That is also why there is no
 * list of supported apps anywhere in this file: the list is whatever is installed.
 *
 * What the trade costs is a question: the app on the other side knows nothing about this one, so what
 * is sent is a prompt (which the user can rewrite in the settings, or clear) and then the text.
 */
object AskAi {

    /** What is written in front of the text when the user has not changed the prompt. */
    const val DEFAULT_PROMPT = "Traduza para português e explique a gramática, vocabulário e nuances desta frase em japonês:"

    const val MIME_TEXT = "text/plain"

    /**
     * The flag every intent handed to `startActivity` carries, because none of them starts from an
     * activity.
     *
     * The button lives in a floating window and the tap is handled by the foreground service, not by
     * an `Activity`. Android only lets an activity-free context start one with this flag; without it
     * `startActivity` throws `AndroidRuntimeException`, and a `runCatching` around it turns the
     * exception into «nothing on this phone takes text» — which is exactly how the old bug looked. The
     * bubble's «Ajustes» button has always carried it.
     *
     * It belongs on what this file starts — [directIntent] — and **not** on the share itself. See
     * [sendIntent].
     */
    const val NEW_TASK = Intent.FLAG_ACTIVITY_NEW_TASK

    /** One app that can be handed a piece of text, as the settings list it. */
    data class Target(val label: String, val packageName: String)

    /** Makes [intent] startable from the service. Applied to everything that is actually launched. */
    private fun launchable(intent: Intent): Intent = intent.addFlags(NEW_TASK)

    /**
     * What actually leaves the panel: the prompt, when there is one, and then the text.
     *
     * Nothing on either side is nothing to send. The two are separated by a single space however the
     * user typed it — a prompt ending in «:» is meant to be followed by the text on the same line —
     * and whitespace the user dragged in from elsewhere is not part of the question.
     */
    fun request(prompt: String, text: String): String {
        val body = text.trim()
        if (body.isEmpty()) return ""
        val lead = prompt.trim()
        return if (lead.isEmpty()) body else "$lead $body"
    }

    /**
     * The share itself — destination and payload — **without** the launch flag.
     *
     * The flag belongs to whatever `startActivity` is handed, and that is [directIntent], never the
     * payload: one intent describes *what* is sent, the other says *who* starts it.
     */
    fun sendIntent(payload: String, packageName: String?): Intent =
        Intent(Intent.ACTION_SEND)
            .setType(MIME_TEXT)
            .putExtra(Intent.EXTRA_TEXT, payload)
            .apply { if (!packageName.isNullOrBlank()) setPackage(packageName) }

    /**
     * The share addressed to an app, ready for the service to start itself.
     *
     * This is the only way a question leaves the panel: the system chooser was tried twice — the share
     * marked as a new task, then not marked — and on the phone this was tested on both ended the same
     * way, with the chooser closing and the screen unchanged. See [Plan].
     */
    fun directIntent(payload: String, packageName: String?): Intent = launchable(sendIntent(payload, packageName))

    /**
     * The apps worth offering for a question: those that answer with text themselves.
     *
     * The list the package manager reports is every app that accepts a shared string — Gmail,
     * Bluetooth, «Converter para JPEG», a download manager. None of them can answer a question, and
     * thirty of them turn a two-tap choice into a hunt. This keeps the ones that are recognisably an
     * AI or a translator, and never returns an empty list: on a phone with an AI nobody has heard of,
     * the full list is better than «no app takes text», and an empty list would say the wrong thing.
     *
     * The judgement is a guess made from a name, so the choice is never taken away: the picker that
     * shows this list also offers the complete one (see `AppPickerOverlay`).
     */
    fun relevant(all: List<Target>): List<Target> {
        val picked = all.filter { isAiOrTranslation(it) }
        return picked.ifEmpty { all }
    }

    /**
     * One row of the app list the settings screen draws (R18).
     *
     * The same list, read the same way, in the panel and in the settings: the panel had the short
     * list and a footer to the full one since R11, while the settings still offered Gmail and
     * Bluetooth in one flat menu. Sharing the *shape* of the list, and not just the filter, is what
     * keeps the two screens from drifting apart again.
     */
    sealed interface Row {
        /** «Automático»: ask, and pick from the short list at that moment. */
        data object Automatic : Row

        /** One app. */
        data class App(val target: Target) : Row

        /** «Mostrar todos (N)» / «Só os de IA»: the same list, the other half. */
        data object Toggle : Row
    }

    /**
     * The rows of that list: the automatic choice, [all] or the short list, and the footer when the
     * phone reports more apps than are being shown.
     */
    fun rows(all: List<Target>, showingAll: Boolean): List<Row> {
        val short = relevant(all)
        val shown = if (showingAll) all else short
        val rows = ArrayList<Row>(shown.size + 2)
        rows.add(Row.Automatic)
        shown.forEach { rows.add(Row.App(it)) }
        if (all.size > short.size) rows.add(Row.Toggle)
        return rows
    }

    private fun isAiOrTranslation(target: Target): Boolean {
        val packageName = target.packageName.lowercase()
        if (KNOWN_AI_PACKAGES.any { packageName == it || packageName.startsWith("$it.") }) return true
        return tokens(target.label).any { it in AI_WORDS }
    }

    /** A label cut into lowercase words: «Google Tradutor» is *google* and *tradutor*. */
    private fun tokens(label: String): List<String> =
        label.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }

    /**
     * What a tap on «Perguntar à IA» should do.
     *
     * The system chooser is deliberately not on this list. On the phone this was tested on, picking a
     * name in it closed the chooser and left the screen exactly as it was: the chosen app never came
     * to the front, with `FLAG_ACTIVITY_NEW_TASK` on the share it wraps or without it. Opening the
     * app ourselves — the way a fixed app is opened — is the one route known to work there, so the
     * list of apps is drawn by this app and the tap goes straight to [directIntent].
     */
    sealed interface Plan {
        /** Open [packageName] now. */
        data class Direct(val packageName: String) : Plan

        /** Nobody is fixed, or the fixed app is gone: offer [apps] to the user. */
        data class Choose(val apps: List<Target>) : Plan

        /** Nothing on the phone takes text. */
        data object Nothing : Plan
    }

    /**
     * What to do with a tap, from the package remembered in the settings and the apps installed now.
     *
     * A remembered package that is no longer installed is not opened: `startActivity` would fail and
     * the user would be told «no app accepts text» on a phone full of them. It falls through to the
     * list instead, and the caller is expected to forget the name.
     */
    fun plan(chosen: String, installed: List<Target>): Plan = when {
        installed.isEmpty() -> Plan.Nothing
        chosen.isNotBlank() && installed.any { it.packageName == chosen } -> Plan.Direct(chosen)
        else -> Plan.Choose(installed)
    }

    /**
     * Tries [candidates] in order, stopping at the first that opens, and says whether one did.
     *
     * [onOpened] runs at that moment and only then: it is where the panel is taken down, and taking
     * the panel down after a failure would leave the user without the app, the warning, or the text.
     */
    fun attempt(
        candidates: List<Intent>,
        start: (Intent) -> Boolean,
        onOpened: () -> Unit = {},
    ): Boolean {
        for (intent in candidates) {
            if (!start(intent)) continue
            onOpened()
            return true
        }
        return false
    }

    /**
     * Every app that can be handed a piece of text, ready for the settings list and for the panel.
     *
     * On Android 11 and newer the package manager only answers for apps the manifest declares an
     * interest in, which is why the manifest carries a `text/plain` `<queries>` entry: without it this
     * comes back empty on every phone, and the list would look like «no app on this phone can do it».
     */
    fun targets(context: Context): List<Target> {
        val intent = Intent(Intent.ACTION_SEND).setType(MIME_TEXT)
        val manager = context.packageManager
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            manager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            manager.queryIntentActivities(intent, 0)
        }
        return order(
            resolved.map { Target(it.loadLabel(manager).toString(), it.activityInfo.packageName) },
            ourPackage = context.packageName,
        )
    }

    /**
     * The list as the user sees it: one entry per app — an app with two activities that take text is
     * still one choice — our own app left out (sending the text to ourselves reads like a mistake),
     * and names in alphabetical order so the list does not move around between openings.
     */
    fun order(all: List<Target>, ourPackage: String): List<Target> =
        all.asSequence()
            .filter { it.packageName != ourPackage }
            .distinctBy { it.packageName }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
            .toList()

    /**
     * The name to show for the app the question goes to, or [none] when it goes to the chooser.
     *
     * A package that is not in [installed] is *not* shown by name: it was uninstalled, the chooser has
     * taken its place, and the settings should say so rather than keep the name of an app that is not
     * there (the name would be the only trace of it left, since a package is all that is stored).
     */
    fun label(packageName: String, installed: List<Target>, none: String): String =
        installed.firstOrNull { it.packageName == packageName }?.label ?: none

    /**
     * Packages known to be an AI or a translator, whatever the phone calls them.
     *
     * A package name survives what a label does not: the same app is «ChatGPT», «ChatGPT Plus» or
     * «OpenAI» depending on the launcher's language pack, but the package is the same. Matching is by
     * the package itself or by a dot after it, so a helper app shipped inside the same namespace
     * (`com.openai.chatgpt.something`) counts as the app it belongs to.
     */
    private val KNOWN_AI_PACKAGES = listOf(
        "com.openai.chatgpt",
        "com.google.android.apps.bard",
        "com.microsoft.copilot",
        "com.microsoft.office.copilot",
        "com.anthropic.claude",
        "ai.perplexity.app.android",
        "com.perplexity",
        "com.deepseek.chat",
        "com.deepseek.app",
        "ai.x.grok",
        "com.xai.grok",
        "com.poe.android",
        "com.character.ai",
        "com.characterai",
        "com.larus.nova",
        "com.moonshot.kimi",
        "com.alibaba.qwen",
        "com.tencent.hunyuan",
        "com.google.android.apps.translate",
        "com.microsoft.translator",
        "com.deepl.mobiletranslator",
        "com.yandex.translate",
        "com.baidu.translate",
        "com.miui.translator",
        "com.coloros.translate",
        "com.coloros.translator",
    )

    /**
     * Words that make a name read as an AI or a translator, in the languages the app speaks.
     *
     * Whole words only: «AI» as a piece of «Media» or «IA» as a piece of «Galeria» would drag the
     * gallery and the music player back onto the list. Accented and unaccented spellings are both
     * here because the same app can be «Tradutor» on one phone and «Traducao» on the next.
     */
    private val AI_WORDS = setOf(
        "ai",
        "ia",
        "gpt",
        "chatgpt",
        "chat",
        "chatbot",
        "gemini",
        "copilot",
        "claude",
        "perplexity",
        "deepseek",
        "grok",
        "llm",
        "poe",
        "deepl",
        "qwen",
        "kimi",
        "assistant",
        "assistente",
        "translate",
        "translator",
        "translation",
        "tradutor",
        "tradutora",
        "tradução",
        "traducao",
        "traduções",
        "traducoes",
        "interpreter",
        "intérprete",
        "interprete",
    )
}
