package com.base.aihelperwearos.data.models


sealed class MathContentPart {
    data class Text(val content: String) : MathContentPart()
    data class LatexFormula(
        val content: String,
        val imageUrl: String,
        val fallbackImageUrl: String?,
        val isDisplayMode: Boolean
    ) : MathContentPart()
}


data class ParsedMathMessage(
    val parts: List<MathContentPart>
)


data class MathSolution(
    val transcription: String,
    val solution: String,
    val model: String
)
