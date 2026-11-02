package dev.yabai.ocr.dict

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dev.yabai.ocr.DictionaryActivity
import dev.yabai.ocr.R
import dev.yabai.ocr.UpdateInterval
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.io.File
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Keeping a downloaded dictionary up to date without asking the user to import it again (R43).
 *
 * The definitions are baked into the compiled files at import time, so an update is necessarily a
 * **new download and a new compile** — there is no patch to apply. What the app can do is exactly
 * what a reinstall would do, in place: download the archive, compile it into a staging folder and
 * swap it over the installed id. The id, the enable flag and the priority survive, so from the
 * user's side it is an update, not a reinstall.
 *
 * What it cannot do: update a dictionary that arrived through «Importar arquivo». There is no
 * publisher URL to ask, so those rows say so and are left alone.
 */

/** What the network can say about an archive without downloading all of it. */
data class RemoteSignature(
    val etag: String? = null,
    val lastModified: String? = null,
    val length: Long = 0,
) {

    /**
     * Whether two signatures describe the same version: `true` same, `false` different, `null` when
     * nothing comparable came back. ETag is the strongest signal, then Last-Modified, then the size
     * (a different size is proof of a different file; an equal size proves nothing).
     */
    fun sameAs(other: RemoteSignature): Boolean? {
        val mine = etag?.trim()?.takeIf { it.isNotEmpty() }
        val theirs = other.etag?.trim()?.takeIf { it.isNotEmpty() }
        if (mine != null && theirs != null) return mine == theirs

        val myDate = lastModified?.trim()?.takeIf { it.isNotEmpty() }
        val theirDate = other.lastModified?.trim()?.takeIf { it.isNotEmpty() }
        if (myDate != null && theirDate != null) return myDate == theirDate

        // A size is not an identity: the same length can be two different releases, so only a
        // difference is proof of a difference. Saying «current» on an equal size would hide an
        // update that the server never labelled any other way.
        if (length > 0 && other.length > 0) return if (length == other.length) null else false
        return null
    }

    /** A short date for the screen, or the raw header when it is not an HTTP date. */
    val label: String?
        get() = lastModified?.let { raw ->
            try {
                val parsed = ZonedDateTime.parse(raw, DateTimeFormatter.RFC_1123_DATE_TIME)
                parsed.format(DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.getDefault()))
            } catch (t: Throwable) {
                raw
            }
        }
}

/** A downloaded archive plus the signature that identifies this version. */
data class DownloadedDictionary(val file: File, val signature: RemoteSignature)

/** Where an installed dictionary came from, written into its `meta.json` (R43). */
data class DictionarySource(
    val catalogId: String,
    val url: String,
    val signature: RemoteSignature,
)

/** The network calls an update check needs. Abstracted so the check itself can be tested offline. */
interface RemoteSource {
    suspend fun download(
        entry: DictionaryCatalog.Entry,
        target: File,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
    ): DownloadedDictionary

    suspend fun head(url: String): RemoteSignature

    suspend fun readText(url: String): String
}

enum class UpdateState {
    /** Imported from a file: there is no publisher to ask. */
    MANUAL,

    /** The server did not say enough to compare, or the copy predates the signatures. */
    UNKNOWN,

    /** Same revision/signature as the installed copy. */
    CURRENT,

    /** The publisher offers something newer. */
    AVAILABLE,

    /** The check itself failed (network, HTTP, malformed descriptor). */
    FAILED,
}

/**
 * Orders two revision labels the way a person reads them: `2026.03.05` is newer than `2025.08.12`.
 *
 * The labels come from dictionaries, not from a standard, so this only promises something when
 * both sides contain at least one number. Components are compared numerically and a missing
 * component counts as zero (`2026.03` == `2026.03.0`). Returns null when either side has no number
 * at all, which the caller has to treat as «cannot compare» rather than as «newer».
 */
internal fun compareRevisions(installed: String, remote: String): Int? {
    val left = revisionParts(installed) ?: return null
    val right = revisionParts(remote) ?: return null
    val size = maxOf(left.size, right.size)
    for (index in 0 until size) {
        val a = left.getOrElse(index) { 0L }
        val b = right.getOrElse(index) { 0L }
        if (a != b) return a.compareTo(b)
    }
    return 0
}

/** The runs of digits in [label], in order, or null when there are none. */
private fun revisionParts(label: String): List<Long>? {
    val parts = ArrayList<Long>(4)
    var index = 0
    while (index < label.length) {
        if (!label[index].isDigit()) {
            index++
            continue
        }
        val start = index
        while (index < label.length && label[index].isDigit()) index++
        val value = label.substring(start, index).toLongOrNull() ?: return null
        parts.add(value)
    }
    return parts.ifEmpty { null }
}

/** What one dictionary's check concluded. */
data class UpdateResult(
    val id: String,
    val state: UpdateState,
    /** The version the server offers, when it named one. */
    val remoteLabel: String = "",
    /** Why the check could not answer — shown to the user when it is not obvious. */
    val message: String = "",
) {
    val hasUpdate: Boolean get() = state == UpdateState.AVAILABLE

    /** Whether «Atualizar» makes sense: a real update, or the one refresh that enables checking. */
    val canRefresh: Boolean get() = state == UpdateState.AVAILABLE || state == UpdateState.UNKNOWN
}

