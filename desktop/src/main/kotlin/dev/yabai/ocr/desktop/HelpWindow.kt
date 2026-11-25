package dev.yabai.ocr.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.yabai.ocr.desktop.i18n.DesktopStrings

/**
 * One entry of the manual: a stable [id] so the topics can be checked as a set, and the two lines the
 * window draws.
 */
internal data class HelpTopic(val id: String, val title: String, val body: String)

/** One button of the quick reference: the icon it shows, its name and what it does. */
internal data class HelpButton(val icon: String, val name: String, val what: String)

/**
 * The shortcuts the manual prints (R52).
 *
 * Taken from the settings at the moment the screen opens: the manual used to hardcode the defaults,
 * so a reader who had changed one was told to press a key that does nothing.
 */
internal data class HelpShortcuts(
    val fullScreen: String,
    val region: String,
    val hover: String,
    val hoverCycle: String,
    val quickDict: String,
)

internal fun helpShortcuts(settings: DesktopSettings): HelpShortcuts = HelpShortcuts(
    fullScreen = settings.globalHotkey,
    region = settings.regionHotkey,
    hover = settings.hoverHotkey,
    hoverCycle = settings.hoverCycleHotkey,
    quickDict = settings.quickDictHotkey,
)

/**
 * The quick reference the manual opens with: each button with the glyph it draws, so a control can be
 * looked up by its picture instead of by its name.
 */
internal fun helpButtons(): List<HelpButton> = listOf(
    HelpButton("🖥️", DesktopStrings.helpBtnFullScreenName, DesktopStrings.helpBtnFullScreenWhat),
    HelpButton("✂️", DesktopStrings.helpBtnCropName, DesktopStrings.helpBtnCropWhat),
    HelpButton("💬", DesktopStrings.helpBtnHoverName, DesktopStrings.helpBtnHoverWhat),
    HelpButton("🔍", DesktopStrings.helpBtnQuickSearchName, DesktopStrings.helpBtnQuickSearchWhat),
    HelpButton("📖", DesktopStrings.helpBtnDictionariesName, DesktopStrings.helpBtnDictionariesWhat),
    HelpButton("🖼️", DesktopStrings.helpBtnImageName, DesktopStrings.helpBtnImageWhat),
    HelpButton("📋", DesktopStrings.helpBtnCopyName, DesktopStrings.helpBtnCopyWhat),
    HelpButton("🤖", DesktopStrings.helpBtnAiName, DesktopStrings.helpBtnAiWhat),
    HelpButton("◀", DesktopStrings.helpBtnSnippetsName, DesktopStrings.helpBtnSnippetsWhat),
    HelpButton("⚙️", DesktopStrings.helpBtnSettingsName, DesktopStrings.helpBtnSettingsWhat),
    HelpButton("✕", DesktopStrings.helpBtnExitName, DesktopStrings.helpBtnExitWhat),
)

/**
 * The manual, in the order a new user meets the actions: the button reference first, then the two
 * captures, the hover, the search, the dictionaries, the result window, the renderers, the engines
 * and the tray.
 *
 * The texts come from [DesktopStrings], so the screen follows the interface language like the rest of
 * the app, and the ids are what `HelpManualTest` holds against the list of things the app can do.
 * [shortcuts] is what makes the two capture entries name the keys the user actually has.
 */
internal fun helpTopics(shortcuts: HelpShortcuts): List<HelpTopic> = listOf(
    HelpTopic(
        "capture",
        DesktopStrings.helpCaptureTitle(shortcuts.fullScreen),
        DesktopStrings.helpCaptureBody(shortcuts.fullScreen),
    ),
    HelpTopic(
        "region",
        DesktopStrings.helpRegionTitle(shortcuts.region),
        DesktopStrings.helpRegionBody(shortcuts.region),
    ),
    HelpTopic(
        "hover",
        DesktopStrings.helpHoverTitle(shortcuts.hover, shortcuts.hoverCycle),
        DesktopStrings.helpHoverBody(shortcuts.hover, shortcuts.hoverCycle),
    ),
    HelpTopic(
        "quick-search",
        DesktopStrings.helpQuickSearchTitle(shortcuts.quickDict),
        DesktopStrings.helpQuickSearchBody(shortcuts.quickDict),
    ),
    HelpTopic("dictionaries", DesktopStrings.helpDictionariesTitle, DesktopStrings.helpDictionariesBody),
    HelpTopic("result", DesktopStrings.helpResultTitle, DesktopStrings.helpResultBody),
    HelpTopic("renderers", DesktopStrings.helpRenderersTitle, DesktopStrings.helpRenderersBody()),
    HelpTopic("engines", DesktopStrings.helpEnginesTitle, DesktopStrings.helpEnginesBody()),
    HelpTopic("tray", DesktopStrings.helpTrayTitle, DesktopStrings.helpTrayBody),
)

/**
 * The manual, as a screen of the main window (R53).
 *
 * It used to be a window of its own, opened by the Settings' Help button: the reader lost the
 * settings behind it, and the manual was the only screen that did not come back with a button. Now
 * it is one more destination of the same window — like the dictionary manager — and the bar on top
 * says its name and the way back.
 */
@Composable
fun HelpContent(settings: DesktopSettings, onBackRequested: () -> Unit) {
    val shortcuts = remember { helpShortcuts(settings) }
    val topics = remember(shortcuts) { helpTopics(shortcuts) }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colors.surface, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = onBackRequested,
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = yabaiPalette().field,
                        contentColor = MaterialTheme.colors.onSurface,
                    ),
                ) {
                    Text(DesktopStrings.btnBackToPrevious, fontWeight = FontWeight.SemiBold)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = DesktopStrings.helpWindowTitle,
                    style = MaterialTheme.typography.subtitle1.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colors.onSurface,
                )
            }
        }

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
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = DesktopStrings.helpButtonsTitle,
                            style = MaterialTheme.typography.h6.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colors.onBackground,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        helpButtons().forEach { button ->
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Text(
                                    text = button.icon,
                                    style = MaterialTheme.typography.body1,
                                    modifier = Modifier.width(34.dp),
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = button.name,
                                        style = MaterialTheme.typography.body2.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colors.onBackground,
                                    )
                                    Text(
                                        text = button.what,
                                        style = MaterialTheme.typography.body2.copy(lineHeight = 19.sp),
                                        color = MaterialTheme.colors.onBackground,
                                    )
                                }
                            }
                        }
                    }
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
