package dev.yabai.ocr.desktop

import dev.yabai.ocr.core.lens.LensResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * «Trechos» reopens the picker on the screenshot it already scanned. These tests pin the two rules
 * that keep that reuse honest, outside the Compose window that cannot be driven headless.
 */
class PickerScanMemoryTest {

    private fun ready(vararg texts: String): PickerScan.Ready = PickerScan.Ready(
        result = LensResult("ja", emptyList(), emptyList(), elapsedMs = 1L),
        blocks = texts.map { PickerBlock(it, box = null, lines = emptyList()) },
    )

    @Test
    fun `the scan from the previous visit is handed out once`() {
        val scan = ready("迎え入れる")
        val memory = PickerScanMemory(scan)

        assertSame("the reopened picker has to open on the boxes already shown", scan, memory.consume())
        // A reading-mode change reruns the scan effect; reusing the old scan there would show boxes
        // from the previous mode as if they belonged to the new one.
        assertNull("a second visit must recognise the screen again", memory.consume())
    }

    @Test
    fun `a scan that failed is never reused`() {
        val scan = PickerScan.Failed("Lens não respondeu")
        val memory = PickerScanMemory(scan)

        assertNull("reopening after a failure has to retry", memory.consume())
    }

    @Test
    fun `an in-flight scan is never reused`() {
        assertNull(reusablePickerScan(PickerScan.Scanning))
    }

    @Test
    fun `only a completed scan is worth keeping`() {
        val scan = ready("救い")
        assertSame(scan, reusablePickerScan(scan))
        assertNull(reusablePickerScan(PickerScan.Failed("sem rede")))
        assertNull(reusablePickerScan(null))
    }

    @Test
    fun `a reopened picker keeps the blocks it was given`() {
        val scan = ready("振り向かせて", "絡む")
        val reused = PickerScanMemory(scan).consume()

        assertTrue("the reused scan must still be the ready one", reused != null)
        assertEquals(listOf("振り向かせて", "絡む"), reused?.blocks?.map { it.text })
    }
}
