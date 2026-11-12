package dev.yabai.ocr.core.ppocr

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OnnxValue
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import dev.yabai.ocr.core.layout.BlockColumns
import dev.yabai.ocr.core.lens.PixelBox
import java.io.File
import java.nio.FloatBuffer
import kotlin.system.measureTimeMillis

/**
 * PP-OCRv6_manga on ONNX Runtime: `manga_det` finds the text lines, `manga_rec` reads each of them.
 *
 * This is the engine that replaced manga-ocr as the app's offline reader. It is a different design
 * from it: detection is a DB probability map (not a fixed 1024x1024 box list) and recognition is
 * CTC over a 19,053-symbol dictionary (not an autoregressive decoder), so one balloon costs a single
 * forward pass instead of one pass per character.
 *
 * Everything here is pure JVM — no Android, no OpenCV: the DB post-processing lives in
 * [PPOcrPostprocess] and the tensor contracts in [PPOcrPreprocess], both testable without a model.
 */
class PPOcrEngine private constructor(
    private val env: OrtEnvironment,
    private val detSession: OrtSession,
    private val recSession: OrtSession,
    vocabLines: List<String>,
) : AutoCloseable {

    private val vocabulary: List<String> = PPOcrDecoding.vocabulary(vocabLines)

    constructor(detectorFile: File, recognizerFile: File, dictionaryFile: File) : this(
        OrtEnvironment.getEnvironment(),
        OrtEnvironment.getEnvironment().createSession(detectorFile.absolutePath),
        OrtEnvironment.getEnvironment().createSession(recognizerFile.absolutePath),
        dictionaryFile.readLines(),
    )

    constructor(detectorBytes: ByteArray, recognizerBytes: ByteArray, vocabLines: List<String>) : this(
        OrtEnvironment.getEnvironment(),
        OrtEnvironment.getEnvironment().createSession(detectorBytes),
        OrtEnvironment.getEnvironment().createSession(recognizerBytes),
        vocabLines,
    )

    fun recognizePixels(pixels: IntArray, width: Int, height: Int): PPOcrOutcome {
        if (width <= 0 || height <= 0 || pixels.isEmpty()) {
            return PPOcrOutcome(text = "", lines = emptyList(), elapsedMs = 0)
        }

        var blocks: List<PPOcrBlock> = emptyList()
        var detected: List<PPOcrLine> = emptyList()

        val elapsed = measureTimeMillis {
            detected = detect(pixels, width, height).mapNotNull { box ->
                val text = recognizeLine(pixels, width, height, box)
                if (text.isBlank()) null else box.copy(text = text)
            }

            val pixelBoxes = detected.map { PixelBox(it.left, it.top, it.width, it.height) }
            blocks = BlockColumns.groups(pixelBoxes).map { indices ->
                val group = indices.map { detected[it] }
                PPOcrBlock(
                    text = group.joinToString("") { it.text },
                    lines = group.map { it.text },
                    left = group.minOf { it.left },
                    top = group.minOf { it.top },
                    right = group.maxOf { it.right },
                    bottom = group.maxOf { it.bottom },
                    pieces = group,
                )
            }
        }

        return PPOcrOutcome(
            text = blocks.joinToString("\n") { it.text },
            lines = detected.map { it.text },
            elapsedMs = elapsed,
            blocks = blocks,
            detected = detected,
        )
    }

    /** Runs the detector and maps the probability map back to line boxes of the original image. */
    private fun detect(pixels: IntArray, width: Int, height: Int): List<PPOcrLine> {
        val (targetWidth, targetHeight) = detectionSize(width, height)
        val input = PPOcrPreprocess.detectorInput(pixels, width, height, targetWidth, targetHeight)
        val name = detSession.inputNames.first()

        val map = OnnxTensor.createTensor(
            env,
            input,
            longArrayOf(1, 3, targetHeight.toLong(), targetWidth.toLong()),
        ).use { tensor ->
            detSession.run(mapOf(name to tensor)).use { outputs ->
                val tensorOut = firstTensor(outputs) ?: return emptyList()
                toFloatArray(tensorOut)
            }
        }

        return PPOcrPostprocess.boxes(
            map = map,
            mapWidth = targetWidth,
            mapHeight = targetHeight,
            originalWidth = width,
            originalHeight = height,
        )
    }

    /** Reads one line, turning vertical text upright first. */
    private fun recognizeLine(pixels: IntArray, width: Int, height: Int, box: PPOcrLine): String {
        val boxWidth = box.width.coerceAtLeast(1)
        val boxHeight = box.height.coerceAtLeast(1)
        val crop = IntArray(boxWidth * boxHeight)
        for (y in 0 until boxHeight) {
            val sourceY = (box.top + y).coerceIn(0, height - 1)
            for (x in 0 until boxWidth) {
                val sourceX = (box.left + x).coerceIn(0, width - 1)
                crop[y * boxWidth + x] = pixels[sourceY * width + sourceX]
            }
        }

        val prepared = PPOcrPreprocess.recognizerInput(crop, boxWidth, boxHeight)
        val name = recSession.inputNames.first()

        val logits = OnnxTensor.createTensor(
            env,
            prepared.buffer,
            longArrayOf(1, 3, prepared.height.toLong(), prepared.width.toLong()),
        ).use { tensor ->
            recSession.run(mapOf(name to tensor)).use { outputs ->
                val tensorOut = firstTensor(outputs) ?: return ""
                toFloatArray(tensorOut)
            }
        }

        return PPOcrDecoding.decode(logits, vocabulary, classesPerStep = vocabulary.size)
    }

    private fun firstTensor(outputs: OrtSession.Result): OnnxTensor? {
        for ((_, value) in outputs) {
            if (value is OnnxTensor) return value
        }
        return outputs.get(0) as? OnnxTensor
    }

    private fun toFloatArray(tensor: OnnxTensor): FloatArray {
        val buffer: FloatBuffer = runCatching { tensor.floatBuffer }.getOrNull() ?: return FloatArray(0)
        val array = FloatArray(buffer.capacity())
        buffer.rewind()
        buffer.get(array)
        return array
    }

    override fun close() {
        runCatching { detSession.close() }
        runCatching { recSession.close() }
    }
}

