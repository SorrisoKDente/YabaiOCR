package dev.yabai.ocr.capture

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Where a camera photo sits on the screen-sized page the picker draws (R47a).
 *
 * The picker's page is stretched into its window, which is right for a screen capture — it already
 * has the screen's aspect ratio — and would distort a photo. Fitting the photo into a page of the
 * screen's size keeps its proportions, and the picker can keep using one coordinate system for the
 * page, the boxes and the pinch.
 *
 * [scale] is the same for both axes: [drawnWidth] and [drawnHeight] are the photo as drawn, and
 * [left]/[top] centre it. The geometry lives here, apart from the service, so the fit and the box
 * translation can be measured without a screen or an OCR engine.
 */
internal data class PhotoPageLayout(
    val pageWidth: Int,
    val pageHeight: Int,
    val left: Int,
    val top: Int,
    val drawnWidth: Int,
    val drawnHeight: Int,
    val scale: Float,
) {
    /** The part of the page the photo covers, in page pixels. */
    val photoBounds: Rect
        get() = Rect(left, top, left + drawnWidth, top + drawnHeight)
}

internal object PhotoPageGeometry {

    /**
     * Fits a [photoWidth]×[photoHeight] picture inside a [pageWidth]×[pageHeight] page.
     *
     * The smaller of the two ratios wins, so nothing is cropped; the remainder is divided equally
     * on both sides to centre the picture. Null for a picture or a page with no area.
     */
    fun fit(photoWidth: Int, photoHeight: Int, pageWidth: Int, pageHeight: Int): PhotoPageLayout? {
        if (photoWidth <= 0 || photoHeight <= 0 || pageWidth <= 0 || pageHeight <= 0) return null
        val scale = min(pageWidth.toFloat() / photoWidth, pageHeight.toFloat() / photoHeight)
        val drawnWidth = (photoWidth * scale).roundToInt().coerceIn(1, pageWidth)
        val drawnHeight = (photoHeight * scale).roundToInt().coerceIn(1, pageHeight)
        return PhotoPageLayout(
            pageWidth = pageWidth,
            pageHeight = pageHeight,
            left = (pageWidth - drawnWidth) / 2,
            top = (pageHeight - drawnHeight) / 2,
            drawnWidth = drawnWidth,
            drawnHeight = drawnHeight,
            scale = scale,
        )
    }

    /** A rectangle of the photo, in the page's pixels. */
    fun place(layout: PhotoPageLayout, rect: Rect): Rect = Rect(
        layout.left + (rect.left * layout.scale).roundToInt(),
        layout.top + (rect.top * layout.scale).roundToInt(),
        layout.left + (rect.right * layout.scale).roundToInt(),
        layout.top + (rect.bottom * layout.scale).roundToInt(),
    )

    /**
     * The page the picker draws: black, with the photo fitted and centred on it.
     *
     * Null when the bitmap cannot be allocated. The service then opens the panel without the picker
     * rather than failing the read.
     */
    fun page(photo: Bitmap, layout: PhotoPageLayout): Bitmap? = runCatching {
        Bitmap.createBitmap(layout.pageWidth, layout.pageHeight, Bitmap.Config.ARGB_8888).also { page ->
            Canvas(page).apply {
                drawColor(Color.BLACK)
                drawBitmap(
                    photo,
                    null,
                    layout.photoBounds,
                    Paint(Paint.FILTER_BITMAP_FLAG),
                )
            }
        }
    }.getOrNull()
}
