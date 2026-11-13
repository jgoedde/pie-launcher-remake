package io.github.jgoedde.pielauncher.core.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PinHandOffTest {
    @Test
    fun `a held Pin Request is handed over to the drop once`() {
        val handOff = PinHandOff<String>()
        val token = handOff.hold("request")

        assertEquals("request", handOff.take(token))
        assertNull(handOff.take(token))
    }

    @Test
    fun `peeking leaves the Pin Request held`() {
        val handOff = PinHandOff<String>()
        val token = handOff.hold("request")

        assertEquals("request", handOff.peek(token))
        assertEquals("request", handOff.take(token))
        assertNull(handOff.peek(token))
    }

    @Test
    fun `two held Pin Requests get their own tokens`() {
        val handOff = PinHandOff<String>()

        val first = handOff.hold("first")
        val second = handOff.hold("second")

        assertNotEquals(first, second)
        assertEquals("second", handOff.take(second))
        assertEquals("first", handOff.take(first))
    }

    @Test
    fun `a released Pin Request is not handed over`() {
        val handOff = PinHandOff<String>()
        val token = handOff.hold("request")

        handOff.release(token)

        assertNull(handOff.take(token))
    }
}
