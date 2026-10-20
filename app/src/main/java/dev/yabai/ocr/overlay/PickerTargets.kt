package dev.yabai.ocr.overlay

/**
 * Whether a recognised block is worth offering as something to tap in the picker.
 *
 * The rule used to be "both sides at least one text line high", which threw away every column of
 * vertical manga text: a column is as **thick** as one glyph — around 11 dp on a phone — however long
 * the sentence printed in it is. A balloon of three columns was wide enough to pass and got a box,
 * while the same thing on its own — 「やばい!!」, 「わかった」, 「なにぃ!!」 — did not, which is the
 * bug this exists to prevent.
 *
 * What makes a target tappable is the finger, and the picker already grows every box by [SLOP_DP] on
 * each side when it hit-tests. So a box is offered when its **longest** side is a comfortable target
 * on its own and its **shortest** side is at least half of that, which the growth finishes off: a
 * 11 dp column becomes 19 dp under the finger, a 5 dp sliver stays a sliver.
 */
object PickerTargets {

    /** The same 4 dp the picker inflates a box by when hit-testing. */
    const val SLOP_DP = 4

    fun offered(width: Int, height: Int, minTapSize: Int): Boolean {
        if (width <= 0 || height <= 0) return false
        return maxOf(width, height) >= minTapSize && minOf(width, height) * 2 >= minTapSize
    }
}
