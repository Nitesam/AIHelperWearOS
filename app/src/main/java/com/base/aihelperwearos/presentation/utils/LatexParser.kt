package com.base.aihelperwearos.presentation.utils

import com.base.aihelperwearos.data.models.MathContentPart
import com.base.aihelperwearos.data.models.ParsedMathMessage
import android.util.Log
import java.net.URLEncoder

object LatexParser {

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

    fun buildLatexImageUrl(
        latex: String,
        isDisplayMode: Boolean,
        textSizeSp: Float = 14f
    ): String {
        val cleanLatex = latex
            .trim()
            .replace("  ", " ")
            .replace("\n", " ")

        val encoded = URLEncoder.encode(cleanLatex, "UTF-8")
            .replace("+", "%20")
            .replace("%28", "(")
            .replace("%29", ")")
            .replace("%5C", "\\")

        val dpi = when {
            isDisplayMode -> 200
            textSizeSp > 16f -> 160
            else -> 140
        }

        val size = when {
            isDisplayMode -> "\\LARGE"
            textSizeSp > 16f -> "\\large"
            else -> "\\normalsize"
        }

        val url = "https://latex.codecogs.com/png.image?" +
                "\\dpi{$dpi}" +
                "\\bg{white}" +
                "$size%20" +
                encoded

        Log.d("LatexParser", "URL: $url")
        return url
    }
}