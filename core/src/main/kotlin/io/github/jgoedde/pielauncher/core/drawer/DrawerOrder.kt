package io.github.jgoedde.pielauncher.core.drawer

import java.text.Collator
import java.util.Locale

/**
 * Orders Drawer Entries alphabetically by [label] for [locale]: case- and accent-insensitive,
 * locale-aware collation.
 */
fun <T> List<T>.sortedForDrawer(
    locale: Locale,
    label: (T) -> String,
): List<T> {
    val collator = Collator.getInstance(locale).apply { strength = Collator.PRIMARY }
    return sortedWith { a, b -> collator.compare(label(a), label(b)) }
}
