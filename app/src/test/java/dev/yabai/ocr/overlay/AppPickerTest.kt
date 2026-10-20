package dev.yabai.ocr.overlay

import android.content.Context
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import dev.yabai.ocr.R
import dev.yabai.ocr.ai.AskAi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Tests for the window that lists the apps a question can go to.
 *
 * It exists because the list outgrew the panel: a row of chips at the bottom of the card ran past the
 * edge of the screen and the apps below the fold could not be tapped at all. What is held down here is
 * that every app is on the list with its icon, that the list scrolls instead of growing off the screen,
 * and that each way out — a row, the ✕, BACK, a tap outside — ends where it should.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w320dp-h640dp-mdpi")
class AppPickerTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun `every app is listed with its name and its icon`() {
        val picker = picker(APPS)
        layout(picker, 320, 640)

        assertEquals(listOf("ChatGPT", "Gemini", "Claude"), labels(picker))
        for (row in rows(picker)) {
            val icon = icon(row)
            assertNotNull("a row has no icon", icon.drawable)
        }
    }

    @Test
    fun `the list is in the order the apps were given`() {
        val picker = picker(APPS)
        layout(picker, 320, 640)

        assertEquals(APPS.map { it.label }, labels(picker))
    }

    @Test
    fun `a row answers with its package`() {
        val picked = ArrayList<String>()
        val picker = picker(APPS, onPicked = { picked.add(it) })
        layout(picker, 320, 640)

        rowNamed(picker, "Gemini").performClick()

        assertEquals(listOf("com.google.android.apps.bard"), picked)
    }

    /**
     * The list is its own window: closing it must not touch the answer behind it, which is why every
     * way out here only calls back — the controller decides what else goes away.
     */
    @Test
    fun `the cross closes the list`() {
        var dismissed = 0
        val picker = picker(APPS, onDismiss = { dismissed++ })
        layout(picker, 320, 640)

        buttonNamed(picker, "✕").performClick()

        assertEquals(1, dismissed)
    }

    @Test
    fun `a tap outside the card closes the list`() {
        var dismissed = 0
        val picker = picker(APPS, onDismiss = { dismissed++ })
        layout(picker, 320, 640)

        // The root is the whole screen and only the card takes its own taps.
        picker.performClick()

        assertEquals(1, dismissed)
    }

    @Test
    fun `BACK closes the list`() {
        var dismissed = 0
        val picker = picker(APPS, onDismiss = { dismissed++ })
        layout(picker, 320, 640)

        picker.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))

        assertEquals(1, dismissed)
    }

    /**
     * A phone with thirty apps that take text must not produce a card that takes the whole screen: the
     * list scrolls inside a card that keeps to the bottom, the title stays at the top of the card and
     * the last row is still reachable.
     */
    @Test
    fun `a long list scrolls instead of growing off the screen`() {
        val many = (1..30).map { AskAi.Target("App $it", "com.example.app$it") }
        val picker = picker(many)
        layout(picker, 320, 640)

        val card = findByTag(picker, AppPickerOverlay.CARD_TAG)
            ?: error("the list has no card")
        assertTrue(
            "the card is ${card.measuredHeight} px tall on a 640 px screen, leaving nothing of the panel visible",
            card.measuredHeight <= 640 * 3 / 4,
        )

        val list = scrollOf(card as ViewGroup) ?: error("the card has no scrolling list")
        assertTrue(
            "the list does not scroll: content ${list.getChildAt(0).measuredHeight} px in ${list.measuredHeight} px",
            list.getChildAt(0).measuredHeight > list.measuredHeight,
        )
        assertEquals(30, rows(picker).size)
    }

    /**
     * The full list of apps — Gmail, Bluetooth, «Converter para JPEG» — is one tap away from the short
     * one, and the short one comes back: the filter is a guess about names, never a closed door.
     */
    @Test
    fun `the full list is one tap away from the short one`() {
        val all = APPS + AskAi.Target("Bluetooth", "com.android.bluetooth")
        val short = AskAi.relevant(all)
        val picker = picker(short, all)
        layout(picker, 320, 640)

        assertEquals(listOf("ChatGPT", "Gemini", "Claude"), labels(picker))

        buttonNamed(picker, context.getString(R.string.result_ask_ai_all, all.size)).performClick()
        layout(picker, 320, 640)
        assertEquals(listOf("ChatGPT", "Gemini", "Claude", "Bluetooth"), labels(picker))

        buttonNamed(picker, context.getString(R.string.result_ask_ai_only_ai)).performClick()
        layout(picker, 320, 640)
        assertEquals(listOf("ChatGPT", "Gemini", "Claude"), labels(picker))
    }

    /** A list that is already everything it can be has nothing to toggle. */
    @Test
    fun `no footer when there is nothing more to show`() {
        val picker = picker(APPS, APPS)
        layout(picker, 320, 640)

        assertEquals(listOf("✕"), buttons(picker).map { it.text.toString() })
    }

    // --- the picker under test ------------------------------------------------------------------

    private fun picker(
        apps: List<AskAi.Target>,
        all: List<AskAi.Target> = apps,
        onPicked: (String) -> Unit = {},
        onDismiss: () -> Unit = {},
    ): AppPickerOverlay = AppPickerOverlay(context).apply {
        callbacks = object : AppPickerOverlay.Callbacks {
            override fun onAppPicked(packageName: String) = onPicked(packageName)
            override fun onDismiss() = onDismiss()
        }
        show(apps, all)
    }

    // --- helpers --------------------------------------------------------------------------------

    private fun rows(root: ViewGroup): List<View> {
        val found = ArrayList<View>()
        fun walk(group: ViewGroup) {
            for (index in 0 until group.childCount) {
                val child = group.getChildAt(index)
                if (child.tag == AppPickerOverlay.ROW_TAG) found.add(child)
                if (child is ViewGroup) walk(child)
            }
        }
        walk(root)
        return found
    }

    private fun labels(root: ViewGroup): List<String> = rows(root).map { row ->
        val rowGroup = row as ViewGroup
        (0 until rowGroup.childCount)
            .map { rowGroup.getChildAt(it) }
            .filterIsInstance<TextView>()
            .first()
            .text
            .toString()
    }

    private fun rowNamed(root: ViewGroup, label: String): View =
        rows(root).firstOrNull { row ->
            (row as ViewGroup).let { group ->
                (0 until group.childCount)
                    .map { group.getChildAt(it) }
                    .filterIsInstance<TextView>()
                    .any { it.text.toString() == label }
            }
        } ?: error("the list has no row for «$label»")

    private fun icon(row: View): ImageView {
        val group = row as ViewGroup
        for (index in 0 until group.childCount) {
            val child = group.getChildAt(index)
            if (child.tag == AppPickerOverlay.ICON_TAG) return child as ImageView
        }
        error("the row has no icon")
    }

    private fun buttonNamed(root: ViewGroup, label: String): TextView =
        buttons(root).firstOrNull { it.text.toString() == label }
            ?: error("the list has no «$label»")

    private fun buttons(root: ViewGroup): List<TextView> {
        val found = ArrayList<TextView>()
        fun walk(group: ViewGroup) {
            for (index in 0 until group.childCount) {
                val child = group.getChildAt(index)
                if (child is TextView && child.tag == ACTION_BUTTON_TAG) found.add(child)
                if (child is ViewGroup) walk(child)
            }
        }
        walk(root)
        return found
    }

    private fun findByTag(root: ViewGroup, tag: String): View? {
        if (root.tag == tag) return root
        for (index in 0 until root.childCount) {
            val child = root.getChildAt(index)
            if (child is ViewGroup) findByTag(child, tag)?.let { return it }
        }
        return null
    }

    private fun scrollOf(root: ViewGroup): android.widget.ScrollView? {
        for (index in 0 until root.childCount) {
            val child = root.getChildAt(index)
            if (child is android.widget.ScrollView) return child
            if (child is ViewGroup) scrollOf(child)?.let { return it }
        }
        return null
    }

    /** Measures and lays out [view] the way the window manager would. */
    private fun <T : View> layout(view: T, width: Int, height: Int): T {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, width, height)
        return view
    }

    private companion object {
        val APPS = listOf(
            AskAi.Target("ChatGPT", "com.openai.chatgpt"),
            AskAi.Target("Gemini", "com.google.android.apps.bard"),
            AskAi.Target("Claude", "com.anthropic.claude"),
        )
    }
}
