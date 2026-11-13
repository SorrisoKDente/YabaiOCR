package dev.yabai.ocr.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import dev.yabai.ocr.core.md.MarkdownBlock
import dev.yabai.ocr.core.md.MarkdownSpan
import dev.yabai.ocr.core.md.parseMarkdown
import dev.yabai.ocr.desktop.i18n.DesktopStrings

/**
 * Preview of the release notes GitHub publishes with the release.
 *
 * The notes are Markdown; showing the raw text would make the button pointless, so the shared
 * parser turns them into headings, lists, quotes and code, and each block is drawn here.
 */
@Composable
fun ReleaseNotesWindow(
    version: String,
    notes: String,
    onClose: () -> Unit,
) {
    val blocks = remember(notes) { parseMarkdown(notes) }
    val state = rememberWindowState(width = 760.dp, height = 660.dp)

    Window(
        onCloseRequest = onClose,
        title = DesktopStrings.releaseNotesTitle(version),
        state = state,
    ) {
        androidx.compose.runtime.LaunchedEffect(Unit) {
            runCatching { window.iconImage = AppIconGenerator.createWindowImage(64) }
        }
        YabaiDesktopTheme(darkTheme = remember { DesktopSettings().darkTheme }) {
            Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF12141A)) {
                if (blocks.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                        Text(DesktopStrings.releaseNotesEmpty, color = Color(0xFFA0A6B8))
                    }
                } else {
                    SelectionContainer {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(24.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            itemsIndexed(blocks) { _, block -> MarkdownBlockView(block) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MarkdownBlockView(block: MarkdownBlock) {
    when (block) {
        is MarkdownBlock.Heading -> Text(
            text = annotated(block.spans),
            style = TextStyle(
                fontSize = when (block.level) {
                    1 -> 22.sp
                    2 -> 19.sp
                    3 -> 17.sp
                    else -> 15.sp
                },
                fontWeight = FontWeight.Bold,
                color = Color(0xFF9DB2FF),
                lineHeight = 26.sp,
            ),
        )

        is MarkdownBlock.Paragraph -> Text(
            text = annotated(block.spans),
            style = MaterialTheme.typography.body2.copy(color = Color(0xFFE4E7EF), lineHeight = 20.sp),
        )

        is MarkdownBlock.Bullets -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            block.items.forEach { item ->
                Row {
                    Text("•", color = Color(0xFF5D84FF), modifier = Modifier.width(16.dp))
                    Text(
                        text = annotated(item),
                        style = MaterialTheme.typography.body2.copy(color = Color(0xFFE4E7EF), lineHeight = 20.sp),
                    )
                }
            }
        }

        is MarkdownBlock.Quote -> Row {
            Text(
                text = "▎ ",
                color = Color(0xFF5D84FF),
                style = MaterialTheme.typography.body2.copy(lineHeight = 20.sp),
            )
            Text(
                text = annotated(block.spans),
                style = MaterialTheme.typography.body2.copy(
                    color = Color(0xFFA0A6B8),
                    fontStyle = FontStyle.Italic,
                    lineHeight = 20.sp,
                ),
            )
        }

        is MarkdownBlock.Code -> Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF1B1E28),
            shape = RoundedCornerShape(6.dp),
        ) {
            Text(
                text = block.text,
                modifier = Modifier.padding(12.dp),
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = Color(0xFFD6E0FF),
                    lineHeight = 18.sp,
                ),
            )
        }

        MarkdownBlock.Divider -> {
            Spacer(modifier = Modifier.height(4.dp))
            Divider(color = Color(0xFF2E3344))
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

/**
 * Inline runs as one [AnnotatedString]; links carry a [LinkAnnotation] so Compose opens them in the
 * user's browser the same way it handles any other link.
 */
private fun annotated(spans: List<MarkdownSpan>): AnnotatedString = buildAnnotatedString {
    spans.forEach { span ->
        when (span) {
            is MarkdownSpan.Text -> append(span.text)
            is MarkdownSpan.Bold -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(span.text) }
            is MarkdownSpan.Italic -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(span.text) }
            is MarkdownSpan.Code -> withStyle(
                SpanStyle(fontFamily = FontFamily.Monospace, background = Color(0xFF2A2F3D)),
            ) { append(span.text) }

            is MarkdownSpan.Link -> withLink(LinkAnnotation.Url(span.url)) {
                withStyle(SpanStyle(color = Color(0xFF7FA6FF), fontWeight = FontWeight.Medium)) {
                    append(span.text)
                }
            }
        }
    }
}
