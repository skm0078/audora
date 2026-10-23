// File: pdf/src/test/kotlin/io/github/skm0078/audora/pdf/layout/FurnitureFinderTest.kt
// Purpose: Captions and display equations are tagged, and body text that mentions them is not (RI-7a).

package io.github.skm0078.audora.pdf.layout

import io.github.skm0078.audora.core.config.FurnitureConfig
import io.github.skm0078.audora.core.model.UnitKind
import io.github.skm0078.audora.pdf.layout.PageSketch.Companion.BODY
import io.github.skm0078.audora.pdf.layout.PageSketch.Companion.BOLD
import io.github.skm0078.audora.pdf.quality.BundledWordModels
import io.github.skm0078.audora.pdf.units.ReadingUnitBuilder
import org.junit.Assert.assertEquals
import org.junit.Test

class FurnitureFinderTest {
    private val equations = EquationLines(FurnitureConfig(), BundledWordModels)
    private val builder = ReadingUnitBuilder()

    private fun kinds(sketch: PageSketch): List<Pair<UnitKind, String>> =
        builder.build(sketch.build()).map { it.kind to it.text }

    @Test
    fun `display lines of maths are equations and prose with inline maths is not`() {
        listOf(
            "E = mc2" to true,
            "x(t) = x0 + v t (8)" to true,
            "∂u/∂t = D ∇²u + f(u) (12)" to true,
            "σ² = Σ (xi − μ)² / n" to true,
            "क = ख + ग" to true,
            "(9)" to true,
            "where the diffusion coefficient D = 0.5 is constant" to false,
            "The value increases as T → ∞" to false,
            "The results are shown in the table (3)" to false,
            "lrate = d −0.5 model · min(step_num−0.5 , step_num · warmup_steps−1.5 ) (3)" to true,
            "Hello world" to false,
            "PRL 116, 061102 (2016)" to false,
            "1.0 · 1020" to false,
            "$ git checkout -b <newbranchname>" to false,
            "<newbranchname>." to false,
            "?>9@AJ.B<?><#3$4(56#" to false,
            "ϕতিটা ফিўআবশিҝক। জমি না থাকলে (OTA-SFI এর έϠেϏ )" to false,
            "১২+৬=১৮ ।" to true,
            "" to false,
        ).forEach { (text, equation) -> assertEquals(text, equation, equations.isEquation(text)) }
    }

    @Test
    fun `a labelled line set apart is a caption and a sentence naming a figure is body`() {
        listOf(
            Triple("Figure 2. Growth of the cells over time", BODY, UnitKind.CAPTION),
            Triple("Table 3: Results of the second trial", BODY, UnitKind.CAPTION),
            Triple("Fig. 5 Regions of validity in parameter space", BODY, UnitKind.CAPTION),
            Triple("Fig 3 results of the second trial", BODY * 0.9f, UnitKind.CAPTION),
            Triple("चित्र 4: पौधे की वृद्धि", BODY, UnitKind.CAPTION),
            Triple("সারণি ২ । ফসলের তালিকা", BODY, UnitKind.CAPTION),
            Triple("Table 2 shows that the growth is fast and steady.", BODY, UnitKind.BODY),
            Triple("Fig. 5 illustrates the region where it is valid.", BODY, UnitKind.BODY),
            Triple("Figures 8, 9 and 10 show the bifurcation diagrams.", BODY, UnitKind.BODY),
        ).forEach { (text, size, kind) ->
            // Body text above sets the page's body size.
            val sketch = PageSketch()
            sketch.lines(List(3) { "Body text set in the regular size of this page, line $it" }, left = 50f, top = 760f)
            sketch.line(text, left = 50f, top = 700f, size = size)
            assertEquals(text, kind, kinds(sketch).last().first)
        }
    }

    @Test
    fun `a bold label alone takes the smaller caption below it`() {
        val sketch = PageSketch()
        sketch.line("Some body text that comes before the table here.", left = 50f, top = 700f)
        sketch.line("Table 2", left = 50f, top = 670f, font = BOLD)
        sketch.line("Comparison of the two methods", left = 50f, top = 655f, size = BODY * 0.9f)
        sketch.line("More body text follows after the table is done.", left = 50f, top = 620f)
        assertEquals(
            listOf(UnitKind.BODY, UnitKind.CAPTION, UnitKind.CAPTION, UnitKind.BODY),
            kinds(sketch).map { it.first },
        )
    }

    @Test
    fun `an equation inside a paragraph becomes one unit of its own`() {
        val sketch = PageSketch()
        sketch.lines(
            listOf(
                "The motion of the body obeys the following law",
                "x(t) = x0 + v t . (8)",
                "where v is the constant speed of the body.",
            ),
            left = 50f,
            top = 700f,
        )
        assertEquals(
            listOf(
                UnitKind.BODY to "The motion of the body obeys the following law",
                UnitKind.EQUATION to "x(t) = x0 + v t . (8)",
                UnitKind.BODY to "where v is the constant speed of the body.",
            ),
            kinds(sketch),
        )
    }

    @Test
    fun `a wrapped sentence ending in maths stays one sentence and an indented formula is an equation`() {
        val sketch = PageSketch()
        sketch.lines(
            listOf(
                "As the growth function is strictly increasing we have gi below gi+1 for",
                "i = 1, 2, ..., m − 1.",
                "The total population after growth is then the sum",
            ),
            left = 50f,
            top = 700f,
        )
        sketch.line("N = g1 + g2 + gm", left = 50f + BODY * 4, top = 700f - BODY * 1.2f * 3)
        sketch.line("over all the patches of the domain.", left = 50f, top = 700f - BODY * 1.2f * 4)
        assertEquals(
            listOf(UnitKind.BODY, UnitKind.BODY, UnitKind.EQUATION, UnitKind.BODY),
            kinds(sketch).map { it.first },
        )
    }

    @Test
    fun `a caption that layout joined to the paragraph above is split off after its full stop`() {
        val sketch = PageSketch()
        sketch.lines(
            listOf(
                "Useful specifiers lists some of the more useful specifiers that format",
                "takes.",
                "Table 1. Useful specifiers for the pretty format",
            ),
            left = 50f,
            top = 700f,
        )
        assertEquals(
            listOf(
                UnitKind.BODY to "Useful specifiers lists some of the more useful specifiers that format takes.",
                UnitKind.CAPTION to "Table 1.",
                UnitKind.CAPTION to "Useful specifiers for the pretty format",
            ),
            kinds(sketch),
        )
    }
}
