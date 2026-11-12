package dev.yabai.ocr.overlay

/**
 * Whether a capture stops at the picker instead of opening the result panel.
 *
 * A whole-screen capture is a page, not a text: the recognised paragraphs are drawn as boxes and
 * the user picks the one to read — which is what Lens and ML Kit already do here. The rule that
 * used to live inline in the capture pipeline also required **more than one** block, so a page with
 * a single balloon (or an engine that reported the page as one box) skipped the picker and opened
 * the panel straight away, as if the user had chosen the whole screen.
 *
 * A region capture is already a selection and a picker over it would only ask the same question
 * twice, so it still goes straight to the panel.
 */
object PickerDecision {

    fun offersPicker(wholeScreen: Boolean, pickText: Boolean, placeableBlocks: Int): Boolean =
        wholeScreen && pickText && placeableBlocks > 0
}
