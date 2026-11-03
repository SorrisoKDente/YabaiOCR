package dev.yabai.ocr.desktop

import dev.yabai.ocr.core.lens.PixelBox
import java.awt.Point
import java.awt.Rectangle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HoverGeometryTest {

    private val screen = Rectangle(0, 0, 1920, 1080)

    @Test
    fun `crop is clamped at the right and bottom edges`() {
        val crop = HoverGeometry.planCrop(Point(1910, 1070), screen, 640, 360)

        assertEquals(Rectangle(1280, 720, 640, 360), crop)
    }

    @Test
    fun `crop is clamped at the left and top edges`() {
        val crop = HoverGeometry.planCrop(Point(5, 5), screen, 640, 360)

        assertEquals(Rectangle(0, 0, 640, 360), crop)
    }

    @Test
    fun `crop stays on a monitor to the left of the primary one`() {
        val virtual = Rectangle(-1920, 0, 3840, 1080)
        val crop = HoverGeometry.planCrop(Point(-1900, 10), virtual, 640, 360)

        assertEquals(Rectangle(-1920, 0, 640, 360), crop)
        assertTrue(virtual.contains(crop))
    }

    @Test
    fun `crop never runs past a screen smaller than the requested size`() {
        val small = Rectangle(0, 0, 320, 200)
        val crop = HoverGeometry.planCrop(Point(10, 10), small, 640, 360)

        assertEquals(Rectangle(0, 0, 320, 200), crop)
    }

    @Test
    fun `pointer is scaled into the image Robot actually captured`() {
        val crop = Rectangle(100, 100, 640, 360)
        val local = HoverGeometry.localPoint(Point(420, 280), crop, imageWidth = 960, imageHeight = 540)

        // The pointer was 320x180 into the requested crop; a 1.5x capture puts it at 480x270.
        assertEquals(Point(480, 270), local)
    }

    @Test
    fun `token box is scaled back to screen pixels`() {
        val crop = Rectangle(100, 100, 640, 360)
        val screenBox = HoverGeometry.toScreen(
            PixelBox(480, 270, 60, 60),
            crop,
            imageWidth = 960,
            imageHeight = 540,
        )

        assertEquals(Rectangle(420, 280, 40, 40), screenBox)
    }

    @Test
    fun `picks the monitor that holds most of the crop`() {
        val left = Rectangle(0, 0, 1920, 1080)
        val right = Rectangle(1920, 0, 1920, 1080)

        assertEquals(0, HoverGeometry.monitorIndex(listOf(left, right), Rectangle(100, 100, 640, 360)))
        assertEquals(1, HoverGeometry.monitorIndex(listOf(left, right), Rectangle(2500, 100, 640, 360)))
        // Straddling the seam: the bigger half decides.
        assertEquals(1, HoverGeometry.monitorIndex(listOf(left, right), Rectangle(1700, 100, 640, 360)))
        assertEquals(0, HoverGeometry.monitorIndex(emptyList(), Rectangle(0, 0, 10, 10)))
    }

    @Test
    fun `uses the monitor under the pointer, not the union of all screens`() {
        val small = Rectangle(0, 0, 1280, 720)
        val tall = Rectangle(1280, 0, 1920, 1080)
        val monitors = listOf(small, tall)

        assertEquals(small, HoverGeometry.monitorAt(monitors, Point(600, 700)))
        assertEquals(tall, HoverGeometry.monitorAt(monitors, Point(2000, 100)))
        // A point above both screens goes to the nearest one.
        assertEquals(small, HoverGeometry.monitorAt(monitors, Point(600, -100)))
        assertEquals(null, HoverGeometry.monitorAt(emptyList(), Point(0, 0)))
    }

    @Test
    fun `tooltip flips up on the smaller monitor even when a taller screen exists`() {
        val small = Rectangle(0, 0, 1280, 720)

        val point = HoverGeometry.placeTooltip(
            pointer = Point(600, 690),
            size = java.awt.Dimension(460, 150),
            screen = small,
        )

        // 690 - 150 - 18 = 522. Measuring against a union that reaches 1080 would not flip at all.
        assertEquals(Point(618, 522), point)
    }

    @Test
    fun `tooltip sits below right of the pointer`() {
        val point = HoverGeometry.placeTooltip(
            pointer = Point(300, 300),
            size = java.awt.Dimension(460, 280),
            screen = screen,
        )

        assertEquals(Point(318, 318), point)
    }

    @Test
    fun `tooltip flips to the left and above near the bottom right corner`() {
        val point = HoverGeometry.placeTooltip(
            pointer = Point(1900, 1060),
            size = java.awt.Dimension(460, 280),
            screen = screen,
        )

        // 1900 - 460 - 18 = 1422; 1060 - 280 - 18 = 762.
        assertEquals(Point(1422, 762), point)
    }

    @Test
    fun `tooltip is clamped inside a monitor with negative origin`() {
        val virtual = Rectangle(-1920, -200, 3840, 1280)
        // A pointer coordinate outside the virtual desktop must not produce an off-screen card.
        val point = HoverGeometry.placeTooltip(
            pointer = Point(-2500, -190),
            size = java.awt.Dimension(460, 280),
            screen = virtual,
        )

        assertEquals(Point(-1920, -172), point)
        assertTrue(virtual.contains(point))
    }

    @Test
    fun `tooltip never leaves a screen smaller than the card`() {
        val small = Rectangle(0, 0, 300, 200)
        val point = HoverGeometry.placeTooltip(
            pointer = Point(10, 10),
            size = java.awt.Dimension(460, 280),
            screen = small,
        )

        assertEquals(Point(0, 0), point)
    }

    @Test
    fun `contains honours the margin used to keep the tooltip open`() {
        val rect = Rectangle(100, 100, 50, 50)

        assertTrue(HoverGeometry.contains(rect, Point(95, 95), margin = 6))
        assertTrue(HoverGeometry.contains(rect, Point(150, 150), margin = 6))
        assertFalse(HoverGeometry.contains(rect, Point(80, 80), margin = 6))
        assertFalse(HoverGeometry.contains(null, Point(100, 100)))
    }
}