/**
 * CTC greedy decoding of the recogniser output, kept apart from the ONNX session.
 *
 * The model answers `[1, T, V]` logits and the vocabulary is `blank` (0), then the dictionary in
 * file order, then a space. Greedy decoding takes the best class at each step, drops the blanks and
 * collapses a class repeated in consecutive steps — a character printed twice still gives one
 * glyph, which is why the collapse cannot be skipped.
 */
object PPOcrDecoding {

    /** `blank` + every dictionary line + the trailing space, exactly like the reference. */
    fun vocabulary(lines: List<String>): List<String> =
        listOf("blank") + lines.map { it.trimEnd('\r', '\n') } + listOf(" ")

    /** [logits] is the flat `T x V` output; [classesPerStep] is `V`. */
    fun decode(logits: FloatArray, vocabulary: List<String>, classesPerStep: Int): String {
        if (classesPerStep <= 0 || logits.isEmpty()) return ""
        val steps = logits.size / classesPerStep
        val indices = IntArray(steps) { step ->
            val offset = step * classesPerStep
            var bestIndex = 0
            var bestValue = Float.NEGATIVE_INFINITY
            for (index in 0 until classesPerStep) {
                val value = logits[offset + index]
                if (value > bestValue) {
                    bestValue = value
                    bestIndex = index
                }
            }
            bestIndex
        }
        return decodeIndices(indices, vocabulary)
    }

    fun decodeIndices(indices: IntArray, vocabulary: List<String>): String {
        val builder = StringBuilder()
        var previous = -1
        for (index in indices) {
            if (index != PPOcrSpec.CTC_BLANK && index != previous && index in vocabulary.indices) {
                builder.append(vocabulary[index])
            }
            previous = index
        }
        return builder.toString()
    }
}
