package dev.yabai.ocr

import android.content.Context
import android.content.Intent
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import dev.yabai.ocr.overlay.ResultOverlay
import dev.yabai.ocr.service.LookupOverlayService
import java.time.Duration
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
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowSettings
import org.robolectric.shadows.ShadowWindowManagerImpl

/**
 * The word from another app in a panel over the reader (R37).
 *
 * The first integration (R35e) opened a screen of its own. It worked, but it took the page away from
 * the reader, while Google Tradutor and Takoboto keep it behind their answer. The fix is to reuse
 * the bubble's panel: a `TYPE_APPLICATION_OVERLAY` window owned by a small service, closed by the
 * same tap outside the card, and gone when the panel goes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w320dp-h640dp-mdpi")
class LookupOverlayTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    /** Enough for the doorway delay plus whatever the panel posts after it. */
    private val settleWindow = Duration.ofMillis(LookupOverlayService.DOORWAY_DELAY_MS + 400)

    @Test
    fun `a palavra abre o painel flutuante com a consulta`() {
        val service = start("波及")

        val panel = windows().filterIsInstance<ResultOverlay>().singleOrNull()
            ?: error("the service did not put a panel on screen")
        assertEquals("the query did not reach the panel", "波及", field(panel).text.toString())
        val params = panel.layoutParams as WindowManager.LayoutParams
        assertEquals(
            "the panel window does not cover the screen, so a tap outside is lost",
            WindowManager.LayoutParams.MATCH_PARENT,
            params.height,
        )
        assertFalse("the service stopped while the panel was up", shadowOf(service).isStoppedBySelf)

        service.onDestroy()
    }

    @Test
    fun `fechar o painel encerra o servico`() {
        val service = start("波及")
        val panel = windows().filterIsInstance<ResultOverlay>().single()

        panel.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))

        assertTrue("the service stayed alive with no panel", shadowOf(service).isStoppedBySelf)
        assertTrue(
            "the panel window stayed behind",
            windows().filterIsInstance<ResultOverlay>().isEmpty(),
        )
    }

    /**
     * The panel has to be born after the doorway is gone (R38).
     *
     * The doorway finishes in the same turn it receives the word, and its task is removed a moment
     * later. A window added in between takes the focus from the doorway and loses it when the reader
     * returns — a focus loss the panel reads as «the user left» (R9), which would close it on the
     * spot. Drawing it after the hand-over means the reader is already the app behind the panel.
     */
    @Test
    fun `o painel espera a porta sair de cena`() {
        val service = start("波及", settle = false)

        shadowOf(Looper.getMainLooper())
            .idleFor(Duration.ofMillis(LookupOverlayService.DOORWAY_DELAY_MS / 2))
        assertTrue(
            "the panel was drawn while the doorway's task was still in front",
            windows().filterIsInstance<ResultOverlay>().isEmpty(),
        )

        shadowOf(Looper.getMainLooper())
            .idleFor(Duration.ofMillis(LookupOverlayService.DOORWAY_DELAY_MS))
        assertEquals(
            "the panel never came",
            1,
            windows().filterIsInstance<ResultOverlay>().size,
        )

        service.onDestroy()
    }

    // --- helpers --------------------------------------------------------------------------------

    private fun start(text: String, settle: Boolean = true): LookupOverlayService {
        ShadowSettings.setCanDrawOverlays(true)
        val intent = Intent(context, LookupOverlayService::class.java)
            .putExtra(LookupOverlayService.EXTRA_TEXT, text)
        val service = Robolectric.buildService(LookupOverlayService::class.java, intent).create().get()
        service.onStartCommand(intent, 0, 1)
        if (settle) shadowOf(Looper.getMainLooper()).idleFor(settleWindow)
        return service
    }

    private fun field(panel: ViewGroup): EditText {
        val found = ArrayList<EditText>()
        fun walk(group: ViewGroup) {
            for (index in 0 until group.childCount) {
                when (val child = group.getChildAt(index)) {
                    is EditText -> found.add(child)
                    is ViewGroup -> walk(child)
                }
            }
        }
        walk(panel)
        assertTrue("the panel has no search field", found.isNotEmpty())
        return found.first()
    }

    /** Everything on screen, the way the window manager sees it. */
    private fun windows(): List<View> {
        val manager = context.getSystemService(WindowManager::class.java)
        return Shadow.extract<ShadowWindowManagerImpl>(manager).views
    }
}
