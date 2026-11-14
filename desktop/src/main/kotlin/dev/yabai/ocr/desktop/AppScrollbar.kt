package dev.yabai.ocr.desktop

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The scrollable container the desktop windows use, with the scrollbar Compose Desktop does not
 * draw on its own.
 *
 * The mouse wheel already scrolls a [LazyColumn] on desktop, but nothing on screen says the content
 * continues below the fold: in the settings, the dictionary manager and the result panel the last
 * card simply ended at the window edge. The scrollbar is drawn as an overlay inside the same [Box],
 * so it takes no width from the content and the 1:1 layout of the picker and the result panel is
 * untouched.
 *
 * The wrapper keeps every parameter of the container it wraps, so a call site only changes the
 * function name. That is deliberate: the scrollbar must be the only difference.
 */

/** A [LazyColumn] filling [modifier], with its scrollbar pinned to the right edge. */
@Composable
internal fun ScrollableLazyColumn(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: LazyListScope.() -> Unit,
) {
    Box(modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = state,
            contentPadding = contentPadding,
            verticalArrangement = verticalArrangement,
            content = content,
        )
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(state),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
        )
    }
}
