package com.base.aihelperwearos.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.base.aihelperwearos.data.models.MathContentPart
import com.base.aihelperwearos.presentation.utils.LatexParser
import dev.jeziellago.compose.markdowntext.MarkdownText

/**
 * Renders markdown mixed with LaTeX formulas as Wear OS UI content.
 *
 * @param markdown raw markdown text that may include LaTeX delimiters.
 * @param modifier modifier applied to the column container.
 * @param fontSize base font size in sp units.
 * @return `Unit` after composing the content.
 */
@Composable
fun MathMarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    fontSize: Int = 11
) {
    val segments = remember(markdown) { splitMarkdownAndCode(markdown) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        segments.forEach { segment ->
            when (segment) {
                is MarkdownSegment.Text -> MathTextSegment(
                    markdown = segment.content,
                    fontSize = fontSize
                )
                is MarkdownSegment.Code -> CodeBlockSegment(
                    language = segment.language,
                    code = segment.content,
                    fontSize = fontSize
                )
            }
        }
    }
}

@Composable
private fun MathTextSegment(
    markdown: String,
    fontSize: Int
) {
    val parsed = remember(markdown) { LatexParser.parseLatexContent(markdown) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        parsed.parts.forEach { part ->
            when (part) {
                is MathContentPart.Text -> MarkdownText(
                    markdown = part.content,
                    fontSize = fontSize.sp,
                    lineHeight = (fontSize + 1).sp,
                    color = MaterialTheme.colors.onSurface,
                    modifier = Modifier.fillMaxWidth()
                )
                is MathContentPart.LatexFormula -> LatexImage(
                    latex = part.content,
                    imageUrl = part.imageUrl,
                    fallbackImageUrl = part.fallbackImageUrl,
                    isDisplayMode = part.isDisplayMode,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun CodeBlockSegment(
    language: String?,
    code: String,
    fontSize: Int
) {
    val displayLines = remember(code) { formatCodeLinesForWear(code) }
    val codeFontSize = (fontSize - 1).coerceAtLeast(8)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colors.surface.copy(alpha = 0.82f),
                shape = RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 7.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        Text(
            text = language?.takeIf { it.isNotBlank() }?.lowercase() ?: "code",
            fontFamily = FontFamily.Monospace,
            fontSize = 8.sp,
            lineHeight = 9.sp,
            color = MaterialTheme.colors.secondary
        )

        displayLines.forEach { line ->
            Row(modifier = Modifier.fillMaxWidth()) {
                if (line.indentGuide.isNotEmpty()) {
                    Text(
                        text = line.indentGuide,
                        fontFamily = FontFamily.Monospace,
                        fontSize = codeFontSize.sp,
                        lineHeight = (codeFontSize + 1).sp,
                        color = MaterialTheme.colors.onSurfaceVariant.copy(alpha = 0.72f)
                    )
                }
                Text(
                    text = line.body.ifEmpty { " " },
                    fontFamily = FontFamily.Monospace,
                    fontSize = codeFontSize.sp,
                    lineHeight = (codeFontSize + 1).sp,
                    color = MaterialTheme.colors.onSurface,
                    modifier = Modifier.weight(1f, fill = true)
                )
            }
        }
    }
}

private sealed class MarkdownSegment {
    data class Text(val content: String) : MarkdownSegment()
    data class Code(val language: String?, val content: String) : MarkdownSegment()
}

private data class DisplayCodeLine(
    val indentGuide: String,
    val body: String
)

private fun splitMarkdownAndCode(markdown: String): List<MarkdownSegment> {
    val regex = Regex("""```([A-Za-z0-9_+\-.]*)[ \t]*\r?\n([\s\S]*?)```""")
    val segments = mutableListOf<MarkdownSegment>()
    var cursor = 0

    regex.findAll(markdown).forEach { match ->
        if (match.range.first > cursor) {
            val text = markdown.substring(cursor, match.range.first)
            if (text.isNotBlank()) {
                segments += MarkdownSegment.Text(text)
            }
        }

        segments += MarkdownSegment.Code(
            language = match.groupValues.getOrNull(1)?.takeIf { it.isNotBlank() },
            content = match.groupValues.getOrElse(2) { "" }.trim('\n', '\r')
        )
        cursor = match.range.last + 1
    }

    if (cursor < markdown.length) {
        val text = markdown.substring(cursor)
        if (text.isNotBlank()) {
            segments += MarkdownSegment.Text(text)
        }
    }

    return segments.ifEmpty { listOf(MarkdownSegment.Text(markdown)) }
}

private fun formatCodeLinesForWear(code: String): List<DisplayCodeLine> {
    return code
        .replace("\t", "    ")
        .replace("\r", "")
        .lines()
        .map { line ->
            val leadingSpaces = line.indexOfFirst { it != ' ' }.let { index ->
                if (index == -1) line.length else index
            }
            val indentLevel = leadingSpaces / 4
            val remainingSpaces = leadingSpaces % 4
            val body = line.drop(leadingSpaces)
            val guide = buildString {
                repeat(indentLevel) { append("│ ") }
                if (remainingSpaces > 0) append(" ".repeat(remainingSpaces))
            }

            DisplayCodeLine(
                indentGuide = if (body.isBlank()) "" else guide,
                body = body
            )
        }
}
