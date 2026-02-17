package com.base.aihelperwearos.presentation.utils

import com.base.aihelperwearos.data.models.MathContentPart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LatexParserTest {

    @Test
    fun `parseLatexContent supports escaped inline and display delimiters`() {
        val content = """
            Osserviamo che \(t=\log x\) e poi usiamo \[\frac{dx}{x}\].
        """.trimIndent()

        val parsed = LatexParser.parseLatexContent(content)
        val formulas = parsed.parts.filterIsInstance<MathContentPart.LatexFormula>()

        assertEquals(2, formulas.size)
        assertEquals("t=\\log x", formulas[0].content)
        assertFalse(formulas[0].isDisplayMode)
        assertEquals("\\frac{dx}{x}", formulas[1].content)
        assertTrue(formulas[1].isDisplayMode)
    }

    @Test
    fun `parseLatexContent still supports dollar delimiters`() {
        val content = "Inline $x^2$ e blocco $$\\int_0^1 x\\,dx$$."

        val parsed = LatexParser.parseLatexContent(content)
        val formulas = parsed.parts.filterIsInstance<MathContentPart.LatexFormula>()

        assertEquals(2, formulas.size)
        assertEquals("x^2", formulas[0].content)
        assertFalse(formulas[0].isDisplayMode)
        assertEquals("\\int_0^1 x\\,dx", formulas[1].content)
        assertTrue(formulas[1].isDisplayMode)
    }
}

