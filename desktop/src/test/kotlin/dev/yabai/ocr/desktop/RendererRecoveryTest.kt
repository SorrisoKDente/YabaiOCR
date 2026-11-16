package dev.yabai.ocr.desktop

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A native crash cannot be caught from inside the JVM, so the app has to notice it *afterwards*.
 *
 * These tests hold the three halves of that: the marker left by a run that never closed, the JVM
 * crash report that tells a real Skiko crash from a process stopped from the outside (Ctrl+C,
 * Task Manager), the ladder the renderer climbs after a real crash (OpenGL before software), and the
 * two ways the user can choose a renderer — the command line, since a packaged app has no JVM flags,
 * and the Settings window, which writes the same preference.
 */
class RendererRecoveryTest {

    private val home: File = Files.createTempDirectory("yabai-renderer").toFile()
    private val crashLogs: File = Files.createTempDirectory("yabai-crash-logs").toFile()

    @After
    fun cleanUp() {
        home.deleteRecursively()
        crashLogs.deleteRecursively()
    }

    private fun recovery() = RendererRecovery(File(home, RUN_MARKER_FILE_NAME), listOf(crashLogs))

    private fun marker(pid: Long? = 4242): File = File(home, RUN_MARKER_FILE_NAME).apply {
        writeText(if (pid == null) "" else "$RUN_MARKER_PID_PREFIX$pid\n")
    }

    private fun crashReport(pid: Long, body: String, at: File = crashLogs): File =
        File(at, "hs_err_pid$pid.log").apply {
            writeText(
                "#\n# A fatal error has been detected by the Java Runtime Environment:\n#\n" +
                    "#  EXCEPTION_ACCESS_VIOLATION (0xc0000005) at pc=0x00007ffc0437c72b\n#\n$body\n",
            )
            // Written after the run started.
            setLastModified(File(home, RUN_MARKER_FILE_NAME).lastModified() + 1_000)
        }

    private val skikoFrame = "# Problematic frame:\n# C  [skiko-windows-x64.dll+0x4c72b]"

    @Test
    fun `a clean exit leaves nothing to notice`() {
        val first = recovery()
        assertFalse("a machine that never ran was reported as crashed", first.previousRun().endedAbnormally)

        first.markRunning()
        assertTrue("a run in progress was not marked", first.previousRun().endedAbnormally)

        recovery().markCleanExit()
        assertFalse("a clean exit left the marker behind", recovery().previousRun().endedAbnormally)
    }

    @Test
    fun `a run stopped from the outside is not a crash`() {
        marker()
        val previous = recovery().previousRun()

        assertTrue("the leftover marker was not noticed", previous.endedAbnormally)
        assertNull("a run that left no crash report was called a crash", previous.skikoCrashReport)
        assertTrue("the run was not recognised as stopped from the outside", previous.stoppedFromOutside)
        assertFalse("Ctrl+C was treated as a Skiko crash", recovery().previousRunCrashed())
    }

    @Test
    fun `the marker names the process whose crash report it should look for`() {
        // The JVM writes `hs_err_pid<pid>.log` after the process dies. The pid in the marker is the
        // only thing that pairs the report with this run instead of one from a reused pid.
        recovery().markRunning()
        val text = File(home, RUN_MARKER_FILE_NAME).readText()
        val pid = ProcessHandle.current().pid()
        assertTrue("the marker does not carry the pid: $text", text.contains("$RUN_MARKER_PID_PREFIX$pid"))

        crashReport(pid, skikoFrame)
        assertTrue("the report of this very process was not found", recovery().previousRunCrashed())
    }

    @Test
    fun `a Skiko crash report marks the previous run as crashed`() {
        marker(4242)
        crashReport(4242, skikoFrame)

        assertTrue("the crash report was not read", recovery().previousRunCrashed())
        assertNotNull(recovery().previousRun().skikoCrashReport)
    }

