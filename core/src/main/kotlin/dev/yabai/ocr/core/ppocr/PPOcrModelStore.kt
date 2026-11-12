package dev.yabai.ocr.core.ppocr

import java.io.File

/**
 * Where the PP-OCRv6_manga files live and where they come from.
 *
 * The published FP32 exports are used on purpose: the pipeline totals ~23 MB instead of ~213 MB for
 * manga-ocr, and every ONNX Runtime CPU kernel is guaranteed to run them — the FP16 exports depend
 * on a CPU FP16 path that varies with the runtime build, and a failure to load would silently leave
 * the offline engine unusable.
 */
class PPOcrModelStore(
    val baseDir: File,
) {
    val detectorFile: File get() = File(baseDir, DETECTOR_FILENAME)
    val recognizerFile: File get() = File(baseDir, RECOGNIZER_FILENAME)
    val dictionaryFile: File get() = File(baseDir, DICTIONARY_FILENAME)

    fun isAvailable(): Boolean =
        detectorFile.length() > 0 && recognizerFile.length() > 0 && dictionaryFile.length() > 0

    fun ensureDirectory(): File {
        if (!baseDir.exists()) baseDir.mkdirs()
        return baseDir
    }

    companion object {
        const val DETECTOR_FILENAME = "manga_det.onnx"
        const val RECOGNIZER_FILENAME = "manga_rec.onnx"
        const val DICTIONARY_FILENAME = "ppocrv6_dict.txt"

        private const val BASE = "https://huggingface.co/Kellenok/PP-OCRv6_manga/resolve/main"

        const val DETECTOR_URL = "$BASE/det/manga_det.onnx"
        const val RECOGNIZER_URL = "$BASE/rec/manga_rec.onnx"
        const val DICTIONARY_URL = "$BASE/ppocrv6_dict.txt"

        /** Total download, for the settings line: 1.73 + 21.33 MB plus the 76 KB dictionary. */
        const val TOTAL_MEGABYTES = 23
    }
}
