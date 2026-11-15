package dev.yabai.ocr.desktop

import java.io.File
import java.util.Properties

/**
 * What a dictionary was installed from, kept next to its blobs as `signature.properties`.
 *
 * [releaseTag] only exists for GitHub release assets: it is what lets the update check compare two
 * publications of the same repository instead of the HTTP metadata of whichever URL happens to be
 * «latest». The installer writes this file and the update checker reads it — before, each side had
 * its own reader and they did not even agree on the path.
 */
internal data class DictionarySignature(
    val etag: String? = null,
    val lastModified: String? = null,
    val length: Long = 0L,
    val releaseTag: String? = null,
)

internal const val DICTIONARY_SIGNATURE_FILE = "signature.properties"

internal fun readDictionarySignature(dictionaryDir: File): DictionarySignature? {
    val file = File(dictionaryDir, DICTIONARY_SIGNATURE_FILE)
    if (!file.isFile) return null
    val props = Properties()
    runCatching { file.inputStream().use { props.load(it) } }.getOrElse { return null }
    return DictionarySignature(
        etag = props.getProperty("etag"),
        lastModified = props.getProperty("lastModified"),
        length = props.getProperty("length")?.toLongOrNull() ?: 0L,
        releaseTag = props.getProperty("releaseTag"),
    )
}

internal fun writeDictionarySignature(dictionaryDir: File, signature: DictionarySignature) {
    val props = Properties().apply {
        signature.etag?.let { setProperty("etag", it) }
        signature.lastModified?.let { setProperty("lastModified", it) }
        setProperty("length", signature.length.toString())
        signature.releaseTag?.let { setProperty("releaseTag", it) }
    }
    runCatching {
        File(dictionaryDir, DICTIONARY_SIGNATURE_FILE).outputStream()
            .use { props.store(it, "Yabai OCR Dictionary Signature") }
    }
}
