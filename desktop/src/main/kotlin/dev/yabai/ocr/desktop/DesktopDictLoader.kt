package dev.yabai.ocr.desktop

import dev.yabai.ocr.core.dict.CompactDictionary
import dev.yabai.ocr.core.dict.LookupEngine
import java.io.File

object DesktopDictLoader {
    fun loadEngine(settings: DesktopSettings): LookupEngine? {
        val dir = settings.dictDirectory()
        if (!dir.isDirectory) return null

        val files = dir.listFiles()?.filter { it.extension == "yabai" || it.isDirectory }.orEmpty()
        if (files.isEmpty()) return null

        val disabled = settings.getDisabledDictionaries()
        val order = settings.getDictionaryOrder()

        val enabledFiles = files.filter { !disabled.contains(it.name) }
        if (enabledFiles.isEmpty()) return null

        val sortedFiles = enabledFiles.sortedWith(Comparator { a, b ->
            val indexA = order.indexOf(a.name).let { if (it < 0) Int.MAX_VALUE else it }
            val indexB = order.indexOf(b.name).let { if (it < 0) Int.MAX_VALUE else it }
            if (indexA != indexB) indexA.compareTo(indexB) else a.name.compareTo(b.name)
        })

        val dicts = sortedFiles.mapNotNull { file -> runCatching { CompactDictionary.open(file) }.getOrNull() }
        return if (dicts.isEmpty()) null else LookupEngine(dicts)
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
}
