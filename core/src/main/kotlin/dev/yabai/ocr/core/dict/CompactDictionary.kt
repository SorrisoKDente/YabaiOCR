package dev.yabai.ocr.core.dict

import com.google.gson.JsonParser
import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import java.util.PriorityQueue
import kotlin.math.min

/** File names of the compiled dictionary layout. */
object DictFiles {
    const val META = "meta.json"

    const val TERM_KEYS = "terms.keys"
    const val TERM_KEYOFF = "terms.keyoff"
    const val TERM_KEYSTART = "terms.keystart"
    const val TERM_REFS = "terms.refs"
    const val TERM_EFLAGS = "terms.eflags"
    const val TERM_DAT = "terms.dat"
    const val TERM_ENTOFF = "terms.entoff"

    const val KANJI_KEYS = "kanji.keys"
    const val KANJI_KEYOFF = "kanji.keyoff"
    const val KANJI_KEYSTART = "kanji.keystart"
    const val KANJI_REFS = "kanji.refs"
    const val KANJI_DAT = "kanji.dat"
    const val KANJI_ENTOFF = "kanji.entoff"
}

/**
 * A compiled dictionary, ready to be queried.
 *
 * The sorted key index is held in memory (a few megabytes even for JMdict) because binary searching
 * it is the hot path of every lookup. The definition blobs stay on disk and are read one at a time,
 * which is what keeps a dictionary several times the size of the APK from costing several times the
 * APK in RAM.
 */
