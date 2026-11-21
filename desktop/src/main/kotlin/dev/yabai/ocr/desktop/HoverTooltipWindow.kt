package dev.yabai.ocr.desktop

import dev.yabai.ocr.desktop.i18n.DesktopStrings

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DefaultWindowExceptionHandlerFactory
import androidx.compose.ui.window.LocalWindowExceptionHandlerFactory
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowExceptionHandler
import androidx.compose.ui.window.WindowExceptionHandlerFactory
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import kotlin.coroutines.resume
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import dev.yabai.ocr.core.dict.TokenMatch
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent

/**
 * Tooltip window anchored next to the pointer with dictionary definitions.
 * Sized dynamically to wrap content tightly without empty space or cutting text at the bottom.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun HoverTooltipWindow(
    matches: List<TokenMatch>,
    mousePoint: Point,
    activeFilter: String = "",
    /**
     * Why the card has nothing to show — today, that the chosen dictionary has no entry for the word.
     * Drawn inside the card: a notice window of its own would open on every hover, on top of the very
     * text being read.
     */
    notice: String? = null,
    installedDictTitles: List<String> = emptyList(),
    onSelectDictionary: (String) -> Unit = {},
    onDismiss: () -> Unit,
    onBoundsChanged: (Rectangle?) -> Unit = {},
    /**
     * Whether the card is on screen.
     *
     * The window is created once and hidden between words instead of being created and destroyed
     * for each one (D98): the Compose windows and Skiko surfaces of a card left allocations in the
     * native allocator that no heap trim returns, and the process kept that RSS for the rest of the
     * session.
     */
    visible: Boolean = true,
) {
    // The card's window is created while Compose is drawing the frame that first shows it, and
    // Compose then renders it from inside that same pass: it throws «Reentry into
    // ignoringRedrawRequests is not allowed» and the default handler answers with an Error dialog.
    // The skipped inner render costs one frame — the next pass draws the card — so the dialog is
    // the only thing worth removing. Nothing else is swallowed.
    CompositionLocalProvider(LocalWindowExceptionHandlerFactory provides TooltipWindowExceptions) {
        HoverTooltipCardWindow(
            matches = matches,
            mousePoint = mousePoint,
            activeFilter = activeFilter,
            notice = notice,
            installedDictTitles = installedDictTitles,
            onSelectDictionary = onSelectDictionary,
            onDismiss = onDismiss,
            onBoundsChanged = onBoundsChanged,
            visible = visible,
        )
    }
}

