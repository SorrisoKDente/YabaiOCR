package dev.yabai.ocr.desktop

import dev.yabai.ocr.desktop.i18n.DesktopStrings
import java.io.File

/** The Skiko property that picks the renderer — Direct3D on Windows, unless told otherwise. */
internal const val SKIKO_RENDER_API_PROPERTY = "skiko.renderApi"

/** Marker written while a run is alive; still present at the next start when that run was killed. */
internal const val RUN_MARKER_FILE_NAME = "run-in-progress"

/** Prefix of the process id written into the marker, which names the crash report to look for. */
internal const val RUN_MARKER_PID_PREFIX = "pid="

/**
 * How much older than the marker a crash report may be and still belong to that run.
 *
 * The JVM writes `hs_err_pid<pid>.log` when it dies natively, always after the marker. The slack is
 * only for file systems whose timestamps are coarser than the two writes.
 */
internal const val CRASH_REPORT_TIME_SLACK_MS = 2_000L


/**
 * The renderers the app may pick, and the order it falls back in after a native crash.
 *
 * Direct3D is what dies in `SkiaLayer.reshape` (see [RendererRecovery]); the fallback climbs this
 * ladder one step per crash instead of jumping straight to the CPU.
 */
enum class SkikoRenderer(val api: String?) {
    /** Whatever Skiko picks for the platform — Direct3D on Windows. */
    AUTOMATIC(null),

    /** The graphics card through another path: avoids Direct3D and keeps the GPU. */
    OPENGL("OPENGL"),

    /** Draws on the CPU: slowest, and the one that cannot reach the Direct3D redrawer at all. */
    SOFTWARE("SOFTWARE"),
    ;

    /**
     * The renderer to try after a run died.
     *
     * OpenGL first: it leaves the crashing Direct3D path and still uses the graphics card, which is
     * the cheaper step. Software is the last resort, not the first answer.
     */
    fun nextAfterCrash(): SkikoRenderer = when (this) {
        AUTOMATIC -> OPENGL
        OPENGL -> SOFTWARE
        SOFTWARE -> SOFTWARE
    }

    companion object {
        /** Parses the stored preference; anything unknown means «let Skiko pick». */
        fun fromStored(text: String?): SkikoRenderer {
            val name = text?.trim()?.uppercase().orEmpty()
            return values().firstOrNull { it.name == name } ?: AUTOMATIC
        }

        /** Parses a value of the `skiko.renderApi` property. */
        fun fromApi(api: String?): SkikoRenderer = when (api?.trim()?.uppercase()) {
            "OPENGL" -> OPENGL
            "SOFTWARE" -> SOFTWARE
            else -> AUTOMATIC
        }

        /** Parses the value of `--renderer=…`; null when it is not one of the accepted names. */
        fun fromArgument(value: String): SkikoRenderer? = when (value.trim().lowercase()) {
            "automatic", "auto", "default", "padrao", "padrão" -> AUTOMATIC
            "opengl", "gl" -> OPENGL
            "software", "sw", "cpu" -> SOFTWARE
            else -> null
        }
    }
}

/** What `--renderer=…` asked for. */
internal sealed interface RendererArgument {
    data object None : RendererArgument
    data class Chosen(val level: SkikoRenderer) : RendererArgument
    data class Invalid(val value: String) : RendererArgument
}

/**
 * Reads `--renderer=opengl` (or `--renderer software`) from the command line.
 *
 * The packaged app has no JVM flags to give — its launcher is an .exe — so the renderer has to be
 * reachable through an argument as well as through the Settings window.
 */
