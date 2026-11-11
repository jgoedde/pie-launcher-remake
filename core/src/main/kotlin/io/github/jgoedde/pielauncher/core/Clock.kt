package io.github.jgoedde.pielauncher.core

/** Wall-clock time source, injected so logic in `:core` stays testable. */
fun interface Clock {
    fun nowMillis(): Long

    companion object {
        val Wall: Clock = Clock { System.currentTimeMillis() }

        fun fixed(epochMillis: Long): Clock = Clock { epochMillis }
    }
}
