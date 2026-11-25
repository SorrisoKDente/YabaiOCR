package dev.yabai.ocr.overlay

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.view.MotionEvent
import android.view.Surface
import android.view.View
import android.widget.FrameLayout
import dev.yabai.ocr.CameraActivity
import dev.yabai.ocr.capture.DeviceRotation
import dev.yabai.ocr.capture.placeInPhoneFrame
import dev.yabai.ocr.capture.PhotoPageGeometry
import dev.yabai.ocr.capture.ScreenMetrics
import dev.yabai.ocr.service.FloatingOcrService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * The picker's page in the frame the phone was held in (R48g).
 *
 * With the rotation locked, the screen stays portrait while the phone is on its side: the photo is
 * upright and 4000×3000, and a page of the window's size fitted it to 1080×810 — a strip in the
 * middle of the screen with the text a third of the size the screen could give it. The page now takes
 * the phone's frame (2400×1080) and the picker turns it into the window, which means the drawing and
 * the touches have to take the **same** turn: a turn on one side and not the other puts every box
 * under the wrong finger, and nothing on screen would look broken.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w320dp-h640dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PageTurnTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    /** A page of the phone's frame — 2400×1080 — inside a 320×640 portrait window. */
    private fun windowPoint(degrees: Int, x: Float, y: Float): PointF {
        val points = floatArrayOf(x, y)
        PageTurn.matrix(degrees, 2400, 1080, 320, 640).mapPoints(points)
        return PointF(points[0], points[1])
    }

    private fun assertPoint(message: String, expected: PointF, actual: PointF) {
        assertEquals("$message (x)", expected.x, actual.x, 0.01f)
        assertEquals("$message (y)", expected.y, actual.y, 0.01f)
    }

    /**
     * A page that already has the window's frame is only stretched (R23), and a sideways one fills
     * the window instead: its own corners land on the window's, so the picture is as large as the
     * screen allows without being cropped.
     */
    @Test
    fun `a sideways page fills the window instead of being squashed into it`() {
        // No turn: page and window are the same frame, stretched as before.
        assertPoint("the page's far corner", PointF(320f, 640f), windowPoint(0, 2400f, 1080f))

        // 270°: the page's top-left corner is at the window's bottom-left, so its top — the top of
        // the manga page — points at the window's left, which is where the world is when the phone's
        // left side is up.
        assertPoint("the page's top-left corner", PointF(0f, 640f), windowPoint(270, 0f, 0f))
        assertPoint("the page's top-right corner", PointF(0f, 0f), windowPoint(270, 2400f, 0f))
        assertPoint("the page's bottom-left corner", PointF(320f, 640f), windowPoint(270, 0f, 1080f))
        assertPoint("the page's bottom-right corner", PointF(320f, 0f), windowPoint(270, 2400f, 1080f))

        // 90°: the same page the other way round, for a phone turned the other way.
        assertPoint("the page's top-left corner", PointF(320f, 0f), windowPoint(90, 0f, 0f))
        assertPoint("the page's top-right corner", PointF(320f, 640f), windowPoint(90, 2400f, 0f))
        assertPoint("the page's bottom-left corner", PointF(0f, 0f), windowPoint(90, 0f, 1080f))
    }

    /** What a tap means: the window point turned back into the page the box was placed on. */
    @Test
    fun `the inverse of the turn is the page a touch is tested on`() {
        val page = floatArrayOf(1200f, 540f)
        val window = floatArrayOf(*page)
        PageTurn.matrix(270, 2400, 1080, 320, 640).mapPoints(window)

        val back = floatArrayOf(*window)
        PageTurn.inverse(270, 2400, 1080, 320, 640).mapPoints(back)

        assertEquals(1200f, back[0], 0.01f)
        assertEquals(540f, back[1], 0.01f)
    }

    /**
     * The regression: a tap where the box is **drawn**.
     *
     * The box is in the page's top-right (page 2400×1080); turned 270° into the 320×640 window it is
     * drawn at the window's top-left, around x 0..89 and y 53..373. A tap at 40,200 is inside what the
     * user sees. Tested against the page coordinates of the window — the code before this turn — 40,200
     * is nowhere near the box, so the picker would read the tap as «outside» and close.
     */
    @Test
    fun `a tap lands on the box it covers on a turned page`() {
        var picked = -1
        var cancelled = false
        val overlay = TextPickerOverlay(context).apply {
            onPick = { picked = it }
            onCancel = { cancelled = true }
            show(
                listOf(PickerBlock(Rect(1000, 0, 2200, 300))),
                PickerReadingMode.OFF,
                Bitmap.createBitmap(2400, 1080, Bitmap.Config.ARGB_8888),
                turn = 270,
            )
            measure(
                View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY),
            )
            layout(0, 0, 320, 640)
        }

        val down = MotionEvent.obtain(0L, 10L, MotionEvent.ACTION_DOWN, 40f, 200f, 0)
        overlay.dispatchTouchEvent(down)
        down.recycle()
        val up = MotionEvent.obtain(0L, 20L, MotionEvent.ACTION_UP, 40f, 200f, 0)
        overlay.dispatchTouchEvent(up)
        up.recycle()

        assertEquals("a tap on the drawn box missed it", 0, picked)
        assertFalse("a tap inside a box is not a cancel", cancelled)
    }

    /**
     * The drawing takes the turn too, not just the touch: the page's top-left quarter is red, and
     * after a 270° turn it is at the window's bottom-left — where the mango's top corner is for
     * someone holding the phone on its side.
     */
    @Test
    fun `the picture is drawn turned into the window`() {
        val page = Bitmap.createBitmap(240, 108, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.BLUE)
        }
        Canvas(page).drawRect(0f, 0f, 120f, 54f, Paint().apply { color = Color.RED })
        val overlay = TextPickerOverlay(context).apply {
            show(emptyList<PickerBlock>(), PickerReadingMode.OFF, page, turn = 270)
            measure(
                View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY),
            )
            layout(0, 0, 320, 640)
        }

        val drawn = Bitmap.createBitmap(320, 640, Bitmap.Config.ARGB_8888)
        overlay.pageView.draw(Canvas(drawn))

        assertEquals(
            "the page's top-left quarter is not at the window's bottom-left: the page was not turned",
            Color.RED,
            drawn.getPixel(80, 480),
        )
        assertEquals(
            "the rest of the page did not follow the quarter",
            Color.BLUE,
            drawn.getPixel(250, 120),
        )
    }

    /**
     * The turn itself: none whenever the page already has the phone's frame, and otherwise the side
     * the picture's top has to point at — right when the phone's right side is up, left when its left
     * side is. A table the wrong way round draws every page upside down, which reads as «the photo is
     * sideways» all over again.
     */
    @Test
    fun `the page only turns when the window did not turn with the phone`() {
        assertEquals(
            "a portrait window with the phone's left side up",
            270,
            DeviceRotation.pageTurn(Surface.ROTATION_270, portraitWindow = true),
        )
        assertEquals(
            "a portrait window with the phone's right side up",
            90,
            DeviceRotation.pageTurn(Surface.ROTATION_90, portraitWindow = true),
        )
        assertEquals("upright", 0, DeviceRotation.pageTurn(Surface.ROTATION_0, portraitWindow = true))
        assertEquals("upside down", 0, DeviceRotation.pageTurn(Surface.ROTATION_180, portraitWindow = true))
        assertEquals(
            "a landscape window already has the phone's frame",
            0,
            DeviceRotation.pageTurn(Surface.ROTATION_270, portraitWindow = false),
        )
        assertEquals(0, DeviceRotation.pageTurn(Surface.ROTATION_90, portraitWindow = false))
    }

    /**
     * R48h: the camera screen shows the picture in the phone's frame too.
     *
     * The preview and the frozen photo were fitted to the window, so with the rotation locked they
     * were an 810-pixel strip with the page on its side — and the picker's page, which is drawn in the
     * phone's frame, then made the picture grow and turn. Both are laid out as the frame and turned
     * into the window now, so the three of them are one rectangle.
     */
    @Test
    fun `the camera screen shows the picture in the phone's frame`() {
        // The preview and the frozen photo get the rectangle the picker's page has, turned into the
        // window — instead of being fitted to the window, which gave the 810-pixel strip.
        val preview = View(context)
        val photo = View(context)
        val root = FrameLayout(context)
        root.addView(preview)
        root.addView(photo)

        placeInPhoneFrame(listOf(preview, photo), 1080, 2400, turn = 270)

        assertEquals("the preview still has the window's width", 2400, preview.layoutParams.width)
        assertEquals(1080, preview.layoutParams.height)
        assertEquals("the preview was not turned into the window", 270f, preview.rotation, 0.01f)
        assertEquals("the photo does not have the preview's frame", 2400, photo.layoutParams.width)
        assertEquals(1080, photo.layoutParams.height)
        assertEquals("the photo was not turned into the window", 270f, photo.rotation, 0.01f)

        // A screen that turned with the phone already has the frame: the window itself.
        placeInPhoneFrame(listOf(preview), 1080, 2400, turn = 0)
        assertEquals(1080, preview.layoutParams.width)
        assertEquals(2400, preview.layoutParams.height)
        assertEquals(0f, preview.rotation, 0.01f)

        val camera = source("app/src/main/java/dev/yabai/ocr/CameraActivity.kt")
        assertTrue(
            "the camera screen never lays the picture out in the phone's frame",
            camera.contains("placeInPhoneFrame("),
        )
        assertTrue(
            "the frame is laid out once, so turning the phone would not move the picture",
            camera.split("applyPhotoFrame()").size > 3,
        )

        // The numbers the two screens share: the photo on the frozen ImageView and the same photo on
        // the picker's page are one rectangle in the window, not the 810-pixel strip.
        val frame = PhotoPageGeometry.frame(1080, 2400, turn = 270)
        val layout = requireNotNull(PhotoPageGeometry.fit(4000, 3000, frame.width, frame.height))
        val onWindow = RectF(layout.photoBounds)
        PageTurn.matrix(270, frame.width, frame.height, 1080, 2400).mapRect(onWindow)

        assertEquals("the picture is not as wide as the window", 1080f, onWindow.width(), 0.5f)
        assertEquals("the picture is not as tall as the page puts it", 1440f, onWindow.height(), 0.5f)
        assertEquals("the picture is not centred", 480f, onWindow.top, 0.5f)
        assertEquals(1920f, onWindow.bottom, 0.5f)
    }

    /**
     * R48h: the actions and the hint follow the page.
     *
     * Left in the window's frame they lay on their side along the window's bottom, which for a
     * reader holding the phone on its side is not their bottom at all. Turned with the page and
     * moved to the edges the reader has at the top and the bottom, they stay readable and on screen —
     * a control turned without being moved would hang half off the window.
     */
    @Test
    fun `the actions and the hint turn with the page and stay on the screen`() {
        // Upright: the bar under the page, the hint above it, neither turned nor moved.
        val upright = picker(0)
        assertEquals(0f, upright.actionBar.rotation, 0.01f)
        assertEquals(0f, upright.hintView.rotation, 0.01f)
        assertTrue(
            "the actions are not at the bottom of an upright page",
            visualBounds(upright.actionBar).centerY() > 640f * 0.8f,
        )

        val turned = picker(270)
        val bar = turned.actionBar
        val hint = turned.hintView
        assertEquals("the actions are still lying on their side", 270f, bar.rotation, 0.01f)
        assertEquals("the hint is still lying on its side", 270f, hint.rotation, 0.01f)

        val barBounds = visualBounds(bar)
        val hintBounds = visualBounds(hint)
        assertEquals(
            "the actions are not 16 dp from the reader's bottom: $barBounds",
            320f - 16f,
            barBounds.right,
            1f,
        )
        assertEquals(
            "the hint is not 48 dp from the reader's top: $hintBounds",
            48f,
            hintBounds.left,
            1f,
        )
        assertTrue("the actions hang off the window: $barBounds", barBounds.top >= 0f && barBounds.bottom <= 640f)
        assertTrue("the hint hangs off the window: $hintBounds", hintBounds.top >= 0f && hintBounds.bottom <= 640f)
    }

    /**
     * R48i: the home screen and the list of apps close the picture.
     *
     * A photo belongs to the camera screen: the file is in the cache and the reading belongs to the
     * panel behind the bubble, so a camera left in the background would be a screen waiting for a
     * user who has gone elsewhere — which is what the picker and the panels already do.
     */
    @Test
    fun `the home screen or the list of apps closes the picture`() {
        val camera = source("app/src/main/java/dev/yabai/ocr/CameraActivity.kt")
        assertTrue(
            "the camera screen is left waiting when the user goes to the home screen",
            camera.contains("override fun onUserLeaveHint()"),
        )
        val leaving = camera.substringAfter("override fun onUserLeaveHint()").substringBefore("\n    }")
        .substringBefore("\r\n    }")
        assertTrue("leaving the camera does not close it: $leaving", leaving.contains("finish()"))
    }

    /**
     * R48j: the camera permission dialog is not the home screen.
     *
     * The system announces a leave when the permission dialog is put up over this screen, and the
     * screen closed on it: the user answered a dialog while the activity behind it was already going
     * away, so the permission had nowhere to land and the picture was never taken. Only a camera that
     * is already running is something to leave.
     */
    @Test
    fun `the camera permission dialog does not close the screen`() {
        FloatingOcrService.isRunning = true
        // The state this is about: no permission yet, so onCreate puts the dialog up.
        shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(Manifest.permission.CAMERA)
        val controller = Robolectric.buildActivity(CameraActivity::class.java)
        try {
            val camera = controller.create().get()
            // Protected, and driving the real callback on a created activity is the point: the source
            // assertions below cannot tell whether the dialog actually reaches this screen.
            val leave = CameraActivity::class.java
                .getDeclaredMethod("onUserLeaveHint")
                .apply { isAccessible = true }
            leave.invoke(camera)

            assertFalse(
                "the permission dialog closed the camera screen before there was a camera",
                camera.isFinishing,
            )

            // The other half: a running camera is still closed when the user goes to the home screen.
            val source = source("app/src/main/java/dev/yabai/ocr/CameraActivity.kt")
            val bound = source.substringAfter("provider.bindToLifecycle(")
            assertTrue(
                "the screen is never marked as running, so the home button would leave the camera up",
                bound.substringBefore("shutter.isEnabled = true").contains("cameraRunning = true"),
            )
            assertTrue(
                "going home no longer closes a running camera",
                source.contains("if (cameraRunning) finish()"),
            )
        } finally {
            runCatching { controller.destroy() }
            FloatingOcrService.isRunning = false
        }
    }

    /**
     * R48j: one measurement of the screen for both sides.
     *
     * The camera laid the preview and the frozen photo out from `resources.displayMetrics` — the area
     * an app draws in, with the system bars already taken out — while the page was measured from the
     * whole display. The two rectangles differed by a bar, so the picture moved when the picker
     * opened. Both now take the display's own size, from the same place.
     */
    @Test
    fun `the camera screen and the page measure the screen the same way`() {
        val camera = source("app/src/main/java/dev/yabai/ocr/CameraActivity.kt")
        val frame = camera.substringAfter("private fun applyPhotoFrame()")
            .substringBefore("private fun matchParent")
        assertTrue(
            "the camera still fits the picture to the area the app draws in, not to the display",
            frame.contains("ScreenMetrics.real(this)") &&
                !frame.contains("resources.displayMetrics.widthPixels") &&
                !frame.contains("resources.displayMetrics.heightPixels"),
        )

        val service = source("app/src/main/java/dev/yabai/ocr/service/FloatingOcrService.kt")
        assertTrue(
            "the page is measured another way than the camera screen",
            service.contains("private fun realDisplayMetrics(): DisplayMetrics = ScreenMetrics.real(this)"),
        )

        val screen = ScreenMetrics.real(context)
        assertTrue("the display says nothing", screen.widthPixels > 0 && screen.heightPixels > 0)
        val page = PhotoPageGeometry.frame(screen.widthPixels, screen.heightPixels, turn = 270)
        val fitted = requireNotNull(PhotoPageGeometry.fit(4000, 3000, page.width, page.height))
        assertEquals("the page is not the display's frame", screen.heightPixels, page.width)
        assertEquals(
            "the photo is not fitted to the rectangle the camera lays out",
            screen.widthPixels,
            fitted.drawnHeight,
        )
    }

    /** The picker as a test sees it: a page in the phone's frame, shown [degrees] into the window. */
    private fun picker(degrees: Int): TextPickerOverlay = TextPickerOverlay(context).apply {
        show(
            emptyList<PickerBlock>(),
            PickerReadingMode.OFF,
            Bitmap.createBitmap(640, 320, Bitmap.Config.ARGB_8888),
            turn = degrees,
        )
        measure(
            View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY),
        )
        layout(0, 0, 320, 640)
    }

    /** Where a turned control ends up on screen: its box, turned about its own centre. */
    private fun visualBounds(view: View): RectF {
        val halfWidth = if (view.rotation % 180f == 0f) view.width / 2f else view.height / 2f
        val halfHeight = if (view.rotation % 180f == 0f) view.height / 2f else view.width / 2f
        val centerX = view.left + view.width / 2f + view.translationX
        val centerY = view.top + view.height / 2f + view.translationY
        return RectF(centerX - halfWidth, centerY - halfHeight, centerX + halfWidth, centerY + halfHeight)
    }

    /**
     * The turn has to come from the phone and reach the picker: the photo's page is built in the
     * phone's frame, and the camera says which frame that is.
     */
    @Test
    fun `the camera tells the picker which frame the photo was taken in`() {
        val camera = source("app/src/main/java/dev/yabai/ocr/CameraActivity.kt")
        assertTrue(
            "the page's turn does not come from how the phone is held",
            camera.contains("DeviceRotation.pageTurn("),
        )
        assertTrue(
            "the phone's frame is never compared with the window's",
            camera.contains("Configuration.ORIENTATION_PORTRAIT"),
        )
        assertTrue(
            "the turn never reaches the service, which is the one drawing the page",
            camera.contains("FloatingOcrService.EXTRA_PHOTO_TURN"),
        )

        val service = source("app/src/main/java/dev/yabai/ocr/service/FloatingOcrService.kt")
        assertTrue(
            "the photo's page is not built in the phone's frame",
            service.contains("PhotoPageGeometry.frame("),
        )
        assertTrue(
            "the picker is not told which way the page goes",
            service.contains("turn = photoTurn"),
        )
        assertTrue(
            "a second look at the boxes would draw the page unturned",
            service.contains("pendingPageTurn = photoTurn") &&
                service.contains("turn = if (pendingPage != null) pendingPageTurn else 0"),
        )
    }

    private fun source(path: String): String {
        val found = sequenceOf(File(path), File("../$path"))
            .firstOrNull { it.isFile }?.readText().orEmpty()
        assertTrue("$path not found", found.isNotEmpty())
        return found
    }
}