internal fun parseRendererArgument(args: List<String>): RendererArgument {
    var invalid: RendererArgument.Invalid? = null
    var chosen: RendererArgument.Chosen? = null

    args.forEachIndexed { index, arg ->
        val value = when {
            arg.startsWith("--renderer=") -> arg.removePrefix("--renderer=")
            arg == "--renderer" -> args.getOrNull(index + 1) ?: ""
            else -> null
        } ?: return@forEachIndexed

        val level = SkikoRenderer.fromArgument(value)
        if (level != null) {
            if (chosen == null) chosen = RendererArgument.Chosen(level)
        } else if (invalid == null) {
            invalid = RendererArgument.Invalid(value)
        }
    }
    return chosen ?: invalid ?: RendererArgument.None
}

/** What to do with the renderer at startup, and what to tell the user about it. */
internal data class RendererDecision(
    /** The value to give `skiko.renderApi`, or null to leave Skiko alone. */
    val value: String?,
    /** The line to print, or null when there is nothing worth saying. */
    val message: String?,
    /** The renderer this run uses. */
    val level: SkikoRenderer,
    /** The preference to store, or null when nothing changed. */
    val persist: SkikoRenderer?,
)

/**
 * Picks the renderer for this run.
 *
 * Order of precedence: an explicit `-Dskiko.renderApi=…` (what Skiko reads anyway, so it is never
 * fought), then `--renderer=…` from the command line, then the crash fallback, then the stored
 * preference. A run that died climbs one step of [SkikoRenderer.nextAfterCrash] and says so, naming
 * the two ways back — the Settings window and `--renderer=automatic` — because the packaged app has
 * no `-D` flags to give.
 */
internal fun decideRendererApi(
    previousRunCrashed: Boolean,
    stored: SkikoRenderer,
    requested: SkikoRenderer? = null,
    current: String? = null,
    previousRunStoppedFromOutside: Boolean = false,
): RendererDecision {
    if (current != null) {
        val level = SkikoRenderer.fromApi(current)
        val message = when {
            previousRunCrashed ->
                "[Renderer] A execução anterior não terminou (crash nativo do Skiko). Mantendo " +
                    "$SKIKO_RENDER_API_PROPERTY=$current, definido explicitamente."
            previousRunStoppedFromOutside -> stoppedFromOutsideMessage(level) +
                " Mantendo $SKIKO_RENDER_API_PROPERTY=$current, definido explicitamente."
            else -> null
        }
        return RendererDecision(null, message, level, null)
    }

    if (requested != null) {
        return RendererDecision(
            value = requested.api,
            message = "[Renderer] Renderizador definido por --renderer: " +
                "${DesktopStrings.rendererName(requested)}.",
            level = requested,
            persist = requested,
        )
    }

    if (previousRunCrashed) {
        val level = stored.nextAfterCrash()
        val why = if (stored == SkikoRenderer.AUTOMATIC) {
            "A execução anterior foi encerrada por um crash nativo do Skiko (Direct3D), sem exceção Java."
        } else {
            "A execução anterior com ${DesktopStrings.rendererName(stored)} também terminou em " +
                "crash nativo do Skiko."
        }
        return RendererDecision(
            value = level.api,
            message = "[Renderer] $why\n" +
                "[Renderer] Iniciando com $SKIKO_RENDER_API_PROPERTY=${level.api} para não repetir o crash.\n" +
                "[Renderer] Para trocar: Ajustes → Renderizador gráfico, ou iniciar com --renderer=automatic.",
            level = level,
            persist = level,
        )
    }

    if (previousRunStoppedFromOutside) {
        // The marker was left behind by something that is not a Skiko crash. Moving the renderer
        // here is what made Ctrl+C look like a crash and turned the fallback on for nothing.
        return RendererDecision(
            value = stored.api,
            message = stoppedFromOutsideMessage(stored),
            level = stored,
            persist = null,
        )
    }

    return RendererDecision(stored.api, null, stored, null)
}

/** The line said when a run ended without a Skiko crash report: nothing is changed. */
private fun stoppedFromOutsideMessage(stored: SkikoRenderer): String =
    "[Renderer] A execução anterior foi encerrada fora do app (Ctrl+C, processo finalizado ou queda de " +
        "energia) e não deixou relatório de crash do Skiko. Renderizador mantido: " +
        DesktopStrings.rendererName(stored) + "."


