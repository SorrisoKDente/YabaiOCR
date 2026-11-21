package dev.yabai.ocr.desktop

import kotlinx.coroutines.delay
import kotlinx.coroutines.yield

/**
 * One dictionary the notice can offer to update.
 *
 * [entry] is what the installer needs; [fileName] is what the check compared; the two revisions are
 * what the notice prints as «old → new».
 */
internal data class DictionaryUpdateOffer(
    val entry: DesktopDictionaryCatalog.Entry,
    val fileName: String,
    val installedRevision: String,
    val remoteRevision: String,
)

/** What one pass of [DictionaryUpdateMonitor] decided to do. */
internal sealed interface DictionaryUpdateStep {
    /** Nothing to do yet; come back in [millis]. */
    data class Wait(val millis: Long) : DictionaryUpdateStep

    /** A check really ran. The result may hold no updates at all. */
    data class Checked(val result: DesktopDictCheckResult) : DictionaryUpdateStep
}

/**
 * The automatic dictionary update check, as a loop instead of a one-shot inside a window.
 *
 * The check used to live in the Settings window's `LaunchedEffect`, so it never ran on a
 * `--minimized` start — the way the reader actually keeps the app running — and, once the window had
 * been opened, `last_dict_update_check` stopped it from asking again. A preference that says «daily»
 * or «weekly» cannot depend on a window being composed: this monitor wakes on its own, asks the
 * interval, and only then touches the network.
 *
 * [now] and [check] are injectable so the schedule can be driven without a real clock or a network;
 * every other timing constant belongs to the monitor.
 */
internal class DictionaryUpdateMonitor(
    private val settings: DesktopSettings,
    private val now: () -> Long = System::currentTimeMillis,
    private val check: (DesktopSettings) -> DesktopDictCheckResult = {
        DesktopDictionaryUpdateChecker.checkUpdates(it)
    },
) {

    /** After a failure, the next attempt waits this long instead of retrying on every wake-up. */
    private var retryAt = 0L

    /**
     * One pass: check when the interval elapsed, otherwise say how long to sleep.
     *
     * The sleep is capped at [DEFAULT_IDLE_MILLIS] so a period changed in Settings takes effect on
     * the next wake-up instead of after the old, longer one.
     */
    fun step(): DictionaryUpdateStep {
        if (settings.dictionaryUpdateInterval == DesktopUpdateInterval.OFF) {
            return DictionaryUpdateStep.Wait(DEFAULT_IDLE_MILLIS)
        }

        val current = now()
        if (current < retryAt) {
            return DictionaryUpdateStep.Wait((retryAt - current).coerceAtMost(DEFAULT_IDLE_MILLIS))
        }
        if (!DesktopDictionaryUpdateChecker.shouldCheck(settings, current)) {
            return DictionaryUpdateStep.Wait(DEFAULT_IDLE_MILLIS)
        }

        return try {
            val result = check(settings)
            retryAt = 0L
            // A checker that never recorded its own timestamp would be asked again on the next pass,
            // turning the interval into a hot loop.
            if (settings.lastDictUpdateCheck < current) settings.lastDictUpdateCheck = current
            DictionaryUpdateStep.Checked(result)
        } catch (_: Throwable) {
            retryAt = now() + DEFAULT_RETRY_MILLIS
            DictionaryUpdateStep.Wait(DEFAULT_RETRY_MILLIS)
        }
    }

    /** Runs until the caller's coroutine is cancelled; [onChecked] sees every completed check. */
    suspend fun run(onChecked: suspend (DesktopDictCheckResult) -> Unit) {
        while (true) {
            // Yields every pass: a due check has no suspension point of its own, and without this a
            // cancellation would only be noticed after the next network call.
            yield()
            when (val step = step()) {
                is DictionaryUpdateStep.Wait -> delay(step.millis)
                is DictionaryUpdateStep.Checked -> onChecked(step.result)
            }
        }
    }

    companion object {
        /** Frequent enough to react to a changed period, cheap enough to ignore. */
        internal const val DEFAULT_IDLE_MILLIS = 15 * 60 * 1000L

        /** A failed check (offline, GitHub down) is not retried on the next 15-minute wake-up. */
        internal const val DEFAULT_RETRY_MILLIS = 60 * 60 * 1000L
    }
}

/** Everything the check found that can actually be installed. */
internal fun dictionaryUpdateOffers(result: DesktopDictCheckResult): List<DictionaryUpdateOffer> =
    result.remoteRevisionMap
        .filterKeys { fileName -> result.updateAvailableMap[fileName] == true }
        .mapNotNull { (fileName, remote) ->
            val entry = catalogEntryForDictionaryFile(fileName) ?: return@mapNotNull null
            DictionaryUpdateOffer(
                entry = entry,
                fileName = fileName,
                installedRevision = result.installedRevisionMap[fileName].orEmpty(),
                remoteRevision = remote,
            )
        }
        .sortedBy { offer -> offer.entry.name }

/**
 * Identity of one announcement: which dictionaries, at which remote revision.
 *
 * The same set shown twice is the same news, so it is not shown again; a newer upstream revision
 * changes the key and earns a new notice. The Android side keeps the same rule in
 * `UpdateStateStore.notifiedKey()`.
 */
internal fun dictionaryNoticeKey(offers: List<DictionaryUpdateOffer>): String =
    offers.sortedBy { it.fileName }.joinToString("|") { "${it.fileName}:${it.remoteRevision}" }

/** What a finished check means for the notice and the tray tooltip that announce updates. */
internal sealed interface DictionaryAnnouncement {
    /** Nothing is installable any more: the notice and the tray tooltip must be forgotten. */
    data object Clear : DictionaryAnnouncement

    /** The same set the reader already saw: leave the notice and the tooltip as they are. */
    data object Keep : DictionaryAnnouncement

    /** Installable updates nobody has seen yet, worth a window and a tooltip. */
    data class Show(val offers: List<DictionaryUpdateOffer>) : DictionaryAnnouncement
}

/**
 * The announcement rule, in one place.
 *
 * «Nothing installable» and «the same news again» both produce no new window, but they are not the
 * same thing: the first must also take down the tray tooltip that points at the update, the second
 * must leave it standing. Conflating them was the bug: the check kept re-announcing the same set and
 * the tooltip outlived the update it announced.
 */
internal fun dictionaryAnnouncement(
    result: DesktopDictCheckResult,
    alreadyShownKey: String,
): DictionaryAnnouncement {
    val offers = dictionaryUpdateOffers(result)
    return when {
        offers.isEmpty() -> DictionaryAnnouncement.Clear
        dictionaryNoticeKey(offers) == alreadyShownKey -> DictionaryAnnouncement.Keep
        else -> DictionaryAnnouncement.Show(offers)
    }
}