@Composable
private fun HoverTooltipCardWindow(
    matches: List<TokenMatch>,
    mousePoint: Point,
    activeFilter: String = "",
    notice: String? = null,
    installedDictTitles: List<String> = emptyList(),
    onSelectDictionary: (String) -> Unit = {},
    onDismiss: () -> Unit,
    onBoundsChanged: (Rectangle?) -> Unit = {},
    visible: Boolean = true,
) {
    // Hoisted, because the list of dictionaries is drawn inside the card: it used to be a popup — a
    // window of its own — which on this desktop opened off the bottom of the screen and took the
    // click with it, and whose open/close raced the card's resize into «Reentry into
    // ignoringRedrawRequests is not allowed». Inside the card it cannot leave the screen and there
    // is no second window to race.
    var dictMenuOpen by remember { mutableStateOf(false) }

    // The pointer the card was last placed for. A height change for the *same* pointer is the
    // user growing this card (opening the dictionary list), not a new word: the card keeps its
    // corner instead of being recomputed from the pointer with the new height. That recomputation
    // is what moved a card near the taskbar upwards — the header left the pointer, and the hover
    // dismissal closed the card the user was clicking.
    var placedFor by remember { mutableStateOf<Point?>(null) }

    // A card that comes back is a new card: the dictionary list it was left with is not part of the
    // next word, and it would open the card taller than the content it is about to show.
    LaunchedEffect(visible) { if (!visible) dictMenuOpen = false }

    val estimatedHeight = remember(matches, dictMenuOpen, installedDictTitles, notice) {
        estimateHeight(matches, dictMenuOpen, installedDictTitles.size, notice != null)
    }

    // Where the card goes, decided before the window exists: Compose places a new window from
    // `WindowState.position`, and a `setLocation` that lands while the window is still being mapped
    // is what the compositor ignores — on KDE/Wayland that left the card in the screen's top-left
    // corner instead of beside the pointer.
    val anchor = remember(mousePoint, estimatedHeight) {
        HoverGeometry.placeTooltip(
            pointer = mousePoint,
            size = java.awt.Dimension(TOOLTIP_WIDTH, estimatedHeight),
            // The card has to stay off the taskbar: a row hidden behind it cannot be clicked.
            screen = HoverGeometry.workAreaAt(mousePoint),
        )
    }

    // The state is built once, and its size is a constant on purpose. `WindowState` is applied by Compose itself, so
    // a height that follows the content made every dictionary change resize the window *during*
    // composition — the second writer of the same bounds, and the one that AWT refused with «Reentry
    // into ignoringRedrawRequests is not allowed». The card is sized and placed in one place now:
    // [applySizeAndPlacement], one event later.
    val state = remember {
        WindowState(
            width = TOOLTIP_WIDTH.dp,
            height = TOOLTIP_INITIAL_HEIGHT.dp,
            position = WindowPosition(anchor.x.dp, anchor.y.dp),
        )
    }

    Window(
        onCloseRequest = onDismiss,
        // Hiding instead of destroying is what keeps the native allocations from piling up word
        // after word (D98). The window itself is created once, by the overlay that owns it.
        visible = visible,
        title = "Yabai OCR - Hover",
        undecorated = true,
        // Not user-resizable on purpose. Compose answers an undecorated *and* resizable window
        // with a resize border of its own (`ComposeWindow.setResizable` enables
        // `UndecoratedWindowResizer` while `isUndecorated && isResizable`): an 8 dp band along
        // every edge where the pointer turns into a resize cursor and a drag resizes the window.
        // The dictionary list's scrollbar is the last 12 dp on the right, so that band sat on top
        // of it — hovering the scrollbar showed the resize cursor and grabbing it resized the card
        // instead of scrolling. The card has no size to offer the user anyway (fixed width, height
        // from the content), and the application still resizes it through `setSize`.
        resizable = false,
        alwaysOnTop = true,
        focusable = false,
        transparent = true,
        state = state,
    ) {
        LaunchedEffect(estimatedHeight, mousePoint, window, visible) {
            window.isAutoRequestFocus = false
            // Decided once per height change: was the card already placed for this word?
            val sameAnchor = placedFor == mousePoint
            if (!visible) {
                // A card that is not on screen has no bounds: the hover's «is the pointer on the
                // card?» test must not run against the rectangle of the word before it.
                onBoundsChanged(null)
                return@LaunchedEffect
            }
            // A reused card is already mapped, so it can be sized and placed before the debounce:
            // without this the next word's card spent 80 ms at the previous word's corner.
            if (window.isShowing) {
                applySizeAndPlacement(window, mousePoint, estimatedHeight, onBoundsChanged, sameAnchor)
                placedFor = mousePoint
            }
            // The window background is left exactly as Compose set it. `transparent = true` makes
            // ComposeWindowPanel ask Skiko for the right background for the renderer in use, and on
            // Windows that answer is `null` for OpenGL and Software: those two make the window
            // transparent through a DWM call that this AWT background would undo. Setting it to a
            // transparent colour here (which is what Direct3D wants anyway) made the card draw
            // nothing at all under OpenGL and Software, while the OCR and the log went on working.
            // Waiting a beat before touching the window does two things. It collapses a burst of
            // content changes — choosing dictionaries, the notice appearing and going away — into a
            // single resize of the final size, and it lets a window that is still being mapped
            // finish doing so first. Resizing this window quickly, or before it has drawn, kills the
            // JVM natively inside Skiko's Direct3D redrawer
            // (EXCEPTION_ACCESS_VIOLATION in PictureRecorder.finishRecordingAsPicture;
            // compose-multiplatform #4425 / skiko #883).
            delay(TOOLTIP_PLACEMENT_DEBOUNCE_MS)
            var attempts = 0
            var applied = applySizeAndPlacement(
                window, mousePoint, estimatedHeight, onBoundsChanged, sameAnchor,
            )
            if (applied) placedFor = mousePoint
            // «Not on screen yet» is the normal first moment of a new card. Giving up there used to
            // leave it at the initial height for its whole life, so the placement is retried for a
            // while — and only for a while, or a card that never shows would keep resizing forever.
            while (shouldRetryCardPlacement(applied, attempts)) {
                delay(CARD_PLACEMENT_RETRY_MS)
                attempts++
                applied = applySizeAndPlacement(
                    window, mousePoint, estimatedHeight, onBoundsChanged, sameAnchor,
                )
                if (applied) placedFor = mousePoint
            }
            // The window exists now, so the placement is no longer decided by whoever mapped it.
            // A WM that keeps its own position for the first frames is corrected here; the size is
            // already right, so this second pass only moves the card.
            delay(TOOLTIP_REPOSITION_DELAY_MS)
            // A correction pass, still for the same word: it must not undo the hold above.
            if (applySizeAndPlacement(
                    window, mousePoint, estimatedHeight, onBoundsChanged, sameAnchor,
                )
            ) {
                placedFor = mousePoint
            }
        }

        DisposableEffect(window) {
            val listener = object : ComponentAdapter() {
                override fun componentMoved(e: ComponentEvent) = onBoundsChanged(window.bounds)
                override fun componentResized(e: ComponentEvent) {
                    onBoundsChanged(window.bounds)
                }
            }
            window.addComponentListener(listener)
            onDispose {
                window.removeComponentListener(listener)
                onBoundsChanged(null)
            }
        }

        YabaiDesktopTheme(darkTheme = remember { DesktopSettings().darkTheme }) {
            Surface(
                modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                color = Color(0xFF1B1E28),
                shape = RoundedCornerShape(10.dp),
                elevation = 8.dp,
            ) {
                Column(modifier = Modifier.fillMaxWidth().wrapContentHeight()) {
                    // Header Bar showing Active Filter & Dropdown Selector
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF141620))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (activeFilter.isBlank()) DesktopStrings.allDictionaries else "📖 $activeFilter",
                            style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold),
                            color = Color(0xFF5D84FF)
                        )

                        Surface(
                            modifier = Modifier.clickable { dictMenuOpen = !dictMenuOpen },
                            color = Color(0xFF2E3344),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = DesktopStrings.selectDictDropdown,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                fontSize = 11.sp,
                                color = Color.White
                            )
                        }
                    }

                    // The list grows the card instead of hanging below it in a popup: at the bottom
                    // of the screen a popup opens outside everything the user can see, and the
                    // click that asked for it looks like it went through to the app behind.
                    if (dictMenuOpen) {
                        DictionaryList(
                            titles = installedDictTitles,
                            onPick = { title ->
                                dictMenuOpen = false
                                onSelectDictionary(title)
                            },
                        )
                    }

                    val noticeText = notice
                    if (noticeText != null) {
                        Text(
                            text = noticeText,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.body2,
                            color = Color(0xFFE5A93C),
                        )
                        Divider(color = Color(0xFF2E3344), thickness = 0.5.dp)
                    }

                    SelectionContainer {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().wrapContentHeight().padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(matches) { match ->
                                val entry = match.entry
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(
                                            text = if (entry.reading.isBlank()) {
                                                entry.term
                                            } else {
                                                "${entry.term}  【${entry.reading}】"
                                            },
                                            style = MaterialTheme.typography.subtitle2.copy(fontWeight = FontWeight.Bold),
                                            color = Color.White,
                                        )
                                        if (match.dictionaryTitle.isNotBlank()) {
                                            Text(
                                                text = match.dictionaryTitle,
                                                style = MaterialTheme.typography.caption,
                                                color = Color(0xFFA0A6B8),
                                            )
                                        }
                                    }

                                    dictionaryFormLine(match)?.let { line ->
                                        Text(
                                            text = line,
                                            style = MaterialTheme.typography.caption,
                                            color = Color(0xFF81D4FA),
                                        )
                                    }
                                    if (match.reasons.isNotEmpty()) {
                                        Text(
                                            text = match.reasons.joinToString(" → "),
                                            style = MaterialTheme.typography.caption,
                                            color = Color(0xFF81D4FA),
                                        )
                                    }
                                    if (entry.tags.isNotBlank()) {
                                        Text(
                                            text = entry.tags,
                                            style = MaterialTheme.typography.caption,
                                            color = Color(0xFFE5A93C),
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = entry.definition,
                                        style = MaterialTheme.typography.body2.copy(lineHeight = 17.sp),
                                        color = Color(0xFFC7CCDA),
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Divider(color = Color(0xFF2E3344), thickness = 0.5.dp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The dictionary list: at most [DICTIONARY_MENU_MAX_ROWS] rows tall, the rest behind a scrollbar.
 *
 * With every dictionary of a full install the list used to grow the card to its 640 px ceiling and
 * the rows past it were simply cut off — the ones at the bottom could not be clicked at all. The
 * card no longer grows with the list; the list scrolls inside it.
 */
@Composable
private fun DictionaryList(titles: List<String>, onPick: (String) -> Unit) {
    val scrollState = rememberScrollState()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = (DICTIONARY_ROW_HEIGHT * DICTIONARY_MENU_MAX_ROWS).dp)
            .background(Color(0xFF141620)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(end = DICTIONARY_SCROLLBAR_GUTTER_DP.dp),
        ) {
            DictionaryRow(text = DesktopStrings.allDictionaries, onClick = { onPick("") })
            titles.forEach { title ->
                DictionaryRow(text = "📖 $title", onClick = { onPick(title) })
            }
            Divider(color = Color(0xFF2E3344), thickness = 0.5.dp)
        }
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(scrollState),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
        )
    }
}

@Composable
private fun DictionaryRow(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        fontSize = 12.sp,
        color = Color(0xFFC7CCDA),
    )
}

