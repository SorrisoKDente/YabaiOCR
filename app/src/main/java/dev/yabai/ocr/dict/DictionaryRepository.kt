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
import java.util.Locale

/** A dictionary sitting on disk, with the user's enable/priority state applied. */
data class InstalledDictionary(
    val id: String,
    val directory: File,
    val info: DictionaryInfo,
    val sourceName: String,
    val enabled: Boolean,
    val sizeBytes: Long,
)

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
        installedCache ?: discover().also { installedCache = it }
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
        onProgress: (String) -> Unit = {},
    ): InstalledDictionary = withContext(Dispatchers.IO) {
        val target = File(root, id)
        val staging = File(root, ".staging-$id")
        staging.deleteRecursively()
        staging.mkdirs()

        val info = try {
            DictionaryCompiler(zip, staging, displayName).compile(
                progress = { phase, done, _ ->
                    when (phase) {
                        DictionaryCompiler.Phase.READING ->
                            onProgress("Lendo verbetes… ($done)")

                        DictionaryCompiler.Phase.INDEXING ->
                            onProgress("Montando índice…")
                    }
                },
            )
        } catch (t: Throwable) {
            staging.deleteRecursively()
            throw t
        }

        synchronized(lock) {
            target.deleteRecursively()
            if (!staging.renameTo(target)) {
                staging.copyRecursively(target, overwrite = true)
                staging.deleteRecursively()
            }
            val order = storedOrder().toMutableList()
            if (order.none { it.first == id }) order.add(id to true)
            persistOrder(order)
            installedCache = null
            releaseLocked()
        }

        val size = target.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        InstalledDictionary(id, target, info, displayName, enabled = true, sizeBytes = size)
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
        val sourceName = try {
            JsonParser.parseString(meta.readText()).asJsonObject.get("sourceName")?.asString
        } catch (t: Throwable) {
            null
        } ?: info.title
        return InstalledDictionary(id, directory, info, sourceName, enabled, size)
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

        fun get(context: Context): DictionaryRepository =
            instance ?: synchronized(this) {
                instance ?: DictionaryRepository(context.applicationContext).also { instance = it }
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
