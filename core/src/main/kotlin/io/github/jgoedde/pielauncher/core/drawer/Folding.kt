package io.github.jgoedde.pielauncher.core.drawer

import java.text.Normalizer
import java.util.Locale

private val CombiningMarks = Regex("\\p{Mn}+")

/** Lowercases [text] and strips accents, so "Ärzte" and "arzte" compare equal. */
internal fun fold(
    text: String,
    locale: Locale,
): String = CombiningMarks.replace(Normalizer.normalize(text, Normalizer.Form.NFD), "").lowercase(locale)