/** Accurately estimates content height so window wraps definitions tightly without cutting text off. */
internal fun estimateHeight(
    matches: List<TokenMatch>,
    dictMenuOpen: Boolean = false,
    dictionaryCount: Int = 0,
    notice: Boolean = false,
): Int {
    var pixels = 58 // Header bar (28px) + container padding + safety buffer
    // The message sits above the definitions: measured, not guessed away, or the card would cut it.
    if (notice) pixels += NOTICE_BLOCK_HEIGHT
    // The list scrolls past [DICTIONARY_MENU_MAX_ROWS], so a large install no longer makes the
    // card taller than the screen (the rows below the ceiling used to be unreachable).
    if (dictMenuOpen) {
        pixels += DICTIONARY_ROW_HEIGHT * minOf(dictionaryCount + 1, DICTIONARY_MENU_MAX_ROWS) + 2
    }
    for (match in matches.take(3)) {
        pixels += 26 // Term & Reading line
        if (dictionaryFormLine(match) != null) pixels += 18
        if (match.reasons.isNotEmpty()) pixels += 18
        if (match.entry.tags.isNotBlank()) pixels += 18

        val lines = match.entry.definition.split('\n').sumOf { line ->
            // Full-width Japanese & formatted translations wrap at ~32 chars in 460dp width
            if (line.isEmpty()) 1 else (line.length / 32) + 1
        }
        pixels += lines * 20 + 16
    }
    return pixels.coerceIn(130, 640)
}

