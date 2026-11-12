package dev.yabai.ocr.core.manga

import java.io.File

/**
 * Manages access to ONNX model files and tokenizer vocabulary on disk.
 */
class MangaOcrModelStore(
    val baseDir: File,
) {
    val detectorFile: File get() = File(baseDir, DETECTOR_FILENAME)
    val recognizerFile: File get() = File(baseDir, RECOGNIZER_FILENAME)
    val vocabFile: File get() = File(baseDir, VOCAB_FILENAME)

    /** Returns true if all required Manga-OCR model files are downloaded and available. */
    fun isAvailable(): Boolean {
        return detectorFile.exists() && detectorFile.length() > 0 &&
                recognizerFile.exists() && recognizerFile.length() > 0 &&
                vocabFile.exists() && vocabFile.length() > 0
    }

    /** Ensure directory exists. */
    fun ensureDirectory(): File {
        if (!baseDir.exists()) {
            baseDir.mkdirs()
        }
        return baseDir
    }

    companion object {
        const val DETECTOR_FILENAME = "comic_text_detector.onnx"
        const val RECOGNIZER_FILENAME = "manga_ocr.onnx"
        const val VOCAB_FILENAME = "vocab.txt"

        const val DETECTOR_URL = "https://huggingface.co/mayocream/comic-text-detector-onnx/resolve/main/comic-text-detector.onnx"
        const val RECOGNIZER_URL = "https://huggingface.co/dhleong/manga-ocr-android/resolve/main/manga-ocr.quant.onnx"
        const val VOCAB_URL = "https://huggingface.co/mayocream/manga-ocr-onnx/resolve/main/vocab.txt"
    }
}
