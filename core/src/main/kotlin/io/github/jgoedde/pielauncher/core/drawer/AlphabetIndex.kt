package io.github.jgoedde.pielauncher.core.drawer

import java.util.Locale

/** One letter of the App Drawer fast-scroller and the list position it jumps to. */
data class IndexLetter(
    val letter: String,
    val firstIndex: Int,
)

/** The bucket for labels that do not start with a letter. */
const val OTHER_BUCKET = "#"

/**
 * The fast-scroller letters for [entries], which must already be in Drawer order: only letters
 * that have entries, accented letters folded into their base (Ä → A), and digits and symbols in
 * one [OTHER_BUCKET] at the top. Each letter points at its first entry.
 */
fun <T> alphabetIndex(
    entries: List<T>,
    locale: Locale,
    label: (T) -> String,
): List<IndexLetter> {
    val letters = LinkedHashMap<String, Int>()
    var other: Int? = null
    entries.forEachIndexed { i, entry ->
        val first = fold(label(entry).trim(), locale).firstOrNull()
        if (first == null || !first.isLetter()) {
            if (other == null) other = i
        } else {
            letters.putIfAbsent(first.toString().uppercase(locale).take(1), i)
        }
    }
    val buckets = letters.map { (letter, index) -> IndexLetter(letter, index) }
    return other?.let { listOf(IndexLetter(OTHER_BUCKET, it)) + buckets } ?: buckets
}
