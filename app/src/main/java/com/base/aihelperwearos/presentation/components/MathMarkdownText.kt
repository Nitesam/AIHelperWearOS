package com.base.aihelperwearos.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.MaterialTheme
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
    val parsed = LatexParser.parseLatexContent(markdown)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        parsed.parts.forEach { part ->
            when (part) {
                is MathContentPart.Text -> {
                    MarkdownText(
                        markdown = part.content,
                        fontSize = fontSize.sp,
                        lineHeight = (fontSize + 1).sp,
                        color = MaterialTheme.colors.onSurface,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                is MathContentPart.LatexFormula -> {
                    LatexImage(
                        latex = part.content,
                        imageUrl = part.imageUrl,
                        isDisplayMode = part.isDisplayMode,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
