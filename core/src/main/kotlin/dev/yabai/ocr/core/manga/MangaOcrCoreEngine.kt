package dev.yabai.ocr.core.manga

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import dev.yabai.ocr.core.layout.BlockColumns
import dev.yabai.ocr.core.lens.PixelBox
import java.io.File
import kotlin.system.measureTimeMillis

data class MangaOcrPiece(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

data class MangaOcrBlock(
    val text: String,
    val lines: List<String>,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val pieces: List<MangaOcrPiece> = emptyList(),
)

data class MangaOcrOutcome(
    val text: String,
    val lines: List<String>,
    val engineLabel: String = "Manga-OCR (Offline)",
    val elapsedMs: Long,
    val blocks: List<MangaOcrBlock> = emptyList(),
)

/**
 * Pure JVM ONNX Manga-OCR engine combining Comic-Text-Detector and ViT recognition transformer.
 */
class MangaOcrCoreEngine private constructor(
    private val detSession: OrtSession,
    private val recSession: OrtSession,
    vocabLines: List<String>,
) : AutoCloseable {

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val detector: ComicTextDetector = ComicTextDetector(env, detSession)
    private val tokenizer: MangaOcrTokenizer = MangaOcrTokenizer.fromLines(vocabLines)

    constructor(
        detModelPath: String,
        recModelPath: String,
        vocabLines: List<String>,
    ) : this(
        OrtEnvironment.getEnvironment().createSession(detModelPath),
        OrtEnvironment.getEnvironment().createSession(recModelPath),
        vocabLines,
    )

    constructor(
        detModelBytes: ByteArray,
        recModelBytes: ByteArray,
        vocabLines: List<String>,
    ) : this(
        OrtEnvironment.getEnvironment().createSession(detModelBytes),
        OrtEnvironment.getEnvironment().createSession(recModelBytes),
        vocabLines,
    )

    constructor(
        detectorFile: File,
        recognizerFile: File,
        vocabFile: File,
    ) : this(
        detectorFile.absolutePath,
        recognizerFile.absolutePath,
        vocabFile.readLines(),
    )

    private data class IntermediateBox(
        val text: String,
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
    )

    fun recognizePixels(
        pixels: IntArray,
        width: Int,
        height: Int,
    ): MangaOcrOutcome {
        if (width <= 0 || height <= 0 || pixels.isEmpty()) {
            return MangaOcrOutcome(text = "", lines = emptyList(), elapsedMs = 0)
        }

        var blocks: List<MangaOcrBlock> = emptyList()
        val elapsed = measureTimeMillis {
            val detectedBoxes = detector.detect(pixels, width, height)

            val intermediateBoxes = detectedBoxes.mapNotNull { box ->
                val boxW = (box.right - box.left).coerceAtLeast(1)
                val boxH = (box.bottom - box.top).coerceAtLeast(1)

                val cropPixels = IntArray(boxW * boxH)
                for (cy in 0 until boxH) {
                    val sy = (box.top + cy).coerceIn(0, height - 1)
                    for (cx in 0 until boxW) {
                        val sx = (box.left + cx).coerceIn(0, width - 1)
                        cropPixels[cy * boxW + cx] = pixels[sy * width + sx]
                    }
                }

                val text = recognizeCrop(cropPixels, boxW, boxH)
                if (text.isNotBlank()) {
                    IntermediateBox(
                        text = text,
                        left = box.left,
                        top = box.top,
                        right = box.right,
                        bottom = box.bottom,
                    )
                } else null
            }

            val pixelBoxes = intermediateBoxes.map {
                PixelBox(x = it.left, y = it.top, width = it.right - it.left, height = it.bottom - it.top)
            }

            val groupIndices = BlockColumns.groups(pixelBoxes)

            blocks = groupIndices.map { indices ->
                val groupBoxes = indices.map { intermediateBoxes[it] }
                val groupLeft = groupBoxes.minOf { it.left }
                val groupTop = groupBoxes.minOf { it.top }
                val groupRight = groupBoxes.maxOf { it.right }
                val groupBottom = groupBoxes.maxOf { it.bottom }

                val pieces = groupBoxes.map {
                    MangaOcrPiece(
                        text = it.text,
                        left = it.left,
                        top = it.top,
                        right = it.right,
                        bottom = it.bottom,
                    )
                }

                val joinedText = groupBoxes.joinToString("") { it.text }
                val lines = groupBoxes.map { it.text }

                MangaOcrBlock(
                    text = joinedText,
                    lines = lines,
                    left = groupLeft,
                    top = groupTop,
                    right = groupRight,
                    bottom = groupBottom,
                    pieces = pieces,
                )
            }
        }

        val fullText = blocks.joinToString("\n") { it.text }
        val allLines = blocks.flatMap { it.lines }

        return MangaOcrOutcome(
            text = fullText,
            lines = allLines,
            elapsedMs = elapsed,
            blocks = blocks,
        )
    }

    private fun recognizeCrop(cropPixels: IntArray, cropW: Int, cropH: Int): String {
        val floatBuffer = MangaOcrPreprocessor.prepareRecognitionInput(cropPixels, cropW, cropH)

        val inputName = recSession.inputNames.iterator().next()
        val inputTensor = OnnxTensor.createTensor(
            env,
            floatBuffer,
            longArrayOf(1, 3, 224, 224)
        )

        return inputTensor.use { tensor ->
            recSession.run(mapOf(inputName to tensor)).use { outputMap ->
                val outputTensor = outputMap.get(0) as? OnnxTensor ?: return ""
                val tokenIds = extractTokenIds(outputTensor)
                tokenizer.decode(tokenIds)
            }
        }
    }

    private fun extractTokenIds(tensor: OnnxTensor): List<Int> {
        val value = tensor.value
        if (value is Array<*>) {
            val first = value.firstOrNull() ?: return emptyList()
            if (first is Array<*>) {
                @Suppress("UNCHECKED_CAST")
                val batch = value as Array<Array<FloatArray>>
                if (batch.isEmpty()) return emptyList()
                val sequence = batch[0]
                return sequence.map { logits ->
                    var maxIdx = 0
                    var maxVal = Float.NEGATIVE_INFINITY
                    for (i in logits.indices) {
                        if (logits[i] > maxVal) {
                            maxVal = logits[i]
                            maxIdx = i
                        }
                    }
                    maxIdx
                }
            } else if (first is LongArray) {
                return first.map { it.toInt() }
            } else if (first is IntArray) {
                return first.toList()
            }
        } else if (value is LongArray) {
            return value.map { it.toInt() }
        } else if (value is IntArray) {
            return value.toList()
        }
        return emptyList()
    }

    override fun close() {
        runCatching { detSession.close() }
        runCatching { recSession.close() }
    }
}
