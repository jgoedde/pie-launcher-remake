package io.github.jgoedde.pielauncher.core.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetBackgroundTest {
    private class View(
        val name: String,
        val children: List<View> = emptyList(),
        val visible: Boolean = true,
        val draws: Boolean = false,
        val backgroundId: Boolean = false,
        val clipsToOutline: Boolean = false,
    ) {
        override fun toString() = name
    }

    private val tree =
        object : WidgetViewTree<View> {
            override fun children(view: View) = view.children

            override fun isVisible(view: View) = view.visible

            override fun draws(view: View) = view.draws

            override fun hasBackgroundId(view: View) = view.backgroundId

            override fun clipsToOutline(view: View) = view.clipsToOutline
        }

    @Test
    fun `the one view marked as background is the background`() {
        val marked = View("marked", backgroundId = true)
        val host = View("host", listOf(View("root", listOf(View("text", draws = true), marked), draws = true)))

        assertSame(marked, WidgetBackground.find(host, tree))
    }

    @Test
    fun `with no marked view the first view that draws is the background`() {
        val card = View("card", listOf(View("text", draws = true)), draws = true)
        val host = View("host", listOf(View("wrapper", listOf(card))))

        assertSame(card, WidgetBackground.find(host, tree))
    }

    @Test
    fun `two marked views count as none`() {
        val card = View("card", listOf(View("a", backgroundId = true), View("b", backgroundId = true)), draws = true)
        val host = View("host", listOf(card))

        assertSame(card, WidgetBackground.find(host, tree))
    }

    @Test
    fun `a container whose content spreads over two visible children is the background`() {
        val wrapper =
            View(
                "wrapper",
                listOf(View("a", draws = true), View("hidden", draws = true, visible = false), View("b", draws = true)),
            )
        val host = View("host", listOf(View("outer", listOf(wrapper))))

        assertSame(wrapper, WidgetBackground.find(host, tree))
    }

    @Test
    fun `invisible views are skipped`() {
        val shown = View("shown", draws = true)
        val host = View("host", listOf(View("root", listOf(View("gone", draws = true, visible = false), shown))))

        assertSame(shown, WidgetBackground.find(host, tree))
    }

    @Test
    fun `with nothing drawn the host itself is the background`() {
        val host = View("host", listOf(View("empty", listOf(View("blank")))))

        assertSame(host, WidgetBackground.find(host, tree))
    }

    @Test
    fun `a widget opts out by clipping its marked background itself`() {
        assertTrue(WidgetBackground.optsOut(View("bg", backgroundId = true, clipsToOutline = true), tree))
        assertFalse(WidgetBackground.optsOut(View("bg", backgroundId = true), tree))
        assertFalse(WidgetBackground.optsOut(View("card", draws = true, clipsToOutline = true), tree))
    }
}