/**
 * Compares the installed copies with what the catalog's servers publish.
 *
 * The index-backed entries (ggjz, Wiktionary) carry a `revision` string, so the check reads a 1 KB
 * descriptor and compares it with the revision stored in the compiled dictionary. The direct
 * entries (JMdict, Jitendex, KANJIDIC, surasura) have no descriptor: the check sends a HEAD and
 * compares ETag/Last-Modified/Content-Length with the signature saved at install time.
 */
open class DictionaryUpdateChecker(private val remote: RemoteSource = DictionaryDownloader()) {

    open suspend fun checkAll(installed: List<InstalledDictionary>): List<UpdateResult> = coroutineScope {
        installed.map { item ->
            async(Dispatchers.IO) { check(item) }
        }.awaitAll()
    }

    suspend fun check(item: InstalledDictionary): UpdateResult = try {
        val entry = DictionaryCatalog.find(item.sourceId.ifBlank { item.id })
        if (entry == null) {
            UpdateResult(item.id, UpdateState.MANUAL)
        } else if (entry.indexUrl != null) {
            checkRevision(entry, item)
        } else {
            checkSignature(entry, item)
        }
    } catch (t: kotlinx.coroutines.CancellationException) {
        throw t
    } catch (t: Throwable) {
        UpdateResult(item.id, UpdateState.FAILED, message = t.message ?: t.javaClass.simpleName)
    }

    private suspend fun checkRevision(
        entry: DictionaryCatalog.Entry,
        item: InstalledDictionary,
    ): UpdateResult {
        val json = remote.readText(entry.indexUrl.orEmpty())
        val revision = try {
            JsonParser.parseString(json).asJsonObject
                .get("revision")?.takeIf { !it.isJsonNull }?.asString.orEmpty()
        } catch (t: Throwable) {
            ""
        }
        if (revision.isBlank()) {
            return UpdateResult(
                item.id,
                UpdateState.UNKNOWN,
                message = "o catálogo não informou a revisão",
            )
        }
        // A copy with no revision cannot be called current: any named revision is newer than
        // «unknown», and offering the update is safer than hiding it.
        //
        // The comparison is numeric, not textual. The ggjz and Wiktionary descriptors are published
        // by hand and can lag behind the archive they point at: kty-ja-pt announced 2025.08.12 while
        // the archive in the same server was compiled with 2026.03.05, and string inequality offered
        // that older label as an «update» (found on the device, R43).
        val order = compareRevisions(item.info.revision, revision)
        val state = when {
            item.info.revision.isBlank() -> UpdateState.AVAILABLE
            order != null -> if (order >= 0) UpdateState.CURRENT else UpdateState.AVAILABLE
            // Neither label has a number to order by. Equality is still meaningful; anything else
            // is offered, because hiding a real update is worse than one unnecessary download.
            item.info.revision == revision -> UpdateState.CURRENT
            else -> UpdateState.AVAILABLE
        }
        return UpdateResult(item.id, state, remoteLabel = revision)
    }

    private suspend fun checkSignature(
        entry: DictionaryCatalog.Entry,
        item: InstalledDictionary,
    ): UpdateResult {
        val url = entry.url ?: return UpdateResult(
            item.id,
            UpdateState.UNKNOWN,
            message = "o catálogo não informou de onde baixar",
        )
        val remoteSignature = remote.head(url)
        val installed = item.sourceSignature ?: return UpdateResult(
            item.id,
            UpdateState.UNKNOWN,
            message = "esta cópia foi instalada antes das verificações; atualize uma vez para ativá-las",
        )
        return when (installed.sameAs(remoteSignature)) {
            true -> UpdateResult(item.id, UpdateState.CURRENT, remoteLabel = remoteSignature.label.orEmpty())
            false -> UpdateResult(
                item.id,
                UpdateState.AVAILABLE,
                remoteLabel = remoteSignature.label.orEmpty(),
            )
            null -> UpdateResult(
                item.id,
                UpdateState.UNKNOWN,
                message = "o servidor não informou uma versão comparável",
            )
        }
    }
}

/** The last check, kept so the screen can show it after the process was gone. */
data class StoredUpdates(val checkedAt: Long, val results: Map<String, UpdateResult>)

