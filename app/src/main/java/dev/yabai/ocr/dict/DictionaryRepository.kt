package dev.yabai.ocr.dict

import android.content.Context
import androidx.core.content.edit
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dev.yabai.ocr.core.dict.CompactDictionary
import dev.yabai.ocr.core.dict.DictionaryCompiler
import dev.yabai.ocr.core.dict.DictionaryInfo
import dev.yabai.ocr.core.dict.LookupEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.Locale

/** A dictionary sitting on disk, with the user's enable/priority state applied. */
data class InstalledDictionary(
    val id: String,
    val directory: File,
    val info: DictionaryInfo,
    val sourceName: String,
    val enabled: Boolean,
    val sizeBytes: Long,
    /**
     * Catalog id this copy came from, or "" for a dictionary imported from a file (R43). Downloads
     * use the catalog id as the installed id, so an old install is still recognisable.
     */
    val sourceId: String = "",
    /** HTTP identity of the archive at install time, or null when it was never recorded (R43). */
    val sourceSignature: RemoteSignature? = null,
) {
    /**
     * The dictionary as the user has to see it when several can be confused (R22).
     *
     * The title comes from inside the archive — «JMdict», «Wikcionário» — and it is the same for
     * every variant of the same dictionary: the English and the Portuguese JMdict, three
     * Wikcionários in three languages. What the user picked (the catalog entry, or the file name)
     * carries the language, so it is shown beside the title whenever it is not the title already.
     */
    val displayName: String
        get() = if (sourceName.isNotBlank() && sourceName != info.title) {
            "${info.title} · $sourceName"
        } else {
            info.title
        }
}

/**
 * Owns the compiled dictionaries on disk and the single [LookupEngine] built from them.
 *
 * There is one instance for the whole process because both the settings screen and the floating
 * service need it, and loading the key indexes twice would double the memory for no reason.
 *
 * The engine is loaded lazily on the first lookup and released explicitly: a JMdict index is a few
 * megabytes of sorted bytes and it has no business being resident while the user is not reading
 * anything.
 */
class DictionaryRepository private constructor(private val appContext: Context) {

    private val root = File(appContext.filesDir, DIRECTORY_ROOT)
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val lock = Any()
    private var engineCache: LookupEngine? = null
    private var installedCache: List<InstalledDictionary>? = null

    fun rootDirectory(): File {
        root.mkdirs()
        return root
    }

    fun installed(): List<InstalledDictionary> = synchronized(lock) {
        // Only a non-empty answer is cached. A scan that saw the dictionary folder in a transient
        // state would otherwise hide every dictionary until the process was restarted — leaving the
        // tab and coming back did not help because the empty answer was still in the cache (R43).
        installedCache?.let { return it }
        discover().also { if (it.isNotEmpty()) installedCache = it }
    }

    fun enabledCount(): Int = installed().count { it.enabled }

    /** Titles of the enabled dictionaries, in the user's priority order. */
    fun enabledTitles(): List<String> = installed().filter { it.enabled }.map { it.info.title }

    /**
     * The dictionary a stored filter points at, or null for «todos».
     *
     * A filter whose dictionary has since been disabled or removed is not a filter any more: the
     * engine would answer nothing at all, which looks like a broken dictionary rather than a stale
     * setting, so it is dropped here and the next capture is wide open again.
     */
    fun resolveFilter(stored: String): String? =
        stored.takeIf { it.isNotBlank() && enabledTitles().contains(it) }

    fun hasAny(): Boolean = installed().isNotEmpty()

    fun setEnabled(id: String, enabled: Boolean) = synchronized(lock) {
        val order = storedOrder().toMutableList()
        val index = order.indexOfFirst { it.first == id }
        if (index >= 0) order[index] = id to enabled else order.add(id to enabled)
        persistOrder(order)
        installedCache = null
        releaseLocked()
    }

    /** Moves a dictionary up (-1) or down (+1) in the lookup order. */
    fun move(id: String, delta: Int) = synchronized(lock) {
        val order = storedOrder().toMutableList()
        val index = order.indexOfFirst { it.first == id }
        val target = index + delta
        if (index < 0 || target < 0 || target >= order.size) return@synchronized
        val entry = order.removeAt(index)
        order.add(target, entry)
        persistOrder(order)
        installedCache = null
        releaseLocked()
    }

