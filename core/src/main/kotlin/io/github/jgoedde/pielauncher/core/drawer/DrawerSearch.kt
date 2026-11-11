package io.github.jgoedde.pielauncher.core.drawer

import java.util.Locale

/**
 * App Drawer search over the labels of [entries]. Build once per entry list, then call [search]
 * on every keystroke.
 */
class DrawerSearch<T>(
    entries: List<T>,
    private val locale: Locale,
    label: (T) -> String,
) {
    private val indexed: List<Indexed<T>> = entries.sortedForDrawer(locale, label).map { Indexed(it, label(it), locale) }

    /** Matches in rank order, alphabetical within a tier; a blank [query] lists every entry. */
    fun search(query: String): List<T> {
        val q = fold(query.trim(), locale)
        if (q.isEmpty()) return indexed.map { it.value }
        val tiers = List(Tier.entries.size) { mutableListOf<T>() }
        for (entry in indexed) entry.tierFor(q)?.let { tiers[it.ordinal] += entry.value }
        return tiers.flatten()
    }

    private enum class Tier { LabelPrefix, Word, Substring, Fuzzy }

    private class Indexed<T>(
        val value: T,
        label: String,
        locale: Locale,
    ) {
        private val text = fold(label, locale)
        private val words = text.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
        private val initials = words.joinToString("") { it.take(1) }

        fun tierFor(q: String): Tier? =
            when {
                text.startsWith(q) -> Tier.LabelPrefix
                words.any { it.startsWith(q) } || initials.startsWith(q) -> Tier.Word
                q in text -> Tier.Substring
                isNearWordPrefix(q) -> Tier.Fuzzy
                else -> null
            }

        /** Whether [q] is within [maxTypos] edits of how some word starts. */
        private fun isNearWordPrefix(q: String): Boolean {
            if (q.length < FUZZY_MIN_LENGTH) return false
            val allowed = maxTypos(q.length)
            return words.any { word ->
                (q.length - allowed..q.length + allowed).any { len ->
                    len in 1..word.length && editDistance(q, word, len, allowed) <= allowed
                }
            }
        }
    }

    private companion object {
        const val FUZZY_MIN_LENGTH = 3

        fun maxTypos(queryLength: Int) = if (queryLength < 6) 1 else 2
    }
}

/**
 * Optimal string alignment distance (insert, delete, substitute, swap neighbours) between [a] and
 * the first [bLength] chars of [b]. Returns early with `limit + 1` once it cannot get under [limit].
 */
private fun editDistance(
    a: String,
    b: String,
    bLength: Int,
    limit: Int,
): Int {
    var prev2 = IntArray(bLength + 1)
    var prev = IntArray(bLength + 1) { it }
    var cur = IntArray(bLength + 1)
    for (i in 1..a.length) {
        cur[0] = i
        var rowMin = i
        for (j in 1..bLength) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            var d = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
            if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) d = minOf(d, prev2[j - 2] + 1)
            cur[j] = d
            rowMin = minOf(rowMin, d)
        }
        if (rowMin > limit) return limit + 1
        val recycled = prev2
        prev2 = prev
        prev = cur
        cur = recycled
    }
    return prev[bLength]
}