    @Test
    fun `a crash report from another native library does not change the renderer`() {
        marker(4242)
        crashReport(4242, "# Problematic frame:\n# C  [jnitHook.dll+0x1234]")

        assertFalse("a crash outside Skiko was blamed on the renderer", recovery().previousRunCrashed())
    }

    @Test
    fun `a crash report older than the marker is not this run's`() {
        marker(4242)
        crashReport(4242, skikoFrame).setLastModified(0)

        assertFalse("a report from another process with the same pid was used", recovery().previousRunCrashed())
    }

    @Test
    fun `a crash report in another directory is found`() {
        marker(4242)
        val deeper = File(crashLogs, "logs").apply { mkdirs() }
        crashReport(4242, skikoFrame, deeper)

        val onlyHome = RendererRecovery(File(home, RUN_MARKER_FILE_NAME), listOf(home))
        assertFalse("the report was found in a directory it is not in", onlyHome.previousRunCrashed())

        val fromBoth = RendererRecovery(File(home, RUN_MARKER_FILE_NAME), listOf(home, deeper))
        assertTrue("the report was only looked for in one place", fromBoth.previousRunCrashed())
    }

    @Test
    fun `an old marker without a pid does not change the renderer`() {
        // Written by a version before the marker carried the pid: there is no report to look for.
        marker(pid = null)

        assertFalse("a legacy marker was still treated as a crash", recovery().previousRunCrashed())
        assertTrue(recovery().previousRun().stoppedFromOutside)
    }

    @Test
    fun `a crash climbs to OpenGL before giving up the graphics card`() {
        assertEquals(SkikoRenderer.OPENGL, SkikoRenderer.AUTOMATIC.nextAfterCrash())
        assertEquals(SkikoRenderer.SOFTWARE, SkikoRenderer.OPENGL.nextAfterCrash())
        // Already on the CPU: there is nowhere safer to go.
        assertEquals(SkikoRenderer.SOFTWARE, SkikoRenderer.SOFTWARE.nextAfterCrash())
    }

    @Test
    fun `the first crash after Direct3D starts OpenGL and says how to change it`() {
        val decision = decideRendererApi(
            previousRunCrashed = true,
            stored = SkikoRenderer.AUTOMATIC,
        )

        assertEquals("OPENGL", decision.value)
        assertEquals(SkikoRenderer.OPENGL, decision.level)
        assertEquals("the fallback was not remembered", SkikoRenderer.OPENGL, decision.persist)
        val message = decision.message.orEmpty()
        assertTrue("the log does not name the renderer it chose: $message", message.contains("OPENGL"))
        assertTrue("the log does not explain the crash: $message", message.contains("crash"))
        assertTrue(
            "the log does not say how to change the renderer back: $message",
            message.contains("--renderer=automatic"),
        )
    }

    @Test
    fun `a crash while already on OpenGL falls to software`() {
        val decision = decideRendererApi(previousRunCrashed = true, stored = SkikoRenderer.OPENGL)

        assertEquals("SOFTWARE", decision.value)
        assertEquals(SkikoRenderer.SOFTWARE, decision.persist)
    }

    @Test
    fun `a run stopped from the outside keeps the renderer and says why`() {
        val decision = decideRendererApi(
            previousRunCrashed = false,
            previousRunStoppedFromOutside = true,
            stored = SkikoRenderer.OPENGL,
        )

        assertEquals("the renderer was moved for a run that did not crash", "OPENGL", decision.value)
        assertEquals(SkikoRenderer.OPENGL, decision.level)
        assertNull("a choice nobody changed was rewritten", decision.persist)
        val message = decision.message.orEmpty()
        assertTrue("the log does not explain that nothing crashed: $message", message.contains("Ctrl+C"))
        assertTrue("the log does not say the renderer was kept: $message", message.contains("OpenGL"))
    }

    @Test
    fun `a run without a crash uses what the settings say`() {
        val decision = decideRendererApi(previousRunCrashed = false, stored = SkikoRenderer.OPENGL)

        assertEquals("OPENGL", decision.value)
        assertNull("a choice nobody changed was rewritten", decision.persist)
        assertNull("the app logged about a crash that did not happen", decision.message)
    }

