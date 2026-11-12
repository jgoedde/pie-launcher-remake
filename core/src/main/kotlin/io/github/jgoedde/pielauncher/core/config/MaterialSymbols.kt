package io.github.jgoedde.pielauncher.core.config

/**
 * The Material Symbols Rounded names (PRD 9.1), read from the codepoints file bundled next to
 * this class. Import validation rejects names missing here; the app draws a Layer's icon by the
 * codepoint from the bundled font.
 */
object MaterialSymbols {
    private const val RESOURCE = "/io/github/jgoedde/pielauncher/core/config/material-symbols-rounded.codepoints"

    private val codepoints: Map<String, Int> by lazy {
        val stream = checkNotNull(MaterialSymbols::class.java.getResourceAsStream(RESOURCE)) { "$RESOURCE is not bundled" }
        stream.bufferedReader().useLines { lines ->
            lines
                .map { it.trim().split(' ') }
                .filter { it.size == 2 }
                .associate { (name, hex) -> name to hex.toInt(16) }
        }
    }

    /** The codepoint of [name] in the bundled font; null when it is no Material Symbols Rounded name. */
    fun codepoint(name: String): Int? = codepoints[name]
}