    fun delete(id: String) = synchronized(lock) {
        File(root, id).deleteRecursively()
        val order = storedOrder().toMutableList()
        order.removeAll { it.first == id }
        persistOrder(order)
        installedCache = null
        releaseLocked()
    }

    /**
     * Compiles [zip] into the repository. The dictionary becomes live only after the whole archive
     * has been rewritten, so a failure or a cancellation leaves nothing half-installed behind.
     */
    suspend fun install(
        zip: File,
        id: String,
        displayName: String,
        source: DictionarySource? = null,
        onProgress: (String) -> Unit = {},
    ): InstalledDictionary = withContext(Dispatchers.IO) {
        val target = File(root, id)
        val staging = File(root, ".staging-$id")
        staging.deleteRecursively()
        staging.mkdirs()

        val info = try {
            val compiled = DictionaryCompiler(zip, staging, displayName).compile(
                progress = { phase, done, _ ->
                    when (phase) {
                        DictionaryCompiler.Phase.READING ->
                            onProgress("Lendo verbetes… ($done)")

                        DictionaryCompiler.Phase.INDEXING ->
                            onProgress("Montando índice…")
                    }
                },
            )
            // The update check needs to know what the archive looked like when it was installed
            // (R43). It goes into the same meta.json the rest of the dictionary is read from.
            if (source != null) annotateSource(File(staging, "meta.json"), source)
            compiled
        } catch (t: Throwable) {
            staging.deleteRecursively()
            throw t
        }

        val enabled = synchronized(lock) {
            // The engine may still hold the old copy's index files open. Replacing them under it is
            // what makes an update dangerous: the reader would be searching a file that no longer
            // exists, and on platforms that lock open files the swap itself fails. The cache is
            // rebuilt from the new copy on the next lookup.
            releaseLocked()
            target.deleteRecursively()
            if (!staging.renameTo(target)) {
                target.deleteRecursively()
                staging.copyRecursively(target, overwrite = true)
                staging.deleteRecursively()
            }
            val order = storedOrder().toMutableList()
            if (order.none { it.first == id }) order.add(id to true)
            persistOrder(order)
            installedCache = null
            order.first { it.first == id }.second
        }

        val size = target.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        InstalledDictionary(
            id = id,
            directory = target,
            info = info,
            sourceName = displayName,
            enabled = enabled,
            sizeBytes = size,
            sourceId = source?.catalogId.orEmpty(),
            sourceSignature = source?.signature,
        )
    }

    /**
     * The engine for the enabled dictionaries, in the user's priority order, or null when nothing is
     * enabled. The first dictionary in the list wins ties, so priority is also a ranking.
     */
    fun lookupEngine(): LookupEngine? = synchronized(lock) {
        engineCache?.let { return it }
        val enabled = installed().filter { it.enabled }
        if (enabled.isEmpty()) return null

        val dictionaries = ArrayList<CompactDictionary>(enabled.size)
        for (item in enabled) {
            val dictionary = CompactDictionary.open(item.directory) ?: continue
            dictionaries.add(dictionary)
        }
        if (dictionaries.isEmpty()) return null

        LookupEngine(dictionaries).also { engineCache = it }
    }

    /** Frees the in-memory key indexes. They are rebuilt on the next lookup. */
    fun release() = synchronized(lock) { releaseLocked() }

    private fun releaseLocked() {
        engineCache?.let { engine ->
            // The engine holds the dictionaries; closing each one releases its blob handles.
            engine.close()
        }
        engineCache = null
    }

    // --- storage -------------------------------------------------------------------------------

    private fun discover(): List<InstalledDictionary> {
        val stored = storedOrder().toMap()
        val result = ArrayList<InstalledDictionary>(8)

        for (id in stored.keys) {
            val item = readInstalled(id, stored[id] ?: true) ?: continue
            result.add(item)
        }
        // Anything on disk the prefs do not know about (a folder copied in by hand) still shows up.
        val known = result.map { it.id }.toSet()
        val onDisk = root.listFiles()
            ?.filter { it.isDirectory && !it.name.startsWith(".") && it.name !in known }
            ?.sortedBy { it.name }
            .orEmpty()
        for (directory in onDisk) {
            readInstalled(directory.name, enabled = true, directory = directory)?.let { result.add(it) }
        }
        return result
    }

