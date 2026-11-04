package dev.yabai.ocr.desktop

import dev.yabai.ocr.core.dict.CompactDictionary
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
