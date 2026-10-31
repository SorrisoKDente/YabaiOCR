package dev.yabai.ocr.overlay

import android.content.Context
import android.content.Intent
import android.os.Looper
import android.view.View
import android.view.WindowManager
import dev.yabai.ocr.ai.AskAi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowSettings
import org.robolectric.shadows.ShadowWindowManagerImpl
import java.time.Duration

/**
 * Tests for HOME and recents closing the panel (R40).
 *
 * R9 used the window focus, and on the phone it did not work: a `TYPE_APPLICATION_OVERLAY` window is
 * drawn above the launcher and the recents screen, so it keeps the input focus and
 * `onWindowFocusChanged` never fires. The system does send [Intent.ACTION_CLOSE_SYSTEM_DIALOGS] with
 * the reason, to every window of the app, and that is what these tests drive: the panel and anything
 * this app drew over it go together, and the service is told exactly once.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w320dp-h640dp-mdpi")
class SystemDialogTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun `o home fecha o painel e avisa o servico`() {
        val callbacks = RecordingCallbacks()
        openPanel(callbacks)

        systemDialog("homekey")

        assertTrue("the panel was left floating over the launcher", panelOnScreen() == null)
        assertEquals("the service was not told", listOf("result dismissed"), callbacks.calls)
    }

    @Test
    fun `o botao de apps recentes fecha o painel`() {
        val callbacks = RecordingCallbacks()
        openPanel(callbacks)

        systemDialog("recentapps")

        assertTrue("the panel was left over the recents screen", panelOnScreen() == null)
        assertEquals("the service was not told", listOf("result dismissed"), callbacks.calls)
    }

    @Test
    fun `o painel da busca fecha do mesmo jeito`() {
        val callbacks = RecordingCallbacks()
        val controller = controller(callbacks)
        controller.showDictionarySearch(listOf("JMdict"), null, 1)
        assertTrue("the search card did not open", controller.isResultVisible())

        systemDialog("homekey")

        assertTrue("the search card was left over the launcher", panelOnScreen() == null)
        assertEquals(listOf("result dismissed"), callbacks.calls)
    }

    /** The power menu is not the user walking away: the panel is still there when it closes. */
    @Test
    fun `um motivo que nao e sair nao fecha o painel`() {
        val callbacks = RecordingCallbacks()
        val controller = openPanel(callbacks)

        systemDialog("globalactions")

        assertTrue("the panel went away with the power menu", controller.isResultVisible())
        assertTrue("the service was told about a panel that is still there", callbacks.calls.isEmpty())
    }

    /**
     * The list of apps is drawn by this app over the panel. Left alone behind the launcher it would be
     * an orphan window with nothing to answer to.
     */
    @Test
    fun `a lista de apps vai junto`() {
        val callbacks = RecordingCallbacks()
        val controller = openPanel(callbacks)
        controller.showAskAiApps(listOf(AskAi.Target("ChatGPT", "com.openai.chatgpt")), SENTENCE)
        assertTrue("the list of apps did not open", windows().any { it is AppPickerOverlay })

        systemDialog("homekey")

        assertTrue("the panel stayed", !controller.isResultVisible())
        assertTrue(
            "the list of apps stayed over the launcher",
            windows().none { it is AppPickerOverlay },
        )
        assertEquals("the panel and the list were counted twice", 1, callbacks.calls.size)
    }

    /**
     * HOME pressed twice, or HOME plus the focus loss R9 still watches, or both the broadcast and the
     * window callback, must not tell the service about a panel it has already forgotten — closing
     * once is closing once.
     */
    @Test
    fun `o servico e avisado uma vez so`() {
        val callbacks = RecordingCallbacks()
        openPanel(callbacks)
        val panel = panelOnScreen() ?: error("the controller did not put a result panel on screen")

        systemDialog("homekey")
        windowDialog(panel, "homekey")
        panel.onWindowFocusChanged(false)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400))
        systemDialog("recentapps")

        assertEquals(listOf("result dismissed"), callbacks.calls)
    }

    /**
     * The second route: the window manager calls `closeSystemDialogs(reason)` on every window, and
     * `ViewRootImpl` hands the reason to the root view. This is the one that does not depend on a
     * broadcast being delivered; it is driven here the way the framework does it, through the
     * `View` method the panel shadows.
     */
    @Test
    fun `o sistema fecha o painel pela janela`() {
        val callbacks = RecordingCallbacks()
        openPanel(callbacks)
        val panel = panelOnScreen() ?: error("the controller did not put a result panel on screen")

        windowDialog(panel, "homekey")

        assertTrue("the panel was left floating over the launcher", panelOnScreen() == null)
        assertEquals(listOf("result dismissed"), callbacks.calls)
    }

    /** The window callback carries the same reasons, and the power menu is filtered out there too. */
    @Test
    fun `pela janela o menu de energia tambem nao fecha`() {
        val callbacks = RecordingCallbacks()
        val controller = openPanel(callbacks)
        val panel = panelOnScreen() ?: error("the controller did not put a result panel on screen")

        windowDialog(panel, "globalactions")

        assertTrue("the panel went away with the power menu", controller.isResultVisible())
        assertTrue("the service was told about a panel that is still there", callbacks.calls.isEmpty())
    }

    /** After a panel goes, the next one has to be heard again: the listener must not be left dead. */
    @Test
    fun `um painel aberto depois volta a escutar`() {
        val callbacks = RecordingCallbacks()
        val controller = openPanel(callbacks)
        systemDialog("homekey")

        showResult(controller)
        systemDialog("homekey")

        assertFalse("the second panel ignored HOME", controller.isResultVisible())
        assertEquals(2, callbacks.calls.count { it == "result dismissed" })
    }

    /** After a panel goes, the next one still hears the window callback, not just the broadcast. */
    @Test
    fun `um painel aberto depois volta a ouvir a janela`() {
        val callbacks = RecordingCallbacks()
        val controller = openPanel(callbacks)
        windowDialog(panelOnScreen()!!, "recentapps")

        showResult(controller)
        windowDialog(panelOnScreen()!!, "recentapps")

        assertFalse("the second panel ignored the window callback", controller.isResultVisible())
        assertEquals(2, callbacks.calls.count { it == "result dismissed" })
    }

    // --- the panel under test -------------------------------------------------------------------

    private fun controller(callbacks: RecordingCallbacks): OverlayController {
        ShadowSettings.setCanDrawOverlays(true)
        return OverlayController(context).apply { this.callbacks = callbacks }
    }

    private fun openPanel(callbacks: RecordingCallbacks): OverlayController =
        controller(callbacks).also { showResult(it) }

    private fun showResult(controller: OverlayController) {
        controller.showResult(
            text = SENTENCE,
            meta = "Google Lens · 2348 ms · ja",
            notes = emptyList(),
            offlineAvailable = true,
            showLines = false,
            lines = emptyList(),
            canRepick = true,
            canAskAi = true,
        )
    }

    /**
     * What the system sends when HOME or recents is pressed — see
     * [Intent.ACTION_CLOSE_SYSTEM_DIALOGS].
     */
    private fun systemDialog(reason: String) {
        context.sendBroadcast(
            Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS).putExtra("reason", reason),
        )
        shadowOf(Looper.getMainLooper()).idle()
    }

    /**
     * What the window manager does when HOME or recents is pressed: it calls
     * `IWindow.closeSystemDialogs(reason)` on every window, and the view root hands the reason to
     * the window's root view. `View.onCloseSystemDialogs` is `@hide`, so it is reached by reflection
     * here — the call itself is virtual, exactly like the framework's.
     */
    private fun windowDialog(panel: View, reason: String) {
        View::class.java
            .getMethod("onCloseSystemDialogs", String::class.java)
            .invoke(panel, reason)
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun panelOnScreen(): ResultOverlay? = windows().filterIsInstance<ResultOverlay>().firstOrNull()

    private fun windows(): List<View> {
        val manager = context.getSystemService(WindowManager::class.java)
        return Shadow.extract<ShadowWindowManagerImpl>(manager).views
    }

    private companion object {
        const val SENTENCE = "職員さんに聞いた"
    }
}
