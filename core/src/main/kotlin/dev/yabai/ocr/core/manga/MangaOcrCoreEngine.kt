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

    /**
     * Runs the recogniser on one crop and returns what it read.
     *
     * The downloaded model is kha-white's manga-ocr exported with its decoder attached — two inputs
     * (`image`, `token_ids`) and one `logits` output — so it does **not** answer in a single pass:
     * each call returns the logits for every token fed so far, and only the last row is the next
     * token. Feeding it once produced a single argmax, usually `</s>`, which decodes to the empty
     * string: that is why nothing was ever recognised. [MangaOcrDecoding] runs the loop.
     */
    private fun recognizeCrop(cropPixels: IntArray, cropW: Int, cropH: Int): String {
        val floatBuffer = MangaOcrPreprocessor.prepareRecognitionInput(cropPixels, cropW, cropH)

        val names = recSession.inputNames.toList()
        val imageName = names.firstOrNull { it.contains("pixel", true) || it.contains("image", true) }
            ?: names.firstOrNull()
            ?: return ""
        val tokenName = names.firstOrNull { name ->
            name != imageName && (name.contains("token", true) || name.contains("decoder", true) || name.contains("id", true))
        }

        val imageTensor = OnnxTensor.createTensor(
            env,
            floatBuffer,
            longArrayOf(1, 3, RECOGNITION_SIZE.toLong(), RECOGNITION_SIZE.toLong()),
        )
        imageTensor.use { image ->
            // A model with a single input decodes the tokens itself (the "preprocessed" exports).
            if (tokenName == null) {
                recSession.run(mapOf(imageName to image)).use { outputMap ->
                    val tensor = outputTensor(outputMap) ?: return ""
                    return tokenizer.decode(extractTokenIds(tensor))
                }
            }

            val tokenIds = MangaOcrDecoding.generate { tokens ->
                nextToken(imageName, image, tokenName, tokens)
            }
            // 0..4 are the tokenizer's own controls; manga-ocr only ever prints 5 and above.
            return tokenizer.decode(tokenIds.filter { it >= MangaOcrDecoding.FIRST_TEXT_TOKEN_ID })
        }
    }

    /** One decoder step: the tokens read so far in, the next token out. */
    private fun nextToken(
        imageName: String,
        image: OnnxTensor,
        tokenName: String,
        tokens: LongArray,
    ): Int? {
        val tokenTensor = OnnxTensor.createTensor(
            env,
            java.nio.LongBuffer.wrap(tokens),
            longArrayOf(1, tokens.size.toLong()),
        )
        return tokenTensor.use { tokensInput ->
            recSession.run(mapOf(imageName to image, tokenName to tokensInput)).use { outputMap ->
                val logits = outputTensor(outputMap) ?: return null
                argmaxLastRow(logits)
            }
        }
    }

    private fun outputTensor(outputMap: OrtSession.Result): OnnxTensor? {
        for ((name, value) in outputMap) {
            if (name == "logits") return value as? OnnxTensor
        }
        return outputMap.get(0) as? OnnxTensor
    }

    /**
     * The token the decoder chose for the position it was asked about.
     *
     * `logits` is `[1, sequence, vocabulary]`; every row is a whole vocabulary of scores for the
     * position it sits at, and generation reads the last one.
     */
    private fun argmaxLastRow(tensor: OnnxTensor): Int? {
        val shape = tensor.info.shape
        if (shape.size < 2) return null
        val vocabulary = shape[shape.size - 1].toInt()
        val sequence = if (shape.size >= 3) shape[shape.size - 2].toInt() else 1
        if (vocabulary <= 0) return null

        val buffer = runCatching { tensor.floatBuffer }.getOrNull() ?: return null
        val offset = (sequence - 1).coerceAtLeast(0) * vocabulary
        if (offset + vocabulary > buffer.capacity()) return null

        var bestIndex = 0
        var bestValue = Float.NEGATIVE_INFINITY
        for (index in 0 until vocabulary) {
            val value = buffer.get(offset + index)
            if (value > bestValue) {
                bestValue = value
                bestIndex = index
            }
        }
        return bestIndex
    }

    private fun extractTokenIds(tensor: OnnxTensor): List<Int> {
        val value = runCatching { tensor.value }.getOrNull()
        val shape = tensor.info.shape

        when (value) {
            is LongArray -> return value.map { it.toInt() }
            is IntArray -> return value.toList()
            is Array<*> -> {
                val first = value.firstOrNull()
                when (first) {
                    is LongArray -> return first.map { it.toInt() }
                    is IntArray -> return first.toList()
                }
            }
        }

        if (value is Array<*>) {
            val array2d = extract2dFloatArray(value)
            if (array2d != null) {
                return array2d.map { logits ->
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
            }
        }

        if (shape.size >= 3) {
            val seqLen = shape[shape.size - 2].toInt()
            val vocabSize = shape[shape.size - 1].toInt()
            val fb = runCatching { tensor.floatBuffer }.getOrNull()
            if (fb != null && seqLen > 0 && vocabSize > 0 && fb.capacity() >= seqLen * vocabSize) {
                val tokenIds = ArrayList<Int>(seqLen)
                for (s in 0 until seqLen) {
                    var maxIdx = 0
                    var maxVal = Float.NEGATIVE_INFINITY
                    val offset = s * vocabSize
                    for (v in 0 until vocabSize) {
                        val valAt = fb.get(offset + v)
                        if (valAt > maxVal) {
                            maxVal = valAt
                            maxIdx = v
                        }
                    }
                    tokenIds.add(maxIdx)
                }
                return tokenIds
            }
        }

        return emptyList()
    }

    private fun extract2dFloatArray(value: Array<*>): Array<FloatArray>? {
        if (value.isEmpty()) return null
        val first = value[0] ?: return null
        if (first is Array<*>) {
            if (first.isEmpty()) return null
            val second = first[0] ?: return null
            if (second is FloatArray) {
                @Suppress("UNCHECKED_CAST")
                return first as Array<FloatArray>
            } else if (second is Array<*>) {
                val third = second.firstOrNull()
                if (third is FloatArray) {
                    @Suppress("UNCHECKED_CAST")
                    return second as Array<FloatArray>
                }
            }
        } else if (first is FloatArray) {
            @Suppress("UNCHECKED_CAST")
            return value as Array<FloatArray>
        }
        return null
    }

    override fun close() {
        runCatching { detSession.close() }
        runCatching { recSession.close() }
    }

    private companion object {
        /** Side of the square crop manga-ocr reads. */
        const val RECOGNITION_SIZE = 224
    }
}

