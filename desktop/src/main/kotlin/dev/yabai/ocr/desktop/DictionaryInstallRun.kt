package dev.yabai.ocr.desktop

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * What one catalogue install reports back to the card.
 *
 * Every hook is suspending because production dispatches them to the UI thread; the tests pass plain
 * lambdas, so the order of the reports is what they check.
 */
internal class CatalogueInstallHooks(
    val installed: suspend () -> Unit = {},
    val cancelled: suspend () -> Unit = {},
    val failed: suspend (String) -> Unit = {},
    val finished: suspend () -> Unit = {},
)

/**
 * Runs one catalogue install and always reports a final state.
 *
 * [finished] runs inside [NonCancellable] on purpose: the card's reset used to be a plain
 * `withContext(Dispatchers.Main)` in `finally`, and once the job had been cancelled that call throws
 * immediately — so a cancelled download left the card saying «baixando» forever, with the Cancel
 * button gone and nothing running. The reset is the one thing that must happen even then.
 */
internal suspend fun runCatalogueInstall(install: suspend () -> Unit, hooks: CatalogueInstallHooks) {
    try {
        install()
        hooks.installed()
    } catch (e: DictionaryDownloadCancelled) {
        // The user's Cancel also cancels the job, so this report has to survive a cancelled
        // coroutine — otherwise the card resets silently and never says «cancelled».
        withContext(NonCancellable) { hooks.cancelled() }
    } catch (e: CancellationException) {
        // The job itself was cancelled: still tell the card, then let the cancellation propagate.
        withContext(NonCancellable) { hooks.cancelled() }
        throw e
    } catch (e: Exception) {
        hooks.failed(e.message.orEmpty())
    } finally {
        withContext(NonCancellable) { hooks.finished() }
    }
}