    private fun readInstalled(
        id: String,
        enabled: Boolean,
        directory: File = File(root, id),
    ): InstalledDictionary? {
        if (!directory.isDirectory) return null
        val meta = File(directory, "meta.json")
        if (!meta.isFile) return null
        val info = try {
            CompactDictionary.readMeta(meta)
        } catch (t: Throwable) {
            return null
        }
        val size = directory.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        val metaObject = try {
            JsonParser.parseString(meta.readText()).asJsonObject
        } catch (t: Throwable) {
            null
        }
        fun string(name: String): String? =
            metaObject?.get(name)?.takeIf { !it.isJsonNull }?.asString

        val sourceName = string("sourceName") ?: info.title
        val etag = string("sourceEtag")
        val lastModified = string("sourceLastModified")
        val length = metaObject?.get("sourceLength")?.takeIf { !it.isJsonNull }?.asLong ?: 0L
        val signature = if (etag != null || lastModified != null || length > 0) {
            RemoteSignature(etag, lastModified, length)
        } else {
            null
        }
        return InstalledDictionary(
            id = id,
            directory = directory,
            info = info,
            sourceName = sourceName,
            enabled = enabled,
            sizeBytes = size,
            sourceId = string("sourceId").orEmpty(),
            sourceSignature = signature,
        )
    }

    /** Adds the download's identity to the compiled `meta.json`, before it becomes live. */
    private fun annotateSource(meta: File, source: DictionarySource) {
        if (!meta.isFile) return
        val obj = try {
            JsonParser.parseString(meta.readText()).asJsonObject
        } catch (t: Throwable) {
            return
        }
        obj.addProperty("sourceId", source.catalogId)
        obj.addProperty("sourceUrl", source.url)
        source.signature.etag?.let { obj.addProperty("sourceEtag", it) }
        source.signature.lastModified?.let { obj.addProperty("sourceLastModified", it) }
        if (source.signature.length > 0) obj.addProperty("sourceLength", source.signature.length)
        meta.writeText(obj.toString(), StandardCharsets.UTF_8)
    }

    private fun storedOrder(): List<Pair<String, Boolean>> {
        val raw = prefs.getString(KEY_ORDER, null) ?: return emptyList()
        return try {
            val array = JsonParser.parseString(raw).asJsonArray
            array.mapNotNull { element ->
                val obj = element.asJsonObject
                val id = obj.get("id")?.asString ?: return@mapNotNull null
                val enabled = obj.get("enabled")?.asBoolean ?: true
                id to enabled
            }
        } catch (t: Throwable) {
            emptyList()
        }
    }

    private fun persistOrder(order: List<Pair<String, Boolean>>) {
        val array = JsonArray()
        for ((id, enabled) in order) {
            array.add(JsonObject().apply {
                addProperty("id", id)
                addProperty("enabled", enabled)
            })
        }
        prefs.edit { putString(KEY_ORDER, array.toString()) }
    }

    companion object {
        private const val DIRECTORY_ROOT = "dictionaries"
        private const val PREFS_NAME = "yabai_ocr_dicts"
        private const val KEY_ORDER = "order"

        @Volatile
        private var instance: DictionaryRepository? = null

        /**
         * The one repository of this application.
         *
         * It is keyed to the application context, not merely created once: the caches it holds —
         * the installed list, the compiled index — belong to a directory tree, and a process can
         * hold more than one application (the test runner does, one per test). Reusing the first
         * repository for a second application would answer about the wrong files, which is exactly
         * what the dictionary screen showed when it was tested with the rest of the suite.
         */
        fun get(context: Context): DictionaryRepository {
            val app = context.applicationContext
            instance?.let { if (it.appContext === app) return it }
            return synchronized(this) {
                instance?.let { if (it.appContext === app) return@synchronized it }
                DictionaryRepository(app).also { instance = it }
            }
        }

        /** A stable, filesystem-safe id for a dictionary the user imported by hand. */
        fun importedId(title: String): String {
            val slug = title.lowercase(Locale.ROOT)
                .map { if (it.isLetterOrDigit()) it else '-' }
                .joinToString("")
                .trim('-')
                .replace(Regex("-+"), "-")
                .take(40)
            val suffix = System.currentTimeMillis().toString().takeLast(5)
            return if (slug.isEmpty()) "import-$suffix" else "$slug-$suffix"
        }
    }
}
