package dev.yabai.ocr.desktop

import dev.yabai.ocr.core.dict.LookupEngine
import java.awt.Point
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Two lifecycle rules the hover learned the hard way:
 *
 * 1. Turning it off must let the dictionaries go (123 MB of key index on a real install), or an app
 *    that only sits in the tray keeps the whole cost of a feature the reader turned off.
 * 2. On a compositor bridge the pointer events only arrive when the hand moves, so the gate that
 *    waits out a failed read (or a dismissal grace period) had no clock behind it: the word the
 *    pointer was resting on stayed unread until the pointer was moved off and back.
 */
class HoverOcrManagerLifecycleTest {

    private object NoPointer : PointerSource {
        override fun current(): Point? = null
    }

    private fun manager(
        backendKind: HoverBackendKind = HoverBackendKind.AWT,
        pointerSource: PointerSource = NoPointer,
        cache: HoverDictionaryCache = HoverDictionaryCache({ null }, {}),
        capture: ((java.awt.Rectangle) -> java.awt.image.BufferedImage)? = null,
        releaseOcrSessions: () -> Unit = {},
        trimHeap: () -> Unit = {},
        // These tests are about releasing what the hover opened, not about the dictionary gate:
        // a real empty dictionary folder would refuse to start it (R57).
        installedDictionaryTitles: () -> List<String> = { listOf("Test") },
    ) = HoverOcrManager(
        onResultsReady = { _, _, _, _ -> },
        onDismiss = {},
        backendKind = backendKind,
        pointerSource = pointerSource,
        captureProvider = capture,
        dictionaryCache = cache,
        releaseOcrSessions = releaseOcrSessions,
        trimHeap = trimHeap,
        dictionaryTitles = installedDictionaryTitles,
    )

    @Test
    fun turningTheHoverOffReleasesTheDictionaries() {
        val releases = AtomicInteger()
        val cache = HoverDictionaryCache(
            opener = { LookupEngine(emptyList()) },
            closer = { releases.incrementAndGet() },
        )
        // The engine the hover opened while it was on.
        cache.get(DesktopSettings())
        val hover = manager(cache = cache)

        hover.setEnabled(true)
        hover.setEnabled(false)

        // Before close(): closing the app is a different moment and releases the engine anyway.
        assertEquals("the engine must be released when the hover is turned off", 1, releases.get())
        hover.close()
    }

    @Test
    fun turningTheHoverOffAlsoReleasesTheOfflineReaderSessions() {
        val released = AtomicInteger()
        val hover = manager(releaseOcrSessions = { released.incrementAndGet() })

        hover.setEnabled(true)
        hover.setEnabled(false)

        // The release runs off the UI thread, because closing a session waits for a read in flight.
        val deadline = System.currentTimeMillis() + 3_000
        while (released.get() < 1 && System.currentTimeMillis() < deadline) Thread.sleep(20)
        assertEquals("the ONNX sessions must not outlive the hover", 1, released.get())
        hover.close()
    }

    @Test
    fun turningTheHoverOffTrimsTheHeap() {
        val trims = AtomicInteger()
        val hover = manager(trimHeap = { trims.incrementAndGet() })

        hover.setEnabled(true)
        hover.setEnabled(false)

        // The trim runs after the card is gone, off the UI thread; it is what makes the RSS follow
        // the release instead of staying at the high-water mark of the last hover (D95).
        val deadline = System.currentTimeMillis() + 5_000
        while (trims.get() < 1 && System.currentTimeMillis() < deadline) Thread.sleep(20)
        assertEquals("turning the hover off must hand the heap back", 1, trims.get())
        hover.close()
    }

    @Test
    fun aBridgeBackendRetriesAFailedReadWithoutMovingThePointer() {
        val captures = AtomicInteger()
        val pointer = BridgePointerSource()
        pointer.update(Point(300, 200))
        val hover = manager(
            backendKind = HoverBackendKind.GNOME,
            pointerSource = pointer,
            capture = {
                captures.incrementAndGet()
                throw IllegalStateException("captura falhou de propósito")
            },
        )

        hover.setEnabled(true)
        try {
            val deadline = System.currentTimeMillis() + 5_000
            while (captures.get() < 2 && System.currentTimeMillis() < deadline) Thread.sleep(50)
            assertTrue(
                "the retry clock must keep ticking on the bridge without pointer events " +
                    "(captures=${captures.get()})",
                captures.get() >= 2,
            )
        } finally {
            hover.close()
        }
    }
}
