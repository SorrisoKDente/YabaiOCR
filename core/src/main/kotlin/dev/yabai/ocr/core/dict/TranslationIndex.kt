package dev.yabai.ocr.core.dict

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import java.text.Normalizer
import java.util.Locale
import java.util.PriorityQueue
import kotlin.math.min

/**
 * The words a translated definition is made of.
 *
 * A query is typed in Portuguese or English, but the definition may carry accents, punctuation and
 * case the user does not: «Espada» must find «espada», «lâmina» must find «lamina». Tokens are
 * lowercased and stripped of combining marks, and one- and two-letter words are dropped because
 * they are almost never what someone means — «a», «de», «of» would otherwise match most of the
 * dictionary and bury the real result.
 */
internal object TranslationTokens {

    private val words = Regex("[\\p{L}\\p{N}]+")
    private val combining = Regex("\\p{M}+")

    /**
     * Words that carry no meaning of their own in a definition. They are left out of the index and
     * out of the query, so a search for «a espada» is a search for «espada».
     */
    private val stopwords = setOf(
        // Portuguese
        "de", "da", "do", "das", "dos", "em", "no", "na", "nos", "nas", "um", "uma", "uns", "umas",
        "o", "os", "as", "e", "ou", "que", "para", "por", "com", "sem", "ao", "aos", "se", "como",
        "mais", "mas", "seu", "sua", "seus", "suas", "ele", "ela", "isso", "isto", "aquilo", "ser",
        "sao", "nao", "sim", "muito", "pouco", "tambem", "quando", "onde", "qual", "quem",
        // English
        "the", "an", "and", "or", "of", "to", "in", "on", "at", "is", "are", "was", "were", "be",
        "been", "being", "it", "its", "this", "that", "these", "those", "with", "without", "for",
        "from", "by", "as", "into", "about", "not", "no", "yes", "very", "more", "most", "such",
        "which", "who", "whom", "when", "where", "what", "than", "then", "there", "their", "his",
        "her", "he", "she", "they", "them", "we", "you", "i", "me", "my", "your", "our", "one",
        // Spanish, because a Portuguese reader often owns a Spanish dictionary too
        "el", "la", "los", "las", "un", "una", "unos", "unas", "y", "o", "del", "al", "es", "son",
        "por", "para", "con", "sin", "que", "como", "mas", "su", "sus", "este", "esta", "estos",
        "estas", "muy", "tambien", "no", "si", "cuando", "donde", "cual", "quien",
    )

    /** Lowercase, no accents: what the index and the query are compared by. */
    fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(combining, "")

    /** The meaningful tokens of [text], in order, with duplicates kept. */
    fun tokenize(text: String): List<String> =
        words.findAll(normalize(text))
            .map { it.value }
            .filter { it.length >= 2 && it !in stopwords }
            .toList()
}

/** How far a translation index build has got, for a progress line in the UI. */
data class TranslationProgress(
    val dictionaryTitle: String,
    /** 0..1 while the dictionary is being indexed, 1 when it is ready to query. */
    val fraction: Float,
    /** True only while the one-time index for this dictionary is being written. */
    val building: Boolean,
)

/**
 * An inverted index over the **definitions** of a compiled dictionary, kept beside it on disk.
 *
 * A normal lookup follows the sorted key index and never reads the definitions it is not going to
 * show. Searching by meaning is the opposite: «sword» has to find the entries whose definition says
 * *sword*, and the only way to know that is to read them all. Doing that per keystroke costs
 * seconds; the roadmap measured 3,3 s for Jitendex PT-BR on a desktop and ~40–50 MB for an index
 * held in RAM. This file is the same index, built once and **stored on disk**, so only the token
 * list is in memory (~a few MB) and a query reads just the postings of the words it asked for.
 *
 * It is deliberately a sidecar (`terms.inv*`), not part of the dictionary format: existing
 * dictionaries do not need to be imported again, an old app ignores the files, and a dictionary
 * update invalidates the sidecar by its size and timestamp. The cost — disk and the one-time scan —
 * is only paid by whoever turns translation mode on.
 */
