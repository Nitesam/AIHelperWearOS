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
            val (svgUrl, pngUrl) = buildLatexUrls(cleanLatex, isDisplay)
            parts.add(
                MathContentPart.LatexFormula(
                    content = cleanLatex,
                    imageUrl = svgUrl,
                    fallbackImageUrl = pngUrl,
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

    fun buildLatexUrls(
        latex: String,
        isDisplayMode: Boolean,
        sizeCommand: String = if (isDisplayMode) "\\large" else ""
    ): Pair<String, String> {
        val cleanLatex = latex
            .trim()
            .replace("  ", " ")
            .replace("\n", " ")

        val sizedLatex = if (sizeCommand.isNotEmpty()) {
            "$sizeCommand $cleanLatex"
        } else {
            cleanLatex
        }

        val encoded = URLEncoder.encode(sizedLatex, "UTF-8")
            .replace("+", "%20")

        val svgUrl = "https://i.upmath.me/svg/$encoded"
        val pngUrl = "https://i.upmath.me/png/$encoded"

        Log.d("LatexParser", "SVG: $svgUrl")
        Log.d("LatexParser", "PNG: $pngUrl")
        
        return Pair(svgUrl, pngUrl)
    }

    fun buildFullscreenLatexUrls(latex: String): Pair<String, String> {
        return buildLatexUrls(latex, isDisplayMode = false, sizeCommand = "\\Huge")
    }
}
