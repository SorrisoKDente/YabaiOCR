package dev.yabai.ocr.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import dev.yabai.ocr.desktop.i18n.DesktopStrings

/**
 * One entry of the manual: a stable [id] so the topics can be checked as a set, and the two lines the
 * window draws.
 */
internal data class HelpTopic(val id: String, val title: String, val body: String)

/**
 * The manual, in the order a new user meets the actions: the two captures, the hover, the search,
 * the dictionaries, the result window and the tray.
 *
 * The texts come from [DesktopStrings], so the window follows the interface language like the rest of
 * the app, and the ids are what `HelpManualTest` holds against the list of things the app can do.
 */
internal fun helpTopics(): List<HelpTopic> = listOf(
    HelpTopic("capture", DesktopStrings.helpCaptureTitle, DesktopStrings.helpCaptureBody),
    HelpTopic("region", DesktopStrings.helpRegionTitle, DesktopStrings.helpRegionBody),
    HelpTopic("hover", DesktopStrings.helpHoverTitle, DesktopStrings.helpHoverBody),
    HelpTopic("quick-search", DesktopStrings.helpQuickSearchTitle, DesktopStrings.helpQuickSearchBody),
    HelpTopic("dictionaries", DesktopStrings.helpDictionariesTitle, DesktopStrings.helpDictionariesBody),
    HelpTopic("result", DesktopStrings.helpResultTitle, DesktopStrings.helpResultBody),
    HelpTopic("tray", DesktopStrings.helpTrayTitle, DesktopStrings.helpTrayBody),
)

/** Read-only manual, opened from the Help button at the top of Settings. */
@Composable
fun HelpWindow(onClose: () -> Unit) {
    val topics = helpTopics()

    Window(
        onCloseRequest = onClose,
        title = DesktopStrings.helpWindowTitle,
        state = WindowState(width = 760.dp, height = 720.dp),
    ) {
        LaunchedEffect(Unit) {
            runCatching { window.iconImage = AppIconGenerator.createWindowImage(64) }
        }

        YabaiDesktopTheme(darkTheme = remember { DesktopSettings().darkTheme }) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colors.background) {
                SelectionContainer {
                    ScrollableLazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(24.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        item {
                            Text(
                                text = DesktopStrings.helpIntro,
                                style = MaterialTheme.typography.body2.copy(lineHeight = 20.sp),
                                color = MaterialTheme.colors.secondary,
                            )
                        }
                        items(topics) { topic ->
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = topic.title,
                                    style = MaterialTheme.typography.h6.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colors.onBackground,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = topic.body,
                                    style = MaterialTheme.typography.body2.copy(lineHeight = 20.sp),
                                    color = MaterialTheme.colors.onBackground,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