class CompactDictionary private constructor(
    val dir: File,
    val info: DictionaryInfo,
    private val terms: Index?,
    private val kanji: Index?,
) : Closeable {

    private class Index(
        val keys: ByteArray,
        val keyOffsets: IntArray,
        val keyStarts: IntArray,
        val refs: IntArray,
        /** One byte per entry: the part-of-speech bitmask, 0 for words that never inflect. */
        val entryFlags: ByteArray?,
        val blobFile: File,
        val blobOffsets: IntArray,
    ) : Closeable {
        val keyCount: Int get() = keyOffsets.size - 1

        private var blob: RandomAccessFile? = null
        private val lock = Any()

        fun readBlob(id: Int): ByteArray {
            if (id < 0 || id + 1 >= blobOffsets.size) return ByteArray(0)
            val start = blobOffsets[id]
            val length = blobOffsets[id + 1] - start
            if (length <= 0) return ByteArray(0)
            val bytes = ByteArray(length)
            synchronized(lock) {
                val file = blob ?: RandomAccessFile(blobFile, "r").also { blob = it }
                file.seek(start.toLong())
                file.readFully(bytes)
            }
            return bytes
        }

        override fun close() {
            synchronized(lock) {
                runCatching { blob?.close() }
                blob = null
            }
        }
    }

    val termCount: Int get() = info.termCount
    val kanjiCount: Int get() = info.kanjiCount

    /** True when this dictionary has the word in any form. */
    fun hasTerm(key: String): Boolean = terms?.let { position(it, key) } != null

    /**
     * Every entry stored under [key].
     *
     * @param allowedFlags bitmask a deinflected search must match; pass 0 for an exact-form lookup,
     *   which accepts everything.
     */
    fun termEntries(key: String, allowedFlags: Int = 0): List<DictionaryEntry> {
        val index = terms ?: return emptyList()
        val position = position(index, key) ?: return emptyList()

        val out = ArrayList<DictionaryEntry>(4)
        val flags = index.entryFlags
        for (slot in index.keyStarts[position] until index.keyStarts[position + 1]) {
            val id = index.refs[slot]
            if (allowedFlags != 0) {
                val entryFlags = if (flags != null && id < flags.size) flags[id].toInt() and 0xFF else 0
                if (!Deinflector.compatible(entryFlags, allowedFlags)) continue
            }
            val entry = decodeEntry(index, id) ?: continue
            out.add(entry)
        }
        // A word written exactly as asked always beats a homophone reached through its reading.
        // Without this, は (the topic particle) resolves to 歯, "tooth", because 歯 is *read* は.
        out.sortWith(
            compareByDescending<DictionaryEntry> { if (it.term == key) 1 else 0 }
                .thenByDescending { it.score },
        )
        return out
    }

    /**
     * The keys that begin with [prefix], at most [limit], **shortest first**.
     *
     * A key is a headword **or** a reading: the compiled index stores both under the same sorted
     * list, which is why a lookup by reading finds the kanji headword. That makes the raw list the
     * right place to look for «what else starts like this» (R31) — 日本 finds 日本語, 日本人, and
     * にほん finds 日本.
     *
     * Not just the first keys in index order, though: the index is sorted by UTF-8 bytes, so every
     * kana continuation comes before every kanji one, and 日本… would spend the whole list on
     * 日本じゅう, 日本アルプス and other loanwords long before reaching 日本語. The shortest
     * completions are the compounds a query usually starts, so the scan goes through every key with
     * the prefix — a byte comparison, no disk read — and only the chosen ones are decoded.
     */
    fun keysStartingWith(prefix: String, limit: Int): List<String> {
        val index = terms ?: return emptyList()
        if (prefix.isEmpty() || limit <= 0) return emptyList()
        val probe = prefix.toByteArray(StandardCharsets.UTF_8)

        // A bounded max-heap: the largest (longest, then latest) key is dropped whenever a shorter
        // one arrives, so the heap always holds the shortest [limit] keys seen so far.
        val best = PriorityQueue(
            compareByDescending<Int> { index.keyOffsets[it + 1] - index.keyOffsets[it] }
                .thenByDescending { it },
        )
        var slot = lowerBound(index, probe)
        while (slot < index.keyCount && startsWith(index, slot, probe)) {
            best.add(slot)
            if (best.size > limit) best.poll()
            slot++
        }
        return best
            .sortedWith(compareBy({ index.keyOffsets[it + 1] - index.keyOffsets[it] }, { it }))
            .map { keyAt(index, it) }
    }

    /** The kanji entry for a single character, if this dictionary has kanji data. */
    fun kanjiEntry(character: String): KanjiEntry? {
        val index = kanji ?: return null
        val position = position(index, character) ?: return null
        val id = index.refs[index.keyStarts[position]]
        val bytes = index.readBlob(id)
        if (bytes.isEmpty()) return null
        val reader = BlobReader(bytes)
        reader.int() // score
        reader.string() // the character itself
        val onYomi = reader.string()
        val kunYomi = reader.string()
        val body = reader.rest()
        if (body.isBlank()) return null
        // The readings are kept apart from the rendered body because the furigana pass needs them
        // as readings, not as a line of text: 訓: た.べる is not something to write after a kanji.
        return KanjiEntry(character, body, onYomi, kunYomi, info.title)
    }

    private fun decodeEntry(index: Index, id: Int): DictionaryEntry? {
        val bytes = index.readBlob(id)
        if (bytes.isEmpty()) return null
        return try {
            val reader = BlobReader(bytes)
            val score = reader.int()
            val term = reader.string()
            val reading = reader.string()
            val tags = reader.string()
            val definition = reader.rest()
            if (term.isEmpty() || definition.isEmpty()) null
            else DictionaryEntry(term, reading, tags, 0, score, definition)
        } catch (t: Throwable) {
            null
        }
    }

    /** Lower bound search over the sorted key blob; null when absent, otherwise its slot. */
    private fun position(index: Index, key: String): Int? {
        val probe = key.toByteArray(StandardCharsets.UTF_8)
        var low = 0
        var high = index.keyCount - 1
        while (low <= high) {
            val mid = (low + high) ushr 1
            val comparison = compareKey(index, mid, probe)
            when {
                comparison < 0 -> low = mid + 1
                comparison > 0 -> high = mid - 1
                else -> return mid
            }
        }
        return null
    }

    /** First slot whose key is not smaller than [probe]; [keyCount] when every key is smaller. */
    private fun lowerBound(index: Index, probe: ByteArray): Int {
        var low = 0
        var high = index.keyCount
        while (low < high) {
            val mid = (low + high) ushr 1
            if (compareKey(index, mid, probe) < 0) low = mid + 1 else high = mid
        }
        return low
    }

    /** Whether the key at [slot] begins with [probe], byte for byte. */
    private fun startsWith(index: Index, slot: Int, probe: ByteArray): Boolean {
        val start = index.keyOffsets[slot]
        val length = index.keyOffsets[slot + 1] - start
        if (length < probe.size) return false
        for (indexInProbe in probe.indices) {
            if (index.keys[start + indexInProbe] != probe[indexInProbe]) return false
        }
        return true
    }

    private fun keyAt(index: Index, slot: Int): String {
        val start = index.keyOffsets[slot]
        return String(index.keys, start, index.keyOffsets[slot + 1] - start, StandardCharsets.UTF_8)
    }

    private fun compareKey(index: Index, slot: Int, probe: ByteArray): Int {
        val keys = index.keys
        var i = index.keyOffsets[slot]
        val end = index.keyOffsets[slot + 1]
        var j = 0
        val probeLength = probe.size
        val common = min(end - i, probeLength)
        while (j < common) {
            val a = keys[i].toInt() and 0xFF
            val b = probe[j].toInt() and 0xFF
            if (a != b) return a - b
            i++
            j++
        }
        return (end - index.keyOffsets[slot]) - probeLength
    }

    override fun close() {
        terms?.close()
        kanji?.close()
    }

    /** Sequential reader over one entry blob. */
    private class BlobReader(private val bytes: ByteArray) {
        private var position = 0

        fun int(): Int {
            if (position + 4 > bytes.size) throw IllegalStateException("blob truncated")
            val value = ((bytes[position].toInt() and 0xFF) shl 24) or
                ((bytes[position + 1].toInt() and 0xFF) shl 16) or
                ((bytes[position + 2].toInt() and 0xFF) shl 8) or
                (bytes[position + 3].toInt() and 0xFF)
            position += 4
            return value
        }

        fun string(): String {
            if (position + 2 > bytes.size) throw IllegalStateException("blob truncated")
            val length = ((bytes[position].toInt() and 0xFF) shl 8) or (bytes[position + 1].toInt() and 0xFF)
            position += 2
            if (position + length > bytes.size) throw IllegalStateException("blob truncated")
            val value = String(bytes, position, length, StandardCharsets.UTF_8)
            position += length
            return value
        }

        fun rest(): String {
            val value = String(bytes, position, bytes.size - position, StandardCharsets.UTF_8)
            position = bytes.size
            return value
        }
    }

    companion object {
        /** Opens a compiled dictionary directory, or null when it is missing or incomplete. */
        fun open(dir: File): CompactDictionary? {
            val metaFile = File(dir, DictFiles.META)
            if (!metaFile.isFile) return null

            val info = try {
                readMeta(metaFile)
            } catch (t: Throwable) {
                return null
            }

            val terms = openIndex(dir, DictFiles.TERM_KEYS, withFlags = true) ?: return null
            val kanji = openIndex(dir, DictFiles.KANJI_KEYS, withFlags = false)
            return CompactDictionary(
                dir,
                info,
                terms?.takeIf { it.keyCount > 0 },
                kanji?.takeIf { it.keyCount > 0 },
            )
        }

        fun readMeta(metaFile: File): DictionaryInfo {
            val obj = JsonParser.parseString(metaFile.readText(StandardCharsets.UTF_8)).asJsonObject
            // Optional fields are written as JSON nulls when the dictionary does not carry them.
            // Calling asString/asInt on a JsonNull throws, and the repository reads a failed meta as
            // «no dictionary» — so an archive without an author would vanish after a restart.
            fun string(key: String): String? =
                obj.get(key)?.takeIf { !it.isJsonNull }?.asString

            fun number(key: String): Int =
                obj.get(key)?.takeIf { !it.isJsonNull }?.asInt ?: 0

            return DictionaryInfo(
                title = string("title").orEmpty(),
                revision = string("revision").orEmpty(),
                format = number("format"),
                author = string("author"),
                url = string("url"),
                description = string("description"),
                attribution = string("attribution"),
                termCount = number("termCount"),
                kanjiCount = number("kanjiCount"),
                compiledWith = number("compiledWith"),
            )
        }

        private fun openIndex(dir: File, base: String, withFlags: Boolean): Index? {
            fun file(suffix: String) = File(dir, base.removeSuffix(".keys") + suffix)
            val keysFile = file(KEYS_SUFFIX)
            val keyOffFile = file(OFF_SUFFIX)
            val keyStartFile = file(START_SUFFIX)
            val refsFile = file(REFS_SUFFIX)
            val entOffFile = file(ENTOFF_SUFFIX)
            if (!keysFile.isFile || !keyOffFile.isFile || !keyStartFile.isFile || !refsFile.isFile) return null
            if (!entOffFile.isFile) return null

            return try {
                Index(
                    keys = keysFile.readBytes(),
                    keyOffsets = DictionaryCompiler.readIntArray(keyOffFile),
                    keyStarts = DictionaryCompiler.readIntArray(keyStartFile),
                    refs = DictionaryCompiler.readIntArray(refsFile),
                    entryFlags = if (withFlags) File(dir, base.removeSuffix(".keys") + ".eflags")
                        .takeIf { it.isFile }?.readBytes() else null,
                    blobFile = File(dir, base.removeSuffix(".keys") + ".dat"),
                    blobOffsets = DictionaryCompiler.readIntArray(entOffFile),
                )
            } catch (t: Throwable) {
                null
            }
        }

        private const val KEYS_SUFFIX = ".keys"
        private const val OFF_SUFFIX = ".keyoff"
        private const val START_SUFFIX = ".keystart"
        private const val REFS_SUFFIX = ".refs"
        private const val ENTOFF_SUFFIX = ".entoff"
    }
}
