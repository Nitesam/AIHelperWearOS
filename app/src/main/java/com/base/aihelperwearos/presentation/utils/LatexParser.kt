package com.base.aihelperwearos.presentation.utils

import com.base.aihelperwearos.data.models.MathContentPart
import com.base.aihelperwearos.data.models.ParsedMathMessage
import android.util.Log
import java.net.URLEncoder

object LatexParser {

    /**
     * Splits mixed text and LaTeX content into renderable parts.
     *
     * @param content message content containing inline or display math.
     * @return `ParsedMathMessage` with ordered text and LaTeX parts.
     */
    fun parseLatexContent(content: String): ParsedMathMessage {
        val parts = mutableListOf<MathContentPart>()

        val combinedPattern = Regex(
            pattern = """\$\$(.+?)\$\$|\$(.+?)\$""",
            option = RegexOption.DOT_MATCHES_ALL
        )

        var lastIndex = 0

        combinedPattern.findAll(content).forEach { match ->
            if (match.range.first > lastIndex) {
                val textBefore = content.substring(lastIndex, match.range.first).trim()
                if (textBefore.isNotEmpty()) {
                    parts.add(MathContentPart.Text(textBefore))
                }
            }

            val isDisplay = match.value.startsWith("$$")
            val latexContent = if (isDisplay) {
                match.groupValues[1]
            } else {
                match.groupValues[2]
            }

            val cleanLatex = latexContent.trim()
            parts.add(
                MathContentPart.LatexFormula(
                    content = cleanLatex,
                    imageUrl = buildLatexImageUrl(cleanLatex, isDisplay),
                    isDisplayMode = isDisplay
                )
            )

            lastIndex = match.range.last + 1
        }

        if (lastIndex < content.length) {
            val textAfter = content.substring(lastIndex).trim()
            if (textAfter.isNotEmpty()) {
                parts.add(MathContentPart.Text(textAfter))
            }
        }

        if (parts.isEmpty()) {
            parts.add(MathContentPart.Text(content))
        }

        return ParsedMathMessage(parts)
    }

    /**
     * Builds a URL for rendering a LaTeX expression as an image.
     *
     * @param latex LaTeX source string.
     * @param isDisplayMode whether the formula is display-style.
     * @param textSizeSp text size used for scaling.
     * @return image URL as a `String`.
     */
    fun buildLatexImageUrl(
        latex: String,
        isDisplayMode: Boolean,
        textSizeSp: Float = 14f
    ): String {
        val cleanLatex = latex
            .trim()
            .replace("  ", " ")
            .replace("\n", " ")

        val sizedLatex = if (isDisplayMode) {
            "\\large $cleanLatex"
        } else {
            cleanLatex
        }

        val encoded = URLEncoder.encode(sizedLatex, "UTF-8")
            .replace("+", "%20")

        val url = "https://i.upmath.me/png/$encoded"

        Log.d("LatexParser", "URL: $url")
        return url
    }
}