/** Width of the card, in pixels; also the width [estimateHeight]'s line wrapping assumes. */
private const val TOOLTIP_WIDTH = 460

/** Height the card starts at, before [applySizeAndPlacement] measures the content. */
private const val TOOLTIP_INITIAL_HEIGHT = 220

/**
 * Height of one dictionary row, in pixels; the unit [estimateHeight] counts them with.
 *
 * The row has no fixed height: a 12 sp line plus the 7 dp of padding on each side is ~30 px, and
 * giving it a `height` smaller than that cut the names in half — the list showed ten half-rows.
 */
/** Height of the in-card notice (two lines plus its padding and divider), in pixels. */
private const val NOTICE_BLOCK_HEIGHT = 58

/** Rows of the dictionary list shown before it starts scrolling. */
private const val DICTIONARY_MENU_MAX_ROWS = 6

/** Room for the scrollbar, so a long name does not end under it. */
private const val DICTIONARY_SCROLLBAR_GUTTER_DP = 10

private const val DICTIONARY_ROW_HEIGHT = 32

/**
 * How long the card waits before it is sized.
 *
 * Long enough to swallow the burst of height changes a dictionary switch produces (the notice goes,
 * the definition comes, or the other way round) and for a window that is still being mapped to
 * finish; short enough that the card does not visibly resize after the user has read past it.
 */
private const val TOOLTIP_PLACEMENT_DEBOUNCE_MS = 80L

/**
 * How long to wait before placing the card again.
 *
 * The first placement runs while the window is being mapped, and every compositor is free to put
 * the window where it wants until it is on screen. 120 ms is long enough for the map on this
 * desktop and short enough that the card does not visibly jump.
 */
private const val TOOLTIP_REPOSITION_DELAY_MS = 120L

/**
 * Sizes and places the card, one Swing event later.
 *
 * `setSize` on a window that is mapping, or while one of its popups is being opened or dismissed,
 * re-enters the component's redraw bookkeeping — AWT answers that with «Reentry into
 * ignoringRedrawRequests is not allowed» in an Error dialog. Deferring the call to the next turn of
 * the event loop lets the popup finish first, and the window is only touched while it is displayable.
 */
