package io.github.jgoedde.pielauncher.core.drawer

import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import kotlin.random.Random

/**
 * Benchmark-style check of the PRD budget: at most ~4 ms per keystroke for ~500 Drawer Entries.
 * Typing every prefix of queries that mostly miss is the worst case: every entry falls through to
 * the fuzzy tier.
 */
class DrawerSearchBenchmarkTest {
    @Test
    fun `a keystroke over 500 entries stays within 4 ms`() {
        val random = Random(42)
        val syllables =
            listOf("go", "ma", "cal", "en", "dar", "fi", "re", "fox", "sig", "nal", "té", "lé", "gram", "ö", "pho", "to", "set", "tings")
        val labels =
            List(500) {
                List(random.nextInt(1, 4)) {
                    List(random.nextInt(2, 4)) { syllables.random(random) }.joinToString("").replaceFirstChar(Char::uppercase)
                }.joinToString(" ")
            }
        val search = DrawerSearch(labels, Locale.GERMAN) { it }
        val keystrokes = listOf("signal messenger", "qwertzuiop", "kalendar", "photos").flatMap { q -> (1..q.length).map(q::take) }

        repeat(20) { keystrokes.forEach(search::search) } // JIT warm-up
        val rounds = 20
        val start = System.nanoTime()
        repeat(rounds) { keystrokes.forEach(search::search) }
        val perKeystrokeMs = (System.nanoTime() - start) / 1e6 / (rounds * keystrokes.size)

        println("DrawerSearch: %.3f ms per keystroke".format(perKeystrokeMs))
        assertTrue("%.3f ms per keystroke".format(perKeystrokeMs), perKeystrokeMs <= 4.0)
    }
}