/** Persists check results and the last notification key between runs. */
class UpdateStateStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun save(results: List<UpdateResult>, checkedAt: Long) {
        val json = JsonObject().apply {
            addProperty("checkedAt", checkedAt)
            add("results", JsonArray().apply {
                for (result in results) {
                    add(JsonObject().apply {
                        addProperty("id", result.id)
                        addProperty("state", result.state.name)
                        addProperty("remoteLabel", result.remoteLabel)
                        addProperty("message", result.message)
                    })
                }
            })
        }
        prefs.edit { putString(KEY_RESULTS, json.toString()) }
    }

    fun load(): StoredUpdates {
        val raw = prefs.getString(KEY_RESULTS, null) ?: return StoredUpdates(0, emptyMap())
        return try {
            val obj = JsonParser.parseString(raw).asJsonObject
            val checkedAt = obj.get("checkedAt")?.asLong ?: 0
            val results = LinkedHashMap<String, UpdateResult>()
            obj.getAsJsonArray("results")?.forEach { element ->
                val result = element.asJsonObject
                val id = result.get("id")?.asString ?: return@forEach
                val state = runCatching {
                    UpdateState.valueOf(result.get("state")?.asString.orEmpty())
                }.getOrDefault(UpdateState.UNKNOWN)
                results[id] = UpdateResult(
                    id = id,
                    state = state,
                    remoteLabel = result.get("remoteLabel")?.asString.orEmpty(),
                    message = result.get("message")?.asString.orEmpty(),
                )
            }
            StoredUpdates(checkedAt, results)
        } catch (t: Throwable) {
            StoredUpdates(0, emptyMap())
        }
    }

    /** A dictionary was just installed or removed: its old conclusion no longer applies. */
    fun forget(id: String) {
        val current = load()
        if (!current.results.containsKey(id)) return
        save(current.results.values.filter { it.id != id }, current.checkedAt)
    }

    fun notifiedKey(): String = prefs.getString(KEY_NOTIFIED, "").orEmpty()

    fun setNotifiedKey(key: String) = prefs.edit { putString(KEY_NOTIFIED, key) }

    companion object {
        private const val PREFS_NAME = "yabai_ocr_dictionary_updates"
        private const val KEY_RESULTS = "results"
        private const val KEY_NOTIFIED = "notified"
    }
}

/** Posts the «updates available» notification, at most once per set of versions. */
class DictionaryUpdateNotifier(private val context: Context) {

    fun show(results: List<UpdateResult>) {
        val available = results.filter { it.hasUpdate }
        val manager = NotificationManagerCompat.from(context)
        if (available.isEmpty()) {
            manager.cancel(NOTIFICATION_ID)
            UpdateStateStore(context).setNotifiedKey("")
            return
        }

        val key = available.map { "${it.id}:${it.remoteLabel}" }.sorted().joinToString("|")
        val store = UpdateStateStore(context)
        if (key == store.notifiedKey()) return

        // Without the permission the check still runs and the screen still shows the result; there
        // is simply no tray to put it in. Not remembering the key lets the notification appear on
        // the first check after the permission is granted.
        if (!manager.areNotificationsEnabled()) return

        ensureChannel()
        val intent = PendingIntent.getActivity(
            context,
            0,
            DictionaryActivity.intent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val names = available.mapNotNull { result ->
            DictionaryCatalog.find(result.id)?.name ?: result.id
        }
        val title = context.resources.getQuantityString(
            R.plurals.dictionary_updates_notification_title,
            available.size,
            available.size,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_menu_dictionary)
            .setContentTitle(title)
            .setContentText(names.joinToString(", "))
            .setStyle(NotificationCompat.BigTextStyle().bigText(names.joinToString(", ")))
            .setContentIntent(intent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .build()
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
        store.setNotifiedKey(key)
    }

    private fun ensureChannel() {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.dictionary_updates_channel),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.dictionary_updates_channel_description)
            },
        )
    }

    companion object {
        private const val CHANNEL_ID = "dictionary_updates"
        private const val NOTIFICATION_ID = 0x0D1C
    }
}

/** Runs a check, records it and notifies. Shared by the screen and the periodic worker. */
suspend fun runDictionaryUpdateCheck(
    context: Context,
    remote: RemoteSource = DictionaryDownloader(),
): List<UpdateResult> {
    val installed = DictionaryRepository.get(context).installed()
    if (installed.isEmpty()) return emptyList()
    val results = DictionaryUpdateChecker(remote).checkAll(installed)
    UpdateStateStore(context).save(results, System.currentTimeMillis())
    DictionaryUpdateNotifier(context).show(results)
    return results
}

/** The periodic check that runs when the app is not open. */
class DictionaryUpdateWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        try {
            runDictionaryUpdateCheck(applicationContext)
        } catch (t: Throwable) {
            // A failed check is not worth a retry storm: the next period asks again, and the
            // screen always lets the user ask now.
        }
        return Result.success()
    }
}

/** Schedules (or cancels) the periodic check. */
object DictionaryUpdateScheduler {

    const val WORK_NAME = "dictionary-update-check"

    /** Called on app start: keeps an existing schedule, creates one if it is missing. */
    fun ensure(context: Context, interval: UpdateInterval) {
        if (interval == UpdateInterval.OFF) return
        enqueue(context, interval, ExistingPeriodicWorkPolicy.KEEP)
    }

    /** Called when the user changes the choice: the new period starts from now. */
    fun set(context: Context, interval: UpdateInterval) {
        if (interval == UpdateInterval.OFF) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            return
        }
        enqueue(context, interval, ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE)
    }

    private fun enqueue(
        context: Context,
        interval: UpdateInterval,
        policy: ExistingPeriodicWorkPolicy,
    ) {
        val request = PeriodicWorkRequestBuilder<DictionaryUpdateWorker>(
            interval.days,
            TimeUnit.DAYS,
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, policy, request)
    }
}
