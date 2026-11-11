package dev.yabai.ocr.core.dict

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.stream.JsonReader
import java.io.BufferedOutputStream
import java.io.File
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipFile
import kotlin.math.min

/** Growable int array: avoids boxing a million entries while compiling a dictionary. */
internal class IntVec(initial: Int = 1024) {
    var data = IntArray(initial)
        private set
    var size = 0
        private set

    fun add(value: Int) {
        if (size == data.size) data = data.copyOf(data.size * 2)
        data[size++] = value
    }

    fun toArray(): IntArray = data.copyOf(size)
}

internal class ByteVec(initial: Int = 1024) {
    var data = ByteArray(if (initial > 0) initial else 1)
        private set
    var size = 0
        private set

    fun add(value: Byte) {
        ensure(1)
        data[size++] = value
    }

    fun add(bytes: ByteArray) = add(bytes, 0, bytes.size)

    fun add(bytes: ByteArray, offset: Int, length: Int) {
        if (length <= 0) return
        ensure(length)
        System.arraycopy(bytes, offset, data, size, length)
        size += length
    }

    /** Copies the bytes a key occupies out of a shared buffer. */
    fun addRange(source: ByteArray, offset: Int, end: Int) = add(source, offset, end - offset)

    private fun ensure(extra: Int) {
        if (size + extra <= data.size) return
        var capacity = data.size
        while (capacity < size + extra) capacity = capacity shl 1
        data = data.copyOf(capacity)
    }

    fun toArray(): ByteArray = data.copyOf(size)
}

/**
 * Comparison of two keys held as byte ranges in one shared buffer.
 *
 * Comparing UTF-8 bytes is exactly comparing code points, which is the order the reader binary
 * searches in. (Plain [String.compareTo] would be wrong: it compares UTF-16 units, which puts
 * supplementary-plane kanji below the U+E000 block, the opposite of their byte order.)
 */
internal fun compareKeyRange(data: ByteArray, offsets: IntArray, a: Int, b: Int): Int {
    var i = offsets[a]
    val endA = offsets[a + 1]
    var j = offsets[b]
    val endB = offsets[b + 1]
    val common = min(endA - i, endB - j)
    var k = 0
    while (k < common) {
        val x = data[i].toInt() and 0xFF
        val y = data[j].toInt() and 0xFF
        if (x != y) return x - y
        i++
        j++
        k++
    }
    return (endA - offsets[a]) - (endB - offsets[b])
}

/**
 * Turns a Yomitan/Yomichan dictionary ZIP into the compact, randomly readable form this app uses.
 *
 * The ZIP layout is `index.json` plus a pile of `term_bank_N.json` / `kanji_bank_N.json` files, each
 * an array of arrays. Those are parseable but useless for lookup: you cannot search 15 MB of JSON
 * one word at a time without reparsing it. So the whole thing is rewritten once, at import time,
 * into a sorted key index plus a blob file, which is what the lookup path actually reads.
 *
 * Definitions are rendered from Yomitan's structured-content tree to plain text right here, so the
 * lookup path never touches JSON again.
 */