private suspend fun applySizeAndPlacement(
    window: java.awt.Window,
    anchor: Point,
    height: Int,
    onBoundsChanged: (java.awt.Rectangle?) -> Unit,
    /**
     * True when the pointer did not change since the last placement: the card is only growing (or
     * shrinking) and must keep its corner, clamped into the work area.
     */
    sameAnchor: Boolean = false,
): Boolean = suspendCancellableCoroutine { continuation ->
    javax.swing.SwingUtilities.invokeLater {
        // `isShowing`, not `isDisplayable`: a window that is being mapped or disposed is displayable
        // and has no Compose surface yet, and resizing one is what took the JVM down natively.
        if (!window.isShowing) {
            if (continuation.isActive) continuation.resume(false)
            return@invokeLater
        }
        val placement = planCardPlacement(
            bounds = window.bounds,
            pointer = anchor,
            height = height,
            screen = HoverGeometry.workAreaAt(anchor),
            sameAnchor = sameAnchor,
        )
        runCatching {
            if (placement.resize) window.setSize(TOOLTIP_WIDTH, height)
            window.setLocation(placement.location.x, placement.location.y)
        }
        onBoundsChanged(window.bounds)
        if (continuation.isActive) continuation.resume(true)
    }
}

/** How many times the card tries to place itself while its window is not on screen yet. */
internal const val CARD_PLACEMENT_MAX_ATTEMPTS = 20

/** How long the card waits between those attempts, in milliseconds (half a second in total). */
internal const val CARD_PLACEMENT_RETRY_MS = 25L

/**
 * Whether the card should try to place itself again.
 *
 * Only while it never managed to — a window that is not showing yet — and only up to
 * [CARD_PLACEMENT_MAX_ATTEMPTS], so a card that never becomes visible cannot loop forever.
 */
internal fun shouldRetryCardPlacement(applied: Boolean, attempts: Int): Boolean =
    !applied && attempts < CARD_PLACEMENT_MAX_ATTEMPTS

/** What placing the card comes down to: a move, and a resize only when the size really changed. */
internal data class CardPlacement(val resize: Boolean, val location: Point)

/**
 * Where the card goes and whether it has to be resized to get there.
 *
 * The resize is the expensive half — and, with Skiko's Direct3D redrawer, the dangerous one — so it
 * only happens when the content height actually changed. The position keeps being recomputed
 * because a card near the bottom of the screen flips above the pointer.
 */
internal fun planCardPlacement(
    bounds: Rectangle,
    pointer: Point,
    height: Int,
    screen: Rectangle,
    /**
     * Whether this is the same word the card already shows. Then the card keeps its top-left corner
     * and grows from it, clamped into [screen]; only a new word is anchored to the pointer.
     */
    sameAnchor: Boolean = false,
): CardPlacement {
    val resize = bounds.height != height || bounds.width != TOOLTIP_WIDTH
    val location = if (sameAnchor) {
        holdCardLocation(bounds, height, screen)
    } else {
        HoverGeometry.placeTooltip(pointer, java.awt.Dimension(TOOLTIP_WIDTH, height), screen)
    }
    return CardPlacement(resize, location)
}

/**
 * Where a card that is only growing goes: it stays where it is and the new content extends it
 * downwards, moved up only when the bottom would leave [screen].
 *
 * Recomputing the corner from the pointer with the new height moved the whole card by exactly the
 * height it had gained — with the dictionary list open that is a few hundred pixels, so a card near
 * the taskbar jumped above the pointer and the hover dismissal closed it.
 */
internal fun holdCardLocation(bounds: Rectangle, height: Int, screen: Rectangle): Point {
    val maxX = (screen.x + screen.width - TOOLTIP_WIDTH).coerceAtLeast(screen.x)
    val maxY = (screen.y + screen.height - height).coerceAtLeast(screen.y)
    return Point(
        bounds.x.coerceIn(screen.x, maxX),
        bounds.y.coerceIn(screen.y, maxY),
    )
}

/**
 * Swallows the one exception Compose throws while it is drawing the card for the first time.
 *
 * `SwingWindow` renders a window from inside `update`, and that update runs during a frame; when the
 * window is becoming displayable it asks for an immediate render from within the render already in
 * progress, and the interop bookkeeping refuses the reentry. The next frame draws the card, so the
 * only visible effect of the exception was the Error dialog the user got while changing
 * dictionaries. Everything else still goes to Compose's own handler.
 */
@OptIn(ExperimentalComposeUiApi::class)
internal object TooltipWindowExceptions : WindowExceptionHandlerFactory {
    override fun exceptionHandler(window: java.awt.Window) = WindowExceptionHandler { throwable ->
        if (!isRedrawReentry(throwable)) {
            DefaultWindowExceptionHandlerFactory.exceptionHandler(window).onException(throwable)
        }
    }
}

/** Whether [throwable] is Compose refusing a render it started from inside another render. */
internal fun isRedrawReentry(throwable: Throwable): Boolean =
    throwable is IllegalStateException &&
        throwable.message?.contains("Reentry into ignoringRedrawRequests") == true
