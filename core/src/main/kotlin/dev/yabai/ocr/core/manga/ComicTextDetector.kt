package dev.yabai.ocr.core.manga

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OnnxValue
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import java.nio.FloatBuffer

data class ComicDetectedBox(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val isVertical: Boolean,
)

/**
 * Executes text region detection with the ONNX comic-text-detector (dmMaze's model, exported by
 * mayocream as `comic-text-detector.onnx`).
 *
 * The model has one fixed input — `images`, 1024x1024 RGB scaled to 0..1, **no mean/std** — and
 * three outputs: `blk` (the text lines), `seg` (the text pixel mask) and a third map. The lines are
 * already decoded in `blk`, as rows of `cx, cy, w, h, confidence, horizontal, vertical` in the
 * 1024x1024 space; reading output **0** as a probability heatmap instead was feeding those
 * coordinates back as if they were confidences, which flood-filled the whole page into one box.
 *
 * Boxes come back in the pixels of the image that was handed in, so the caller can crop with them.
 */
class ComicTextDetector(
    private val env: OrtEnvironment,
    private val detSession: OrtSession,
    private val probabilityThreshold: Float = DEFAULT_THRESHOLD,
) {

    fun detect(
        pixels: IntArray,
        width: Int,
        height: Int,
    ): List<ComicDetectedBox> {
        if (width <= 0 || height <= 0 || pixels.isEmpty()) return emptyList()

        val shape = (detSession.inputInfo.values.firstOrNull()?.info as? TensorInfo)?.shape
        val targetHeight = shape?.getOrNull(2)?.takeIf { it > 0 }?.toInt() ?: DETECTION_SIZE
        val targetWidth = shape?.getOrNull(3)?.takeIf { it > 0 }?.toInt() ?: DETECTION_SIZE

        val prep = MangaOcrPreprocessor.prepareDetectionInput(
            pixels = pixels,
            width = width,
            height = height,
            fixedWidth = targetWidth,
            fixedHeight = targetHeight,
        )

        val inputName = detSession.inputNames.iterator().next()
        val tensor = OnnxTensor.createTensor(
            env,
            prep.buffer,
            longArrayOf(1, 3, targetHeight.toLong(), targetWidth.toLong()),
        )

        val boxes = tensor.use { inputTensor ->
            detSession.run(mapOf(inputName to inputTensor)).use { outputs ->
                val byName: Map<String, OnnxValue> = outputs.associate { it.key to it.value }
                val blockTensor = byName["blk"] as? OnnxTensor
                if (blockTensor != null) {
                    boxesFromBlocks(
                        data = blockTensor.floatBuffer,
                        rows = blockTensor.info.shape.getOrNull(1)?.toInt() ?: 0,
                        stride = blockTensor.info.shape.getOrNull(2)?.toInt() ?: BLOCK_STRIDE,
                        origWidth = width,
                        origHeight = height,
                        targetWidth = targetWidth,
                        targetHeight = targetHeight,
                        threshold = probabilityThreshold,
                    )
                } else {
                    // Another export of the same detector: fall back to a single-channel map.
                    val probMap = heatmap(byName)?.let { extractProbMap(it) }
                    if (probMap == null) emptyList() else extractBoxes(probMap, width, height)
                }
            }
        }

        if (boxes.isEmpty()) {
            // Nothing detected: the page as a whole is the only thing left to read.
            return listOf(ComicDetectedBox(0, 0, width, height, width < height))
        }
        return boxes
    }

    companion object {
        /** The resolution the detector was trained and exported for. */
        const val DETECTION_SIZE = 1024

        /** `blk` rows are `cx, cy, w, h, confidence, horizontal, vertical`. */
        const val BLOCK_STRIDE = 7

        /**
         * Confidence a `blk` row needs to become a box.
         *
         * 0.2, measured on the real model: a balloon of vertical Japanese scores around 0.9, but
         * the same balloon printed **horizontally** comes back at 0.20–0.25 — the detector is
         * trained mostly on vertical Japanese and English. At 0.35 that line was thrown away, which
         * is the user-visible half of «não reconhece horizontal». Lowering to 0.1 gained nothing
         * but a duplicate of the same block in the other class, so 0.2 is where the curve settles.
         */
        const val DEFAULT_THRESHOLD = 0.2f

        /** Two boxes of the same orientation above this overlap are the same text line. */
        const val NMS_IOU = 0.5f

        /**
         * The same region reported once as horizontal and once as vertical. The two classes are
         * suppressed separately (as in the reference), so this only catches an exact duplicate —
         * which, left alone, would put the same balloon in the panel twice.
         */
        const val DUPLICATE_IOU = 0.8f

        private const val MIN_BOX_PX = 8

        /**
         * The text lines of the `blk` output, in the pixels of the original image.
         *
         * Coordinates in `blk` are the centre and the size of the line in the detector's own
         * 1024x1024 input, so both are scaled back by the ratio between the two images. The class
         * scores in the last two columns say which way the line is printed.
         */
        fun boxesFromBlocks(
            data: FloatBuffer,
            rows: Int,
            stride: Int,
            origWidth: Int,
            origHeight: Int,
            targetWidth: Int,
            targetHeight: Int,
            threshold: Float = DEFAULT_THRESHOLD,
        ): List<ComicDetectedBox> {
            if (rows <= 0 || stride < BLOCK_STRIDE || targetWidth <= 0 || targetHeight <= 0) return emptyList()
            val scaleX = origWidth.toFloat() / targetWidth
            val scaleY = origHeight.toFloat() / targetHeight

            val candidates = ArrayList<Candidate>(rows)
            for (index in 0 until rows) {
                val base = index * stride
                if (base + BLOCK_STRIDE > data.capacity()) break
                val confidence = data.get(base + 4)
                if (confidence < threshold) continue

                val centerX = data.get(base) * scaleX
                val centerY = data.get(base + 1) * scaleY
                val width = data.get(base + 2) * scaleX
                val height = data.get(base + 3) * scaleY
                if (width <= 0f || height <= 0f) continue

                candidates += Candidate(
                    left = centerX - width / 2f,
                    top = centerY - height / 2f,
                    right = centerX + width / 2f,
                    bottom = centerY + height / 2f,
                    confidence = confidence,
                    vertical = data.get(base + 6) > data.get(base + 5),
                )
            }

            // The detector reports overlapping guesses for the same line; keeping all of them would
            // read a balloon twice and put the text in the panel twice.
            val kept = ArrayList<Candidate>(candidates.size)
            for (candidate in candidates.sortedByDescending { it.confidence }) {
                val duplicate = kept.any { other ->
                    val limit = if (other.vertical == candidate.vertical) NMS_IOU else DUPLICATE_IOU
                    iou(other, candidate) > limit
                }
                if (duplicate) continue
                kept += candidate
            }

            return kept.mapNotNull { candidate ->
                val left = candidate.left.toInt().coerceIn(0, origWidth - 1)
                val top = candidate.top.toInt().coerceIn(0, origHeight - 1)
                val right = candidate.right.toInt().coerceIn(left + 1, origWidth)
                val bottom = candidate.bottom.toInt().coerceIn(top + 1, origHeight)
                if (right - left < MIN_BOX_PX || bottom - top < MIN_BOX_PX) {
                    null
                } else {
                    ComicDetectedBox(left, top, right, bottom, candidate.vertical)
                }
            }
        }

        private data class Candidate(
            val left: Float,
            val top: Float,
            val right: Float,
            val bottom: Float,
            val confidence: Float,
            val vertical: Boolean,
        )

        private fun iou(a: Candidate, b: Candidate): Float {
            val left = maxOf(a.left, b.left)
            val top = maxOf(a.top, b.top)
            val right = minOf(a.right, b.right)
            val bottom = minOf(a.bottom, b.bottom)
            if (right <= left || bottom <= top) return 0f
            val intersection = (right - left) * (bottom - top)
            val union = area(a) + area(b) - intersection
            return if (union <= 0f) 0f else intersection / union
        }

        private fun area(box: Candidate): Float =
            (box.right - box.left).coerceAtLeast(0f) * (box.bottom - box.top).coerceAtLeast(0f)
    }

    private fun heatmap(values: Map<String, OnnxValue>): OnnxTensor? {
        (values["seg"] ?: values["det"] ?: values["prob"])?.let { return it as? OnnxTensor }
        return values.values.firstOrNull { value ->
            val info = (value as? OnnxTensor)?.info ?: return@firstOrNull false
            info.shape.size == 4 && info.shape[1] == 1L
        } as? OnnxTensor
    }

    private fun extractProbMap(tensor: OnnxTensor): Array<FloatArray>? {
        val shape = tensor.info.shape
        if (shape.size >= 2) {
            val h = shape[shape.size - 2].toInt()
            val w = shape[shape.size - 1].toInt()
            if (h > 0 && w > 0) {
                val fb = runCatching { tensor.floatBuffer }.getOrNull()
                if (fb != null && fb.capacity() >= h * w) {
                    val map = Array(h) { y ->
                        val row = FloatArray(w)
                        val offset = y * w
                        for (x in 0 until w) {
                            row[x] = fb.get(offset + x)
                        }
                        row
                    }
                    return map
                }
            }
        }
        return extractProbMapFromValue(tensor.value)
    }

    @Suppress("UNCHECKED_CAST")
    private fun extractProbMapFromValue(value: Any?): Array<FloatArray>? {
        return when (value) {
            is Array<*> -> {
                val first = value.firstOrNull() ?: return null
                when (first) {
                    is Array<*> -> {
                        val second = first.firstOrNull() ?: return null
                        when (second) {
                            is Array<*> -> {
                                (value.getOrNull(0) as? Array<*>)?.getOrNull(0) as? Array<FloatArray>
                            }
                            is FloatArray -> {
                                value.getOrNull(0) as? Array<FloatArray>
                            }
                            else -> null
                        }
                    }
                    is FloatArray -> {
                        value as? Array<FloatArray>
                    }
                    else -> null
                }
            }
            else -> null
        }
    }

    private fun extractBoxes(
        probMap: Array<FloatArray>,
        origW: Int,
        origH: Int,
    ): MutableList<ComicDetectedBox> {
        val mapH = probMap.size
        val mapW = probMap.firstOrNull()?.size ?: 0
        if (mapH <= 0 || mapW <= 0) return mutableListOf()

        val visited = Array(mapH) { BooleanArray(mapW) }
        val boxes = mutableListOf<ComicDetectedBox>()

        val maxQueueSize = mapH * mapW
        val queueX = IntArray(maxQueueSize)
        val queueY = IntArray(maxQueueSize)

        for (y in 0 until mapH) {
            for (x in 0 until mapW) {
                if (!visited[y][x] && probMap[y][x] >= probabilityThreshold) {
                    var minX = x
                    var maxX = x
                    var minY = y
                    var maxY = y

                    var head = 0
                    var tail = 0

                    queueX[tail] = x
                    queueY[tail] = y
                    tail++
                    visited[y][x] = true

                    while (head < tail) {
                        val cx = queueX[head]
                        val cy = queueY[head]
                        head++

                        minX = minOf(minX, cx)
                        maxX = maxOf(maxX, cx)
                        minY = minOf(minY, cy)
                        maxY = maxOf(maxY, cy)

                        // 4-connectivity
                        if (cx + 1 < mapW && !visited[cy][cx + 1] && probMap[cy][cx + 1] >= probabilityThreshold) {
                            visited[cy][cx + 1] = true
                            queueX[tail] = cx + 1
                            queueY[tail] = cy
                            tail++
                        }
                        if (cx - 1 >= 0 && !visited[cy][cx - 1] && probMap[cy][cx - 1] >= probabilityThreshold) {
                            visited[cy][cx - 1] = true
                            queueX[tail] = cx - 1
                            queueY[tail] = cy
                            tail++
                        }
                        if (cy + 1 < mapH && !visited[cy + 1][cx] && probMap[cy + 1][cx] >= probabilityThreshold) {
                            visited[cy + 1][cx] = true
                            queueX[tail] = cx
                            queueY[tail] = cy + 1
                            tail++
                        }
                        if (cy - 1 >= 0 && !visited[cy - 1][cx] && probMap[cy - 1][cx] >= probabilityThreshold) {
                            visited[cy - 1][cx] = true
                            queueX[tail] = cx
                            queueY[tail] = cy - 1
                            tail++
                        }
                    }

                    // Map back to original image dimensions
                    val origLeft = (minX * origW / mapW).coerceIn(0, origW - 1)
                    val origTop = (minY * origH / mapH).coerceIn(0, origH - 1)
                    val origRight = ((maxX + 1) * origW / mapW).coerceIn(origLeft + 1, origW)
                    val origBottom = ((maxY + 1) * origH / mapH).coerceIn(origTop + 1, origH)

                    val boxW = origRight - origLeft
                    val boxH = origBottom - origTop

                    if (boxW >= MIN_BOX_PX && boxH >= MIN_BOX_PX) {
                        boxes.add(ComicDetectedBox(origLeft, origTop, origRight, origBottom, boxW < boxH))
                    }
                }
            }
        }

        return boxes
    }
}
