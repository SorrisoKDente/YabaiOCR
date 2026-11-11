package dev.yabai.ocr.core.manga

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession

data class ComicDetectedBox(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val isVertical: Boolean,
)

/**
 * Executes text region detection using an ONNX DBNet comic text detector session.
 */
class ComicTextDetector(
    private val env: OrtEnvironment,
    private val detSession: OrtSession,
    private val probabilityThreshold: Float = 0.35f,
) {

    fun detect(
        pixels: IntArray,
        width: Int,
        height: Int,
    ): List<ComicDetectedBox> {
        if (width <= 0 || height <= 0 || pixels.isEmpty()) return emptyList()

        val prep = MangaOcrPreprocessor.prepareDetectionInput(pixels, width, height)

        val inputName = detSession.inputNames.iterator().next()
        val tensor = OnnxTensor.createTensor(
            env,
            prep.buffer,
            longArrayOf(1, 3, prep.targetHeight.toLong(), prep.targetWidth.toLong())
        )

        val probMap: Array<FloatArray>? = tensor.use { inputTensor ->
            detSession.run(mapOf(inputName to inputTensor)).use { outputMap ->
                val outputTensor = outputMap.get(0) as? OnnxTensor ?: return emptyList()
                val array = outputTensor.value as? Array<Array<Array<FloatArray>>>
                array?.get(0)?.get(0) // Prob map [targetHeight][targetWidth]
            }
        }

        if (probMap == null) return emptyList()

        val boxes = extractBoxes(probMap, prep.targetHeight, prep.targetWidth, width, height)
        if (boxes.isEmpty()) {
            // Fallback: full image box if no region detected
            boxes.add(ComicDetectedBox(0, 0, width, height, width < height))
        }

        return boxes
    }

    private fun extractBoxes(
        probMap: Array<FloatArray>,
        targetH: Int,
        targetW: Int,
        origW: Int,
        origH: Int,
    ): MutableList<ComicDetectedBox> {
        val visited = Array(targetH) { BooleanArray(targetW) }
        val boxes = mutableListOf<ComicDetectedBox>()

        for (y in 0 until targetH) {
            for (x in 0 until targetW) {
                if (!visited[y][x] && probMap[y][x] >= probabilityThreshold) {
                    var minX = x
                    var maxX = x
                    var minY = y
                    var maxY = y

                    val maxQueueSize = targetH * targetW
                    val queueX = IntArray(maxQueueSize)
                    val queueY = IntArray(maxQueueSize)
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
                        if (cx + 1 < targetW && !visited[cy][cx + 1] && probMap[cy][cx + 1] >= probabilityThreshold) {
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
                        if (cy + 1 < targetH && !visited[cy + 1][cx] && probMap[cy + 1][cx] >= probabilityThreshold) {
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
                    val origLeft = (minX * origW / targetW).coerceIn(0, origW - 1)
                    val origTop = (minY * origH / targetH).coerceIn(0, origH - 1)
                    val origRight = ((maxX + 1) * origW / targetW).coerceIn(origLeft + 1, origW)
                    val origBottom = ((maxY + 1) * origH / targetH).coerceIn(origTop + 1, origH)

                    val boxW = origRight - origLeft
                    val boxH = origBottom - origTop

                    // Filter tiny noise boxes (< 8px)
                    if (boxW >= 8 && boxH >= 8) {
                        boxes.add(ComicDetectedBox(origLeft, origTop, origRight, origBottom, boxW < boxH))
                    }
                }
            }
        }

        return boxes
    }
}