internal class TranslationIndex private constructor(
    private val keys: ByteArray,
    private val keyOffsets: IntArray,
    private val postOffsets: IntArray,
    private val postFile: File,
) : Closeable {

    private var post: RandomAccessFile? = null
    private val lock = Any()

    val tokenCount: Int get() = keyOffsets.size - 1

    /** Entry ids whose definition holds [token], sorted. Empty when the token is not indexed. */
    fun postings(token: String): IntArray {
        val slot = position(TranslationTokens.normalize(token))
        if (slot < 0) return IntArray(0)
        val from = postOffsets[slot]
        val count = postOffsets[slot + 1] - from
        if (count <= 0) return IntArray(0)
        val out = IntArray(count)
        synchronized(lock) {
            val file = post ?: RandomAccessFile(postFile, "r").also { post = it }
            file.seek(from.toLong() * 4)
            for (i in 0 until count) out[i] = file.readInt()
        }
        return out
    }

    /** Binary search over the normalized token blob; -1 when absent. */
    private fun position(token: String): Int {
        val probe = token.toByteArray(StandardCharsets.UTF_8)
        var low = 0
        var high = tokenCount - 1
        while (low <= high) {
            val mid = (low + high) ushr 1
            val compared = compare(mid, probe)
            when {
                compared < 0 -> low = mid + 1
                compared > 0 -> high = mid - 1
                else -> return mid
            }
        }
        return -1
    }

    private fun compare(slot: Int, probe: ByteArray): Int {
        val start = keyOffsets[slot]
        val length = keyOffsets[slot + 1] - start
        val count = min(length, probe.size)
        for (i in 0 until count) {
            val a = keys[start + i].toInt() and 0xFF
            val b = probe[i].toInt() and 0xFF
            if (a != b) return a - b
        }
        return length - probe.size
    }

    override fun close() {
        synchronized(lock) {
            runCatching { post?.close() }
            post = null
        }
    }

    companion object {
        private const val INDEX_FORMAT = 1

        /**
         * Opens the sidecar for [dictionary] when it exists **and** still describes this copy of
         * the dictionary. A rebuilt or updated dictionary changes `terms.dat`, and the stored
         * fingerprint no longer matches — the caller then builds it again.
         */
        fun open(dictionary: CompactDictionary): TranslationIndex? {
            val dir = dictionary.dir
            val meta = File(dir, DictFiles.INV_META)
            if (!meta.isFile) return null
            val expected = fingerprint(dictionary) ?: return null
            val stored = runCatching { meta.readText(StandardCharsets.UTF_8) }.getOrNull() ?: return null
            if (stored != expected) return null
            val keysFile = File(dir, DictFiles.INV_KEYS)
            val keyOffFile = File(dir, DictFiles.INV_KEYOFF)
            val postFile = File(dir, DictFiles.INV_POST)
            val postOffFile = File(dir, DictFiles.INV_POSTOFF)
            if (!keysFile.isFile || !keyOffFile.isFile || !postFile.isFile || !postOffFile.isFile) return null
            return try {
                TranslationIndex(
                    keys = keysFile.readBytes(),
                    keyOffsets = readIntArray(keyOffFile),
                    postOffsets = readIntArray(postOffFile),
                    postFile = postFile,
                )
            } catch (t: Throwable) {
                null
            }
        }

        /**
         * Opens the sidecar or writes it, reporting progress.
         *
         * The build streams the definitions in chunks and merges the sorted runs, so the memory it
         * needs is bounded by one chunk (~150k postings) instead of the whole 4–5 million postings
         * of a large dictionary — the difference between something a phone can do and an OOM.
         */
        fun ensure(
            dictionary: CompactDictionary,
            onProgress: (TranslationProgress) -> Unit = {},
        ): TranslationIndex {
            open(dictionary)?.let {
                onProgress(TranslationProgress(dictionary.info.title, 1f, building = false))
                return it
            }
            return build(dictionary, onProgress)
        }

        private fun build(
            dictionary: CompactDictionary,
            onProgress: (TranslationProgress) -> Unit,
        ): TranslationIndex {
            val dir = dictionary.dir
            val total = dictionary.entryCount
            require(total > 0) { "dictionary has no entries to index" }
            val title = dictionary.info.title

            // A half-written index must never look current: the meta file is the last thing to land.
            File(dir, DictFiles.INV_META).delete()
            // Announce the build before the first chunk: a small dictionary finishes before the
            // every-20k progress lines, and the UI must still know why the query is waiting.
            onProgress(TranslationProgress(title, 0f, building = true))
            val runs = ArrayList<File>()
            try {
                // --- pass 1: tokenize in bounded chunks and write sorted runs ---
                var chunkTokens = ArrayList<String>(CHUNK_PAIRS)
                var chunkIds = IntArray(CHUNK_PAIRS)
                var chunkSize = 0
                var processed = 0

                fun flushChunk() {
                    if (chunkSize == 0) return
                    val order = (0 until chunkSize).sortedWith(
                        compareBy<Int> { chunkTokens[it] }.thenBy { chunkIds[it] },
                    )
                    val run = File.createTempFile("yabai-inv-", ".run", dir)
                    DataOutputStream(BufferedOutputStream(FileOutputStream(run), 1 shl 16)).use { out ->
                        for (index in order) {
                            val bytes = chunkTokens[index].toByteArray(StandardCharsets.UTF_8)
                            out.writeInt(bytes.size)
                            out.write(bytes)
                            out.writeInt(chunkIds[index])
                        }
                    }
                    runs.add(run)
                    chunkTokens = ArrayList(CHUNK_PAIRS)
                    chunkIds = IntArray(CHUNK_PAIRS)
                    chunkSize = 0
                }

                for (id in 0 until total) {
                    val entry = dictionary.entryById(id)
                    if (entry != null) {
                        // The same word twice in one definition is still one posting: the index
                        // answers «which entries mention it», not how often.
                        for (token in TranslationTokens.tokenize(entry.definition).distinct()) {
                            if (chunkSize == CHUNK_PAIRS) flushChunk()
                            chunkTokens.add(token)
                            chunkIds[chunkSize++] = id
                        }
                    }
                    processed++
                    if (processed % PROGRESS_EVERY == 0) {
                        onProgress(TranslationProgress(title, processed.toFloat() / total * 0.75f, true))
                    }
                }
                flushChunk()

                // --- pass 2: merge the runs into one sorted token list + postings ---
                val keysOut = File(dir, DictFiles.INV_KEYS)
                val postOut = File(dir, DictFiles.INV_POST)
                val keyOffsets = IntList()
                val postOffsets = IntList()
                keyOffsets.add(0)
                postOffsets.add(0)
                var keyBytes = 0L
                var postCount = 0

                val cursors = runs.mapNotNull { run ->
                    runCatching { RunCursor(run) }.getOrNull()
                }
                val queue = PriorityQueue<RunCursor>(compareBy({ it.token }, { it.id }))
                cursors.forEach { if (it.advance()) queue.add(it) }

                DataOutputStream(BufferedOutputStream(FileOutputStream(keysOut), 1 shl 16)).use { keyStream ->
                    DataOutputStream(BufferedOutputStream(FileOutputStream(postOut), 1 shl 16)).use { postStream ->
                        var current: String? = null
                        val ids = IntList()
                        var merged = 0
                        while (queue.isNotEmpty()) {
                            val cursor = queue.poll()
                            if (current != null && cursor.token != current) {
                                // A token and its postings: key bytes first, then the id range.
                                val bytes = current.toByteArray(StandardCharsets.UTF_8)
                                keyStream.write(bytes)
                                keyBytes += bytes.size
                                keyOffsets.add(keyBytes.toInt())
                                var previous = -1
                                for (i in 0 until ids.size) {
                                    val id = ids[i]
                                    if (id != previous) {
                                        postStream.writeInt(id)
                                        postCount++
                                        previous = id
                                    }
                                }
                                postOffsets.add(postCount)
                                ids.clear()
                                merged++
                                if (merged % 5000 == 0) {
                                    onProgress(
                                        TranslationProgress(
                                            title,
                                            0.75f + (merged.toFloat() / maxOf(1, cursor.totalTokens)) * 0.24f,
                                            true,
                                        ),
                                    )
                                }
                            }
                            current = cursor.token
                            ids.add(cursor.id)
                            if (cursor.advance()) queue.add(cursor)
                        }
                        if (current != null) {
                            val bytes = current.toByteArray(StandardCharsets.UTF_8)
                            keyStream.write(bytes)
                            keyBytes += bytes.size
                            keyOffsets.add(keyBytes.toInt())
                            var previous = -1
                            for (i in 0 until ids.size) {
                                val id = ids[i]
                                if (id != previous) {
                                    postStream.writeInt(id)
                                    postCount++
                                    previous = id
                                }
                            }
                            postOffsets.add(postCount)
                        }
                    }
                }

                writeIntArray(File(dir, DictFiles.INV_KEYOFF), keyOffsets.toArray())
                writeIntArray(File(dir, DictFiles.INV_POSTOFF), postOffsets.toArray())
                File(dir, DictFiles.INV_META).writeText(fingerprint(dictionary) ?: "", StandardCharsets.UTF_8)
                onProgress(TranslationProgress(title, 1f, building = false))
                return open(dictionary) ?: throw IllegalStateException("translation index could not be opened")
            } finally {
                runs.forEach { runCatching { it.delete() } }
            }
        }

        private const val CHUNK_PAIRS = 150_000
        private const val PROGRESS_EVERY = 20_000

        private fun fingerprint(dictionary: CompactDictionary): String? {
            val data = dictionary.definitionsFile() ?: return null
            if (!data.isFile) return null
            return buildString {
                append("format=").append(INDEX_FORMAT).append('\n')
                append("size=").append(data.length()).append('\n')
                append("modified=").append(data.lastModified()).append('\n')
                append("entries=").append(dictionary.entryCount).append('\n')
            }
        }

        private fun readIntArray(file: File): IntArray {
            val bytes = file.readBytes()
            require(bytes.size % 4 == 0) { "corrupt index array" }
            val out = IntArray(bytes.size / 4)
            for (i in out.indices) {
                out[i] = ((bytes[i * 4].toInt() and 0xFF) shl 24) or
                    ((bytes[i * 4 + 1].toInt() and 0xFF) shl 16) or
                    ((bytes[i * 4 + 2].toInt() and 0xFF) shl 8) or
                    (bytes[i * 4 + 3].toInt() and 0xFF)
            }
            return out
        }

        private fun writeIntArray(file: File, values: IntArray) {
            DataOutputStream(BufferedOutputStream(FileOutputStream(file), 1 shl 16)).use { out ->
                for (value in values) out.writeInt(value)
            }
        }

        /** A growable int array, to keep the merge free of boxing. */
        private class IntList {
            private var data = IntArray(1024)
            var size = 0
                private set

            operator fun get(index: Int): Int = data[index]

            fun add(value: Int) {
                if (size == data.size) data = data.copyOf(maxOf(1024, data.size * 2))
                data[size++] = value
            }

            fun clear() {
                size = 0
            }

            fun toArray(): IntArray = data.copyOf(size)
        }

        /** A run file read in token order, one record at a time. */
        private class RunCursor(file: File) : Closeable {
            private val input = DataInputStream(BufferedInputStream(FileInputStream(file), 1 shl 16))
            var token: String = ""
                private set
            var id: Int = 0
                private set
            var totalTokens: Int = 0
                private set

            /** Reads the next record; false at the end of the run. */
            fun advance(): Boolean = try {
                val length = input.readInt()
                val bytes = ByteArray(length)
                input.readFully(bytes)
                token = String(bytes, StandardCharsets.UTF_8)
                id = input.readInt()
                totalTokens++
                true
            } catch (e: Exception) {
                close()
                false
            }

            override fun close() {
                runCatching { input.close() }
            }
        }
    }
}