    @Test
    fun `an untouched machine keeps the renderer Skiko picks`() {
        val decision = decideRendererApi(previousRunCrashed = false, stored = SkikoRenderer.AUTOMATIC)

        assertNull("the app picked a renderer nobody asked for", decision.value)
        assertEquals(SkikoRenderer.AUTOMATIC, decision.level)
        assertNull(decision.message)
    }

    @Test
    fun `the renderer argument is read on the command line`() {
        assertEquals(RendererArgument.Chosen(SkikoRenderer.OPENGL), parseRendererArgument(listOf("--renderer=opengl")))
        assertEquals(RendererArgument.Chosen(SkikoRenderer.SOFTWARE), parseRendererArgument(listOf("--renderer", "software")))
        assertEquals(RendererArgument.Chosen(SkikoRenderer.AUTOMATIC), parseRendererArgument(listOf("--renderer=automatic")))
        assertEquals(
            RendererArgument.Chosen(SkikoRenderer.SOFTWARE),
            parseRendererArgument(listOf("--minimized", "--renderer=Software")),
        )
        assertEquals(RendererArgument.None, parseRendererArgument(listOf("--minimized")))
        assertEquals(RendererArgument.None, parseRendererArgument(emptyList()))

        // A packaged app is started by double click: a typo must be said out loud, not ignored.
        assertEquals(RendererArgument.Invalid("vulkan"), parseRendererArgument(listOf("--renderer=vulkan")))
        assertEquals(RendererArgument.Invalid(""), parseRendererArgument(listOf("--renderer")))
    }

    @Test
    fun `the argument beats the stored preference and is remembered`() {
        val decision = decideRendererApi(
            previousRunCrashed = false,
            stored = SkikoRenderer.AUTOMATIC,
            requested = SkikoRenderer.SOFTWARE,
        )

        assertEquals("SOFTWARE", decision.value)
        assertEquals("the argument was not stored for the next start", SkikoRenderer.SOFTWARE, decision.persist)
        assertTrue(decision.message.orEmpty().contains("--renderer"))
    }

    @Test
    fun `the argument is what runs after a crash too`() {
        // Someone who asks for OpenGL after a Direct3D crash must not be pushed into software.
        val decision = decideRendererApi(
            previousRunCrashed = true,
            stored = SkikoRenderer.AUTOMATIC,
            requested = SkikoRenderer.OPENGL,
        )

        assertEquals("OPENGL", decision.value)
        assertEquals(SkikoRenderer.OPENGL, decision.persist)
    }

    @Test
    fun `an explicitly chosen skiko render api is never overridden`() {
        val decision = decideRendererApi(
            previousRunCrashed = true,
            stored = SkikoRenderer.AUTOMATIC,
            requested = SkikoRenderer.SOFTWARE,
            current = "OPENGL",
        )

        assertNull("the app overrode an explicit skiko.renderApi", decision.value)
        assertEquals(SkikoRenderer.OPENGL, decision.level)
        assertTrue(
            "an explicit renderer after a crash has to be said in the log: ${decision.message}",
            decision.message.orEmpty().contains("OPENGL"),
        )
        // Nothing to say when no run died: the user's choice is simply used.
        assertNull(
            decideRendererApi(previousRunCrashed = false, stored = SkikoRenderer.AUTOMATIC, current = "OPENGL").message,
        )
    }

    @Test
    fun `a stored preference of the wrong kind falls back to automatic`() {
        assertEquals(SkikoRenderer.AUTOMATIC, SkikoRenderer.fromStored(null))
        assertEquals(SkikoRenderer.AUTOMATIC, SkikoRenderer.fromStored("vulkan"))
        assertEquals(SkikoRenderer.OPENGL, SkikoRenderer.fromStored(" opengl "))
    }
}
