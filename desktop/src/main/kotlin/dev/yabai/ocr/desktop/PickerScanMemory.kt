package dev.yabai.ocr.desktop

/**
 * The scan a reopened picker may reuse, and the rule that makes a finished scan worth keeping.
 *
 * «Trechos» reopens the picker on the same screenshot: recognising the page again would cost a
 * second Lens round trip and could move the boxes the user was already reading. Two rules keep that
 * shortcut honest, and both live here because the picker window cannot be driven in a unit test:
 *
 * 1. The scan belongs to the reading mode and dictionary filter it was made with, so it is handed
 *    out **once**. A mode change inside the picker has to reach the recogniser again.
 * 2. A scan that failed is never kept: reopening after a failure has to retry, not repeat the same
 *    error forever.
 */
internal class PickerScanMemory(initial: PickerScan? = null) {
    private var pending: PickerScan.Ready? = reusablePickerScan(initial)

    /** The scan to show without recognising the screen again; null the second time it is asked. */
    fun consume(): PickerScan.Ready? {
        val next = pending
        pending = null
        return next
    }
}

/** Only a completed scan describes the page; a failure or an in-flight scan cannot be reused. */
internal fun reusablePickerScan(scanned: PickerScan?): PickerScan.Ready? =
    scanned.takeIf { it is PickerScan.Ready } as PickerScan.Ready?