class DictionaryCompiler(
    private val zipFile: File,
    private val outputDir: File,
    private val sourceName: String,
) {

    fun interface Progress {
        fun onProgress(phase: Phase, done: Int, total: Int)
    }

    enum class Phase { READING, INDEXING }

    /** Thrown when the archive is not a dictionary this app can use. */
    class NotADictionaryException(message: String) : Exception(message)

    /**
     * @param shouldCancel polled between records; compilation aborts when it returns true.
     */
    fun compile(
        progress: Progress? = null,
        shouldCancel: () -> Boolean = { false },
    ): DictionaryInfo {
        outputDir.mkdirs()
        val terms = BankAccumulator(File(outputDir, DictFiles.TERM_DAT))
        val kanji = BankAccumulator(File(outputDir, DictFiles.KANJI_DAT))
        var indexJson: IndexJson? = null
        var success = false
        try {
            ZipFile(zipFile).use { zip ->
                val names = zip.entries().asSequence().map { it.name }.toList()
                indexJson = readIndexJson(zip, names)

                for (name in bankNames(names, "term_bank_")) {
                    readBank(zip, name, terms, progress, shouldCancel, kanji = false)
                }
                for (name in bankNames(names, "kanji_bank_")) {
                    readBank(zip, name, kanji, progress, shouldCancel, kanji = true)
                }
            }

            val header = indexJson ?: throw NotADictionaryException("Arquivo inválido.")

            if (terms.entryCount == 0 && kanji.entryCount == 0) {
                throw NotADictionaryException(
                    "Nenhum verbete encontrado. O arquivo precisa ser um dicionário no formato " +
                        "Yomitan/Yomichan (index.json + term_bank_*.json). Dicionários só de " +
                        "frequência ou de nomes não servem.",
                )
            }

            progress?.onProgress(Phase.INDEXING, 0, terms.keyCount + kanji.keyCount)
            writeTermIndex(terms)
            writeKanjiIndex(kanji)

            val info = DictionaryInfo(
                title = header.title.ifBlank { sourceName },
                revision = header.revision,
                format = header.format,
                author = header.author,
                url = header.url,
                description = header.description,
                attribution = header.attribution,
                termCount = terms.entryCount,
                kanjiCount = kanji.entryCount,
            )
            writeMeta(info)
            success = true
            return info
        } finally {
            terms.close()
            kanji.close()
            // A failed import must not leave a half-built dictionary behind for the reader.
            if (!success) outputDir.deleteRecursively()
        }
    }

    // --- reading -------------------------------------------------------------------------------

    private class IndexJson(
        val title: String,
        val revision: String,
        val format: Int,
        val author: String?,
        val url: String?,
        val description: String?,
        val attribution: String?,
    )

    private fun readIndexJson(zip: ZipFile, names: List<String>): IndexJson {
        val name = names.firstOrNull { it.substringAfterLast('/') == "index.json" }
            ?: throw NotADictionaryException("O arquivo não tem index.json, então não é um dicionário Yomitan.")
        val text = zip.getInputStream(zip.getEntry(name)).use {
            it.readBytes().toString(StandardCharsets.UTF_8)
        }
        val obj = JsonParser.parseString(text).asJsonObject
        return IndexJson(
            title = obj.get("title")?.asString.orEmpty(),
            revision = obj.get("revision")?.asString.orEmpty(),
            format = obj.get("format")?.asInt ?: 3,
            author = obj.get("author")?.asString,
            url = obj.get("url")?.asString,
            description = obj.get("description")?.asString,
            attribution = obj.get("attribution")?.asString,
        )
    }

    /** Bank names sorted by their numeric suffix, so entry ids are stable across imports. */
    private fun bankNames(names: List<String>, prefix: String): List<String> =
        names.filter { name ->
            val base = name.substringAfterLast('/')
            base.startsWith(prefix) && base.endsWith(".json")
        }.sortedBy { name ->
            name.substringAfterLast('/').removePrefix(prefix).removeSuffix(".json").toIntOrNull()
                ?: Int.MAX_VALUE
        }

    /**
     * Holds the blob stream plus the keys collected so far for one bank family.
     *
     * Keys are kept as raw UTF-8 in one growable buffer rather than as [String]s. A large dictionary
     * has well over a million readings, and a million separate String objects is the difference
     * between an import that finishes on a phone and one that runs out of heap half way through.
     */
    private class BankAccumulator(blobFile: File) {
        private val raw: OutputStream = BufferedOutputStream(blobFile.outputStream(), 1 shl 16)
        private val offsets = IntVec(1 shl 16).apply { add(0) }
        private val flags = ByteVec(1 shl 16)

        var entryCount = 0
            private set
        private var written: Long = 0L

        /** Concatenated UTF-8 of every key; key `i` is `keyOffsets[i] until keyOffsets[i + 1]`. */
        val keys = ByteVec(1 shl 18)

        /** One offset per key plus the trailing end offset. */
        val keyOffsets = IntVec(1 shl 16).apply { add(0) }
        val refs = IntVec(1 shl 16)

        val keyCount: Int get() = keyOffsets.size - 1

        fun writeBlob(score: Int, a: String, b: String, c: String, body: String, rulesFlags: Int) {
            val id = entryCount++
            val bytes = body.toByteArray(StandardCharsets.UTF_8)
            val bytesA = a.toByteArray(StandardCharsets.UTF_8)
            val bytesB = b.toByteArray(StandardCharsets.UTF_8)
            val bytesC = c.toByteArray(StandardCharsets.UTF_8)
            val added = 4 + 2 + bytesA.size.coerceAtMost(0xFFFF) +
                2 + bytesB.size.coerceAtMost(0xFFFF) +
                2 + bytesC.size.coerceAtMost(0xFFFF) + bytes.size

            if (written + added > Int.MAX_VALUE) {
                throw NotADictionaryException("O dicionário excede o limite suportado de 2 GB.")
            }

            written += writeInt(raw, score)
            written += writeString(raw, a)
            written += writeString(raw, b)
            written += writeString(raw, c)
            raw.write(bytes)
            written += bytes.size
            offsets.add(written.toInt())
            // One flag byte per *entry*: the reader indexes this by entry id, so appending it next
            // to the key instead would shift every value as soon as a word has a second reading.
            flags.add(rulesFlags.toByte())
            check(id == flags.size - 1) { "entry id and flag index drifted apart" }
        }

        fun addKey(key: String, entryId: Int) {
            keys.add(key.toByteArray(StandardCharsets.UTF_8))
            keyOffsets.add(keys.size)
            refs.add(entryId)
        }

        fun close() {
            runCatching { raw.flush() }
            runCatching { raw.close() }
        }

        fun offsetArray(): IntArray = offsets.toArray()
        fun flagArray(): ByteArray = flags.toArray()

        private fun writeInt(out: OutputStream, value: Int): Int {
            out.write(value ushr 24)
            out.write(value ushr 16)
            out.write(value ushr 8)
            out.write(value)
            return 4
        }

        private fun writeString(out: OutputStream, value: String): Int {
            val bytes = value.toByteArray(StandardCharsets.UTF_8)
            val length = min(bytes.size, 0xFFFF)
            out.write(length ushr 8)
            out.write(length)
            out.write(bytes, 0, length)
            return 2 + length
        }
    }

    private fun readBank(
        zip: ZipFile,
        name: String,
        accumulator: BankAccumulator,
        progress: Progress?,
        shouldCancel: () -> Boolean,
        kanji: Boolean,
    ) {
        val entry = zip.getEntry(name) ?: return
        zip.getInputStream(entry).bufferedReader(StandardCharsets.UTF_8).use { reader ->
            val json = JsonReader(reader)
            json.beginArray()
            var processed = 0
            while (json.hasNext()) {
                if (shouldCancel()) throw InterruptedException("import cancelado")
                // Parsing one record at a time keeps a single record's tree in memory, not the bank's.
                val record = JsonParser.parseReader(json)
                if (record.isJsonArray) {
                    val array = record.asJsonArray
                    if (kanji) readKanjiRecord(array, accumulator) else readTermRecord(array, accumulator)
                }
                processed++
                if (processed % 2000 == 0) progress?.onProgress(Phase.READING, accumulator.entryCount, -1)
            }
            json.endArray()
        }
    }

    /** `[term, reading, definitionTags, rules, score, glossary[], sequence, termTags]` */
    private fun readTermRecord(record: JsonArray, accumulator: BankAccumulator) {
        if (record.size() < 6) return
        val term = record.stringAt(0) ?: return
        val reading = record.stringAt(1).orEmpty()
        val tags = record.stringAt(2).orEmpty()
        val rules = record.stringAt(3).orEmpty()
        val score = record.get(4)?.takeIf { it.isJsonPrimitive }?.asString?.toIntOrNull() ?: 0

        if (term.isBlank() || term.length > 60) return

        val glossary = record.get(5)
        if (glossary == null || !glossary.isJsonArray) return
        val rendered = GlossaryRenderer.renderGlossary(glossary.asJsonArray)
        val definition = GlossaryRenderer.renderDefinition(rendered.blocks)
        if (definition.isBlank()) return

        val id = accumulator.entryCount
        val flags = Deinflector.flagsOf(rules)
        accumulator.writeBlob(
            score,
            term,
            reading,
            mergeTags(tags, rendered.tags),
            definition.takeClean(MAX_DEFINITION_CHARS),
            flags,
        )
        accumulator.addKey(term, id)
        if (reading.isNotBlank() && reading != term) accumulator.addKey(reading, id)
    }

    /** `[character, onyomi, kunyomi, tags, meanings[], stats{}]` */
    private fun readKanjiRecord(record: JsonArray, accumulator: BankAccumulator) {
        if (record.size() < 4) return
        val character = record.stringAt(0) ?: return
        if (character.isBlank() || character.length > 4) return

        val onyomi = record.stringAt(1).orEmpty()
        val kunyomi = record.stringAt(2).orEmpty()
        val tags = record.stringAt(3).orEmpty()
        val meanings = record.get(4)
        val stats = record.get(5)

        val body = buildString {
            if (onyomi.isNotBlank()) append("音: ").append(onyomi).append('\n')
            if (kunyomi.isNotBlank()) append("訓: ").append(kunyomi).append('\n')
            if (meanings != null && meanings.isJsonArray) {
                val list = meanings.asJsonArray.mapNotNull { it.takeIf { e -> e.isJsonPrimitive }?.asString }
                if (list.isNotEmpty()) append(list.joinToString("; ")).append('\n')
            }
            val notes = kanjiNotes(stats, tags)
            if (notes.isNotEmpty()) append(notes)
        }.trim()

        if (body.isBlank()) return

        val id = accumulator.entryCount
        accumulator.writeBlob(0, character, onyomi, kunyomi, body.takeClean(MAX_DEFINITION_CHARS), 0)
        accumulator.addKey(character, id)
    }

    private fun kanjiNotes(stats: JsonElement?, tags: String): String {
        val parts = ArrayList<String>(6)
        if (stats != null && stats.isJsonObject) {
            val obj = stats.asJsonObject
            fun pick(key: String, label: String) {
                val value = obj.get(key)?.takeIf { it.isJsonPrimitive }?.asString
                if (!value.isNullOrBlank()) parts.add("$label $value")
            }
            pick("grade", "série")
            pick("freq", "frequência")
            pick("jlpt", "JLPT")
            pick("strokeCount", "traços")
            pick("radical", "radical")
        }
        if (tags.isNotBlank()) parts.add(tags)
        return if (parts.isEmpty()) "" else parts.joinToString(" · ")
    }

    /**
     * The tags the record carries plus the ones the renderer found inside the definition, without
     * repeats: Jitendex keeps the part of speech only in the markup, JMdict only in the record (R19).
     */
    private fun mergeTags(record: String, rendered: String): String {
        val parts = (record.split(' ', ',', '・') + rendered.split('・'))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        return parts.joinToString("・")
    }
    private fun JsonArray.stringAt(index: Int): String? {
        val element = get(index) ?: return null
        if (!element.isJsonPrimitive) return null
        return element.asString
    }

    // --- indexing ------------------------------------------------------------------------------

    private fun writeTermIndex(accumulator: BankAccumulator) {
        writeIndex(
            accumulator = accumulator,
            keysFile = File(outputDir, DictFiles.TERM_KEYS),
            keyOffFile = File(outputDir, DictFiles.TERM_KEYOFF),
            keyStartFile = File(outputDir, DictFiles.TERM_KEYSTART),
            refsFile = File(outputDir, DictFiles.TERM_REFS),
        )
        File(outputDir, DictFiles.TERM_EFLAGS).writeBytes(accumulator.flagArray())
        writeIntArray(File(outputDir, DictFiles.TERM_ENTOFF), accumulator.offsetArray())
    }

    private fun writeKanjiIndex(accumulator: BankAccumulator) {
        writeIndex(
            accumulator = accumulator,
            keysFile = File(outputDir, DictFiles.KANJI_KEYS),
            keyOffFile = File(outputDir, DictFiles.KANJI_KEYOFF),
            keyStartFile = File(outputDir, DictFiles.KANJI_KEYSTART),
            refsFile = File(outputDir, DictFiles.KANJI_REFS),
        )
        writeIntArray(File(outputDir, DictFiles.KANJI_ENTOFF), accumulator.offsetArray())
    }

    /**
     * Writes the sorted, deduplicated key index.
     *
     * The reader expects keys in UTF-8 byte order with, for each distinct key, the ids of every entry
     * filed under it. Sorting is done over an index array so the key bytes themselves are never
     * copied, and equal keys are collapsed by a single scan afterwards.
     */
    private fun writeIndex(
        accumulator: BankAccumulator,
        keysFile: File,
        keyOffFile: File,
        keyStartFile: File,
        refsFile: File,
    ) {
        val keys = accumulator.keys.data
        val keyOffsets = accumulator.keyOffsets.data
        val refs = accumulator.refs.data
        val count = accumulator.keyCount

        val order = IntArray(count) { it }
        sortIndices(order) { a, b ->
            val byKey = compareKeyRange(keys, keyOffsets, a, b)
            if (byKey != 0) byKey else refs[a].compareTo(refs[b])
        }

        val keyBytes = ByteVec(1 shl 20)
        val outOffsets = IntVec(1 shl 14).apply { add(0) }
        val keyStarts = IntVec(1 shl 14).apply { add(0) }
        val outRefs = IntVec(1 shl 14)

        var i = 0
        while (i < count) {
            val first = order[i]
            keyBytes.addRange(keys, keyOffsets[first], keyOffsets[first + 1])

            var j = i
            while (j < count && compareKeyRange(keys, keyOffsets, order[j], first) == 0) {
                outRefs.add(refs[order[j]])
                j++
            }

            outOffsets.add(keyBytes.size)
            keyStarts.add(outRefs.size)
            i = j
        }

        keysFile.writeBytes(keyBytes.toArray())
        writeIntArray(keyOffFile, outOffsets.toArray())
        writeIntArray(keyStartFile, keyStarts.toArray())
        writeIntArray(refsFile, outRefs.toArray())
    }

    private fun writeMeta(info: DictionaryInfo) {
        val obj = JsonObject().apply {
            addProperty("title", info.title)
            addProperty("revision", info.revision)
            addProperty("format", info.format)
            addProperty("author", info.author)
            addProperty("url", info.url)
            addProperty("description", info.description)
            addProperty("attribution", info.attribution)
            addProperty("termCount", info.termCount)
            addProperty("kanjiCount", info.kanjiCount)
            addProperty("sourceName", sourceName)
            addProperty("compiledWith", FORMAT_VERSION)
        }
        File(outputDir, DictFiles.META).writeText(obj.toString(), StandardCharsets.UTF_8)
    }

    companion object {
        /**
         * Bumped when the on-disk layout changes — or when the text baked into it changes, as in R19
         * and R42: the definitions are rendered once, at import time, so an old copy cannot show the
         * new rendering until it is imported again. [DictionaryInfo.outdated] is what tells the user.
         */
        const val FORMAT_VERSION = 3

        private const val MAX_DEFINITION_CHARS = 6000

        internal fun String.takeClean(maxChars: Int): String {
            if (length <= maxChars) return this
            var count = maxChars
            if (Character.isHighSurrogate(this[count - 1])) {
                count--
            }
            return substring(0, count)
        }

        /** Bottom-up merge sort over an index array: no boxing, no comparator objects per swap. */
        private fun sortIndices(order: IntArray, compare: (Int, Int) -> Int) {
            val n = order.size
            if (n < 2) return
            var src = order
            var dst = IntArray(n)
            var width = 1
            while (width < n) {
                var i = 0
                while (i < n) {
                    val mid = min(i + width, n)
                    val end = min(i + 2 * width, n)
                    var a = i
                    var b = mid
                    var k = i
                    while (a < mid && b < end) {
                        dst[k++] = if (compare(src[a], src[b]) <= 0) src[a++] else src[b++]
                    }
                    while (a < mid) dst[k++] = src[a++]
                    while (b < end) dst[k++] = src[b++]
                    i += 2 * width
                }
                val swap = src
                src = dst
                dst = swap
                width = width shl 1
            }
            if (src !== order) System.arraycopy(src, 0, order, 0, n)
        }

        internal fun writeIntArray(file: File, values: IntArray) {
            val buffer = java.nio.ByteBuffer.allocate(values.size * 4)
            buffer.asIntBuffer().put(values)
            file.writeBytes(buffer.array())
        }

        internal fun readIntArray(file: File, expected: Int? = null): IntArray {
            val bytes = file.readBytes()
            val count = bytes.size / 4
            require(expected == null || expected == count) {
                "expected $expected ints in ${file.name}, found $count"
            }
            val buffer = java.nio.ByteBuffer.wrap(bytes)
            val out = IntArray(count)
            buffer.asIntBuffer().get(out)
            return out
        }
    }
}
