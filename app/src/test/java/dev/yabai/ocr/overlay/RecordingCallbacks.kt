package dev.yabai.ocr.overlay

import android.graphics.Rect

/**
 * An [OverlayController.Callbacks] that only writes down what it was asked to do.
 *
 * The panels are built in code, so their tests have nothing to look at but the effect of a tap. A
 * shared recorder keeps that effect readable — and keeps the two test classes that drive the bubble
 * from drifting apart — instead of each one growing its own anonymous object.
 *
 * Every call appends one line, in the order it happened; assertions are on that list.
 */
class RecordingCallbacks(private val asked: MutableList<String> = ArrayList()) :
    OverlayController.Callbacks {

    /** What the controller asked for, oldest first. */
    val calls: List<String> get() = asked

    override fun onFullScreenRequested() {
        asked.add("full screen")
    }

    override fun onRegionRequested() {
        asked.add("region")
    }

    override fun onRegionConfirmed(rect: Rect?) {
        asked.add("region confirmed")
    }

    override fun onRegionCancelled() {
        asked.add("region cancelled")
    }

    override fun onStopRequested() {
        asked.add("stop")
    }

    override fun onOpenSettingsRequested() {
        asked.add("settings")
    }

    override fun onCopyRequested(text: String, closeAfter: Boolean) {
        asked.add("copy$text")
    }

    override fun onAnalyseRequested(text: String) {
        asked.add("analyse$text")
    }

    override fun onRetryOfflineRequested() {
        asked.add("retry offline")
    }

    override fun onResultDismissed() {
        asked.add("result dismissed")
    }

    override fun onBlockPicked(index: Int) {
        asked.add("block $index")
    }

    override fun onPickAllRequested() {
        asked.add("pick all")
    }

    override fun onRepickRequested() {
        asked.add("repick")
    }

    override fun onImageToggleRequested() {
        asked.add("image")
    }

    override fun onAskAiRequested(text: String) {
        asked.add("ask ai$text")
    }

    override fun onAskAiToRequested(packageName: String, text: String) {
        asked.add("ask ai to $packageName$text")
    }

    override fun onDictionaryPicked(title: String?) {
        asked.add("dictionary ${title ?: "todos"}")
    }
}
