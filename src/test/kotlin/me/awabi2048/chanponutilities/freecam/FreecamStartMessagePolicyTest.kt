package me.awabi2048.chanponutilities.freecam

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FreecamStartMessagePolicyTest {
    @Test
    fun `first start and starts after five minutes show guidance`() {
        assertTrue(shouldShowFreecamStartMessage(null, 1_000L, 300_000L))
        assertTrue(shouldShowFreecamStartMessage(1_000L, 301_000L, 300_000L))
    }

    @Test
    fun `starts inside five minutes suppress guidance`() {
        assertFalse(shouldShowFreecamStartMessage(1_000L, 300_999L, 300_000L))
    }

    @Test
    fun `clock rollback does not suppress guidance indefinitely`() {
        assertTrue(shouldShowFreecamStartMessage(10_000L, 9_000L, 300_000L))
    }
}