/**
 * The step loop the manga-ocr decoder needs, kept out of the ONNX session so the one rule that was
 * wrong — asking the decoder exactly once — is a plain function a test can drive.
 *
 * The decoder starts from `</s>` (id 2) and answers one token at a time; each answer is fed back in,
 * because the next token depends on everything read so far. Generation stops at `</s>` (id 3) or
 * after [MAX_NEW_TOKENS] tokens, the same limits as manga-ocr's `generation_config.json` (300) with
 * the shorter cap the model's author uses, since the combined export pays a full encoder pass per
 * token.
 */
object MangaOcrDecoding {

    const val START_TOKEN_ID = 2L
    const val EOS_TOKEN_ID = 3

    /** Token ids below this one are the tokenizer's controls and are never printed. */
    const val FIRST_TEXT_TOKEN_ID = 5

    const val MAX_NEW_TOKENS = 80

    fun generate(next: (LongArray) -> Int?): List<Int> {
        val tokens = ArrayList<Long>(MAX_NEW_TOKENS + 1)
        tokens.add(START_TOKEN_ID)
        val generated = ArrayList<Int>(MAX_NEW_TOKENS)

        while (generated.size < MAX_NEW_TOKENS) {
            val token = next(tokens.toLongArray()) ?: break
            if (token == EOS_TOKEN_ID) break
            tokens.add(token.toLong())
            generated.add(token)
        }
        return generated
    }
}
