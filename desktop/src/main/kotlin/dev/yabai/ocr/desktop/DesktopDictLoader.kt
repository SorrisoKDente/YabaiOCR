package dev.yabai.ocr.desktop

import dev.yabai.ocr.core.dict.CompactDictionary
import dev.yabai.ocr.core.dict.DictFiles
import dev.yabai.ocr.core.dict.LookupEngine
import java.io.File

object DesktopDictLoader {
    private var cachedKey: String? = null
    private var cached: LookupEngine? = null

    fun loadEngine(settings: DesktopSettings): LookupEngine? {
        val files = enabledFiles(settings)
        if (files.isEmpty()) return null
        val dicts = files.mapNotNull { file -> runCatching { CompactDictionary.open(file) }.getOrNull() }
        return if (dicts.isEmpty()) null else LookupEngine(dicts)
    }

    /**
     * The engine kept open between windows.
     *
     * Opening the dictionaries is the slow part of a lookup — the reader has to map each `.yabai`
     * file — and the picker needs one right after a capture, when the user is waiting. The engine
     * is reused until the folder, the enabled set, the priority order or one of the files changes;
     * then it is closed and reopened, so a dictionary disabled in the manager never answers from a
     * stale engine.
     *
     * Only the picker uses this; windows that own their lookup keep [loadEngine] and are free to
     * close their engine when they close.
     */
    @Synchronized
    fun cachedEngine(settings: DesktopSettings): LookupEngine? {
        val files = enabledFiles(settings)
        val key = buildString {
            append(settings.dictDirectory().absolutePath)
            append('|')
            append(settings.getDisabledDictionaries().sorted().joinToString(","))
            append('|')
            append(settings.getDictionaryOrder().joinToString(","))
            for (file in files) {
                append('|')
                append(file.name)
                append(':')
                append(file.length())
                append(':')
                append(file.lastModified())
            }
        }
        if (key == cachedKey) return cached

        cached?.close()
        val dicts = files.mapNotNull { file -> runCatching { CompactDictionary.open(file) }.getOrNull() }
        cached = if (dicts.isEmpty()) null else LookupEngine(dicts)
        cachedKey = key
        return cached
    }

    @Synchronized
    fun closeCached() {
        cached?.close()
        cached = null
        cachedKey = null
    }

    /**
     * A dictionary's name, read from its own `meta.json`.
     *
     * Deliberately not [CompactDictionary.open]: opening a compiled dictionary reads its whole key
     * index into memory — 69 MB across the five dictionaries of a real Steam Deck install, measured
     * there — and a name is all these screens need. The hover card lists the dictionaries on every
     * composition, so that read happened once per hover, on the UI thread, while the card was being
     * drawn (see ROADMAP D81).
     *
     * Null when [entry] is not a compiled dictionary, has no readable metadata, or was compiled
     * without a title.
     */
    fun dictionaryTitle(entry: File): String? =
        runCatching { CompactDictionary.readMeta(File(entry, DictFiles.META)).title }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }

    /** Names of the dictionaries installed in [directory], in the order the folder lists them. */
    fun installedTitles(directory: File): List<String> {
        if (!directory.isDirectory) return emptyList()
        return directory.listFiles()
            ?.filter { it.extension == "yabai" || it.isDirectory }
            ?.mapNotNull(::dictionaryTitle)
            .orEmpty()
    }

    /** Names of the enabled dictionaries, in the priority order the settings ask for. */
    fun installedTitles(settings: DesktopSettings): List<String> =
        loadInstalledFiles(settings).mapNotNull(::dictionaryTitle)

    /**
     * Bytes a dictionary entry occupies on disk.
     *
     * A `.yabai` entry is a compiled dictionary: either one file or a directory holding the
     * compiled parts. `File.length()` answers 0 for a directory, which is why the manager showed
     * every installed dictionary as `0.0 MB`; a folder has to be walked to get the real total.
     */
    fun sizeBytes(entry: File): Long =
        if (entry.isDirectory) entry.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        else entry.length()

    fun loadInstalledFiles(settings: DesktopSettings): List<File> {
        val dir = settings.dictDirectory()
        if (!dir.exists()) dir.mkdirs()
        val files = dir.listFiles()?.filter { it.extension == "yabai" || it.isDirectory }.orEmpty()
        val order = settings.getDictionaryOrder()
        return files.sortedWith(Comparator { a, b ->
            val indexA = order.indexOf(a.name).let { if (it < 0) Int.MAX_VALUE else it }
            val indexB = order.indexOf(b.name).let { if (it < 0) Int.MAX_VALUE else it }
            if (indexA != indexB) indexA.compareTo(indexB) else a.name.compareTo(b.name)
        })
    }

    /** Files that may answer a lookup, in priority order, with disabled dictionaries left out. */
    private fun enabledFiles(settings: DesktopSettings): List<File> {
        val dir = settings.dictDirectory()
        if (!dir.isDirectory) return emptyList()

        val files = dir.listFiles()?.filter { it.extension == "yabai" || it.isDirectory }.orEmpty()
        if (files.isEmpty()) return emptyList()

        val disabled = settings.getDisabledDictionaries()
        val order = settings.getDictionaryOrder()
        val enabled = files.filter { !disabled.contains(it.name) }
        return enabled.sortedWith(Comparator { a, b ->
            val indexA = order.indexOf(a.name).let { if (it < 0) Int.MAX_VALUE else it }
            val indexB = order.indexOf(b.name).let { if (it < 0) Int.MAX_VALUE else it }
            if (indexA != indexB) indexA.compareTo(indexB) else a.name.compareTo(b.name)
        })
    }
}
