package dev.yabai.ocr.desktop.unlimited

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer

data class UnlimitedRecognizedLine(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val isVertical: Boolean,
)

class UnlimitedOcrProcessor(
    detModelBytes: ByteArray,
    recModelBytes: ByteArray,
    private val charDictionary: List<String>,
) : AutoCloseable {

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val detSession: OrtSession = env.createSession(detModelBytes)
    private val recSession: OrtSession = env.createSession(recModelBytes)

    fun isInitialized(): Boolean = true

    fun recognizePixels(
        pixels: IntArray,
        width: Int,
        height: Int,
    ): List<UnlimitedRecognizedLine> {
        if (width <= 0 || height <= 0 || pixels.isEmpty()) return emptyList()

        // 1. Preprocess for DBNet Detection (Resize to multiples of 32, normalized RGB)
        val targetWidth = ((width + 31) / 32) * 32
        val targetHeight = ((height + 31) / 32) * 32

        val detInputBuffer = FloatBuffer.allocate(1 * 3 * targetHeight * targetWidth)
        val mean = floatArrayOf(0.485f, 0.456f, 0.406f)
        val std = floatArrayOf(0.229f, 0.224f, 0.225f)

        // Fill FloatBuffer in NCHW format
        for (c in 0..2) {
            val m = mean[c]
            val s = std[c]
            for (y in 0 until targetHeight) {
                val srcY = (y * height / targetHeight).coerceIn(0, height - 1)
                for (x in 0 until targetWidth) {
                    val srcX = (x * width / targetWidth).coerceIn(0, width - 1)
                    val pixel = pixels[srcY * width + srcX]
                    val color = when (c) {
                        0 -> (pixel shr 16) and 0xFF // Red
                        1 -> (pixel shr 8) and 0xFF  // Green
                        else -> pixel and 0xFF       // Blue
                    }
                    val normalized = (color / 255.0f - m) / s
                    detInputBuffer.put(normalized)
                }
            }
        }
        detInputBuffer.rewind()

        val detTensor = OnnxTensor.createTensor(
            env,
            detInputBuffer,
            longArrayOf(1, 3, targetHeight.toLong(), targetWidth.toLong())
        )

        val detOutputMap = detTensor.use {
            detSession.run(mapOf(detSession.inputNames.iterator().next() to it))
        }

        val detResult = detOutputMap.use {
            val outputTensor = it.get(0) as? OnnxTensor ?: return emptyList()
            val array = outputTensor.value as? Array<Array<Array<FloatArray>>> ?: return emptyList()
            array[0][0] // [targetHeight][targetWidth]
        }

        // 2. Extract bounding boxes from probability map
        val boxes = extractBoxes(detResult, targetHeight, targetWidth, width, height)
        if (boxes.isEmpty()) {
            // Fallback: treat whole image as single box
            boxes.add(UnlimitedBox(0, 0, width, height, width < height))
        }

        // 3. Recognize each box using CRNN/SVTR Rec Session
        val results = mutableListOf<UnlimitedRecognizedLine>()
        for (box in boxes) {
            val boxW = (box.right - box.left).coerceAtLeast(1)
            val boxH = (box.bottom - box.top).coerceAtLeast(1)

            // Crop pixels for this box
            val croppedPixels = IntArray(boxW * boxH)
            for (cy in 0 until boxH) {
                val sy = (box.top + cy).coerceIn(0, height - 1)
                for (cx in 0 until boxW) {
                    val sx = (box.left + cx).coerceIn(0, width - 1)
                    croppedPixels[cy * boxW + cx] = pixels[sy * width + sx]
                }
            }

            val text = recognizeCrop(croppedPixels, boxW, boxH)
            if (text.isNotBlank()) {
                results.add(
                    UnlimitedRecognizedLine(
                        text = text,
                        left = box.left,
                        top = box.top,
                        right = box.right,
                        bottom = box.bottom,
                        isVertical = box.isVertical,
                    )
                )
            }
        }

        return results
    }

    private data class UnlimitedBox(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
        val isVertical: Boolean,
    )

    private fun extractBoxes(
        probMap: Array<FloatArray>,
        detH: Int,
        detW: Int,
        origW: Int,
        origH: Int,
    ): MutableList<UnlimitedBox> {
        val thresh = 0.3f
        val boxes = mutableListOf<UnlimitedBox>()

        // Simple grid segmentation over probability map
        val gridH = 8
        val gridW = 8
        val stepY = detH / gridH
        val stepX = detW / gridW

        for (gy in 0 until gridH) {
            for (gx in 0 until gridW) {
                val startY = gy * stepY
                val endY = (gy + 1) * stepY
                val startX = gx * stepX
                val endX = (gx + 1) * stepX

                var activeCount = 0
                var minX = endX
                var maxX = startX
                var minY = endY
                var maxY = startY

                for (y in startY until endY) {
                    for (x in startX until endX) {
                        if (probMap[y][x] > thresh) {
                            activeCount++
                            if (x < minX) minX = x
                            if (x > maxX) maxX = x
                            if (y < minY) minY = y
                            if (y > maxY) maxY = y
                        }
                    }
                }

                if (activeCount > 10 && minX < maxX && minY < maxY) {
                    val realLeft = (minX * origW / detW).coerceIn(0, origW - 1)
                    val realRight = (maxX * origW / detW).coerceIn(realLeft + 1, origW)
                    val realTop = (minY * origH / detH).coerceIn(0, origH - 1)
                    val realBottom = (maxY * origH / detH).coerceIn(realTop + 1, origH)
                    val isVert = (realBottom - realTop) > (realRight - realLeft)

                    boxes.add(UnlimitedBox(realLeft, realTop, realRight, realBottom, isVert))
                }
            }
        }

        return boxes
    }

    private fun recognizeCrop(cropPixels: IntArray, cropW: Int, cropH: Int): String {
        val recH = 48
        val recW = (cropW * recH / cropH).coerceIn(48, 320)

        val recBuffer = FloatBuffer.allocate(1 * 3 * recH * recW)
        for (c in 0..2) {
            for (y in 0 until recH) {
                val sy = (y * cropH / recH).coerceIn(0, cropH - 1)
                for (x in 0 until recW) {
                    val sx = (x * cropW / recW).coerceIn(0, cropW - 1)
                    val pixel = cropPixels[sy * cropW + sx]
                    val color = when (c) {
                        0 -> (pixel shr 16) and 0xFF
                        1 -> (pixel shr 8) and 0xFF
                        else -> pixel and 0xFF
                    }
                    recBuffer.put((color / 255.0f - 0.5f) / 0.5f)
                }
            }
        }
        recBuffer.rewind()

        val recTensor = OnnxTensor.createTensor(
            env,
            recBuffer,
            longArrayOf(1, 3, recH.toLong(), recW.toLong())
        )

        val recOutputMap = recTensor.use {
            recSession.run(mapOf(recSession.inputNames.iterator().next() to it))
        }

        val indices = recOutputMap.use {
            val outputTensor = it.get(0) as? OnnxTensor ?: return ""
            val raw = outputTensor.value
            extractArgMaxIndices(raw)
        }

        // CTC greedy decode using charDictionary
        val builder = StringBuilder()
        var lastIdx = -1
        for (idx in indices) {
            if (idx != 0 && idx != lastIdx) { // 0 is blank
                val charIdx = idx - 1
                if (charIdx in charDictionary.indices) {
                    builder.append(charDictionary[charIdx])
                }
            }
            lastIdx = idx
        }

        return builder.toString()
    }

    private fun extractArgMaxIndices(raw: Any?): List<Int> {
        val list = mutableListOf<Int>()
        if (raw is Array<*>) {
            val first = raw.getOrNull(0) as? Array<*> ?: return emptyList()
            for (step in first) {
                val scores = step as? FloatArray ?: continue
                var maxIdx = 0
                var maxVal = Float.NEGATIVE_INFINITY
                for (i in scores.indices) {
                    if (scores[i] > maxVal) {
                        maxVal = scores[i]
                        maxIdx = i
                    }
                }
                list.add(maxIdx)
            }
        }
        return list
    }

    override fun close() {
        runCatching { detSession.close() }
        runCatching { recSession.close() }
        runCatching { env.close() }
    }
}