/**
 * Remembers across starts whether the previous run died, and how.
 *
 * Skiko's Direct3D redrawer can take the whole process down without an exception: it crashes in
 * native code while a window is resized (`EXCEPTION_ACCESS_VIOLATION` in
 * `PictureRecorder.finishRecordingAsPicture`, reached from `SkiaLayer.reshape`). Nothing inside the
 * JVM can catch that, so the only witness is the next start.
 *
 * The marker alone is not enough to call it a crash. A run also leaves the marker behind when it is
 * stopped from the outside — Ctrl+C under Gradle, Task Manager, or a machine powered off — and
 * changing the renderer for those wasted the fall back (and, while OpenGL/Software did not draw the
 * card, cost the user the app). The JVM names its native crash report after the dead process, so the
 * marker carries the pid and the next start only believes in a Skiko crash when
 * `hs_err_pid<pid>.log` exists and names Skiko.
 */
internal class RendererRecovery(
    private val inProgress: File,
    private val crashLogDirs: List<File> = listOfNotNull(
        System.getProperty("user.dir")?.let(::File),
        inProgress.parentFile,
    ).distinct(),
) {
    /**
     * What the marker says about the run before this one.
     *
     * [endedAbnormally] is the marker still being there; it is true for a crash and for a process
     * stopped from the outside. [skikoCrashReport] is the report that tells the two apart.
     */
    fun previousRun(): PreviousRun {
        if (!inProgress.isFile) return PreviousRun(false, null)
        val report = markerPid()?.let(::skikoCrashReport)
        return PreviousRun(true, report)
    }

    /** True when the previous run never finished *and* left a native crash report from Skiko. */
    fun previousRunCrashed(): Boolean = previousRun().skikoCrashReport != null

    /** Writes the «this run is alive» marker, with the pid its crash report will be named after. */
    fun markRunning(note: String = "") {
        inProgress.parentFile?.mkdirs()
        val pid = ProcessHandle.current().pid()
        runCatching { inProgress.writeText("$RUN_MARKER_PID_PREFIX$pid${if (note.isBlank()) "" else "\n$note"}") }
    }

    /** The app is closing normally: the marker goes away, so the next start is not a «crashed» one. */
    fun markCleanExit() {
        runCatching { inProgress.delete() }
    }

    private fun markerPid(): Long? = runCatching {
        inProgress.readLines()
            .firstOrNull { it.startsWith(RUN_MARKER_PID_PREFIX) }
            ?.removePrefix(RUN_MARKER_PID_PREFIX)
            ?.trim()
            ?.toLongOrNull()
    }.getOrNull()

    private fun skikoCrashReport(pid: Long): File? {
        val name = "hs_err_pid$pid.log"
        for (dir in crashLogDirs) {
            val report = File(dir, name)
            if (!report.isFile) continue
            // A report older than this run's marker belongs to another process that happened to
            // have the same pid, which Windows reuses.
            if (report.lastModified() < inProgress.lastModified() - CRASH_REPORT_TIME_SLACK_MS) continue
            val text = runCatching { report.readText() }.getOrNull() ?: continue
            if (text.contains("skiko", ignoreCase = true)) return report
        }
        return null
    }

    companion object {
        fun default(home: File = defaultDesktopHome()) = RendererRecovery(File(home, RUN_MARKER_FILE_NAME))
    }
}

/** What the previous run left behind: its marker, and the Skiko report when it really crashed. */
internal data class PreviousRun(
    /** The marker was still on disk: the previous run did not close through the app. */
    val endedAbnormally: Boolean,
    /** The native report of a crash inside Skiko, or null when the run did not leave one. */
    val skikoCrashReport: File?,
) {
    /** Everything ended abnormally and left no Skiko report: stopped from the outside. */
    val stoppedFromOutside: Boolean get() = endedAbnormally && skikoCrashReport == null
}

