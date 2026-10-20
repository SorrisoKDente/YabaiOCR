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
    const val DEFAULT_PROMPT = "Traduza para português e explique: "

    const val MIME_TEXT = "text/plain"

    /**
     * The flag every intent handed to `startActivity` carries, because none of them starts from an
     * activity.
     *
     * The button lives in a floating window and the tap is handled by the foreground service, not by
     * an `Activity`. Android only lets an activity-free context start one with this flag; without it
     * `startActivity` throws `AndroidRuntimeException`, and a `runCatching` around it turns the
     * exception into «nothing on this phone takes text» — for the chosen app and for the chooser
     * alike, which is exactly how the bug looked. The bubble's «Ajustes» button has always carried it.
     *
     * It belongs on what this file starts — [directIntent] and [chooserIntent] — and **not** on the
     * share the chooser wraps. See [sendIntent].
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
     * It is not always what gets started: when the system asks, the chooser wraps it as an extra and
     * launches it from its own task. A `FLAG_ACTIVITY_NEW_TASK` here would make the chosen app open a
     * task of its own, behind everything, which is what made «Perguntar sempre» look like it did
     * nothing: the user picked an app and the screen did not change. Whoever starts it **directly**
     * wraps it in [launchable] first.
     */
    fun sendIntent(payload: String, packageName: String?): Intent =
        Intent(Intent.ACTION_SEND)
            .setType(MIME_TEXT)
            .putExtra(Intent.EXTRA_TEXT, payload)
            .apply { if (!packageName.isNullOrBlank()) setPackage(packageName) }

    /** The share addressed to an app, ready for the service to start itself. */
    fun directIntent(payload: String, packageName: String?): Intent = launchable(sendIntent(payload, packageName))

    /**
     * The system chooser, for whoever never picked an app (or whose app is no longer installed).
     *
     * The flag goes on the chooser — the intent handed to `startActivity` — and deliberately not on
     * the share it wraps: the chosen app is started by the chooser, from the chooser's task, and that
     * is what brings it to the front. See [sendIntent].
     */
    fun chooserIntent(payload: String, title: String): Intent =
        launchable(Intent.createChooser(sendIntent(payload, packageName = null), title))

    /**
     * What to try, in order, for [payload]: the chosen app first, when there is one, and the system
     * chooser last.
     *
     * The chooser is on the list even when an app was chosen, because a choice can be stale — the app
     * may have been uninstalled since, and the package name is all that is remembered. A question with
     * nowhere to go would be worse than the extra tap. Whoever calls this tries them in order and
     * knows that everything after the first entry means the choice did not work.
     */
    fun candidates(payload: String, chosen: String, chooserTitle: String): List<Intent> =
        if (chosen.isBlank()) {
            listOf(chooserIntent(payload, chooserTitle))
        } else {
            listOf(directIntent(payload, chosen), chooserIntent(payload, chooserTitle))
        }

    /**
     * Every app that can be handed a piece of text, ready for the settings list.
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
}
