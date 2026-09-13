package me.awabi2048.chanponutilities.safety

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CommandSafeguardPolicyTest {
    @Test
    fun `fifty targets at exactly ten percent activate`() {
        assertTrue(CommandSafeguardPolicy.shouldActivate(500L, listOf(49, 50)))
    }

    @Test
    fun `less than fifty targets never activate even at ten percent`() {
        assertFalse(CommandSafeguardPolicy.shouldActivate(490L, listOf(49)))
    }

    @Test
    fun `fifty targets below ten percent do not activate`() {
        assertFalse(CommandSafeguardPolicy.shouldActivate(501L, listOf(50)))
    }

    @Test
    fun `empty or zero denominators do not activate`() {
        assertFalse(CommandSafeguardPolicy.shouldActivate(0L, listOf(50)))
        assertFalse(CommandSafeguardPolicy.shouldActivate(500L, emptyList()))
    }
}
