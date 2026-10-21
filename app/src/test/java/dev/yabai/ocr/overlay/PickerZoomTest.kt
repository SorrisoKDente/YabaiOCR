package dev.yabai.ocr.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The zoom of the picker (R15), where the arithmetic is.
 *
 * Two things have to hold for a zoom to be useful: the point under the fingers stays under the
 * fingers, and the page never slides off the screen showing a blank edge. Both are pure geometry, so
 * they are checked here instead of on a phone.
 */
class PickerZoomTest {

    private fun zoom(width: Float = 320f, height: Float = 640f) = PickerZoom(width, height)

    @Test
    fun `at one to one the view point is the page point`() {
        val zoom = zoom()

        assertEquals(1f, zoom.scale, 0.0001f)
        assertEquals(120f, zoom.toPageX(120f), 0.0001f)
        assertEquals(480f, zoom.toPageY(480f), 0.0001f)
    }

    @Test
    fun `a pinch keeps the page point under the fingers`() {
        val zoom = zoom()
        val pageX = zoom.toPageX(100f)
        val pageY = zoom.toPageY(200f)

        zoom.zoomBy(2f, 100f, 200f)

        assertEquals(2f, zoom.scale, 0.0001f)
        assertEquals(pageX, zoom.toPageX(100f), 0.0001f)
        assertEquals(pageY, zoom.toPageY(200f), 0.0001f)
    }

    @Test
    fun `the zoom stops at five times and comes back to one`() {
        val zoom = zoom()

        zoom.zoomBy(100f, 0f, 0f)
        assertEquals(PickerZoom.MAX, zoom.scale, 0.0001f)

        zoom.zoomBy(0.001f, 0f, 0f)
        assertEquals(PickerZoom.MIN, zoom.scale, 0.0001f)
        assertEquals("at 1× there is nothing off screen", 0f, zoom.offsetX, 0.0001f)
        assertEquals(0f, zoom.offsetY, 0.0001f)
    }

    @Test
    fun `a page at one to one does not pan`() {
        val zoom = zoom()

        zoom.panBy(80f, -60f)

        assertEquals(0f, zoom.offsetX, 0.0001f)
        assertEquals(0f, zoom.offsetY, 0.0001f)
    }

    @Test
    fun `a zoomed page pans and a tap is read on the page`() {
        val zoom = zoom()
        zoom.zoomBy(2f, 0f, 0f)

        zoom.panBy(-100f, -100f)

        assertEquals(-100f, zoom.offsetX, 0.0001f)
        assertEquals("the page slid left by 100, so the point at 100 is 100 on the page", 100f, zoom.toPageX(100f), 0.0001f)
        assertEquals(100f, zoom.toPageY(100f), 0.0001f)
    }

    @Test
    fun `the page cannot be dragged past its own edge`() {
        val zoom = zoom()
        zoom.zoomBy(2f, 0f, 0f)

        zoom.panBy(500f, 500f)
        assertEquals("no blank strip on the left", 0f, zoom.offsetX, 0.0001f)
        assertEquals(0f, zoom.offsetY, 0.0001f)

        zoom.panBy(-5_000f, -5_000f)
        assertEquals("no blank strip on the right", -320f, zoom.offsetX, 0.0001f)
        assertEquals(-640f, zoom.offsetY, 0.0001f)
    }

    @Test
    fun `a new page starts unzoomed`() {
        val zoom = zoom()
        zoom.zoomBy(3f, 40f, 40f)
        zoom.panBy(-30f, -30f)

        zoom.reset(320f, 640f)

        assertEquals(1f, zoom.scale, 0.0001f)
        assertEquals(0f, zoom.offsetX, 0.0001f)
        assertEquals(0f, zoom.offsetY, 0.0001f)
    }
}
