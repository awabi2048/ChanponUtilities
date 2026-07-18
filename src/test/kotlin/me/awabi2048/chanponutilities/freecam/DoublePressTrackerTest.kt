package me.awabi2048.chanponutilities.freecam

import java.util.UUID
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue

class DoublePressTrackerTest {
    @Test
    fun `second press inside window triggers once`() {
        val tracker = DoublePressTracker()
        val player = UUID.randomUUID()
        assertFalse(tracker.press(player, 1_000, 900))
        assertTrue(tracker.press(player, 1_800, 900))
        assertFalse(tracker.press(player, 1_900, 900))
    }

    @Test
    fun `expired and reversed timestamps do not trigger`() {
        val tracker = DoublePressTracker()
        val player = UUID.randomUUID()
        assertFalse(tracker.press(player, 1_000, 900))
        assertFalse(tracker.press(player, 2_000, 900))
        assertFalse(tracker.press(player, 1_500, 900))
    }

    @Test
    fun `players are isolated`() {
        val tracker = DoublePressTracker()
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()
        assertFalse(tracker.press(first, 1_000, 900))
        assertFalse(tracker.press(second, 1_100, 900))
        assertTrue(tracker.press(first, 1_200, 900))
        assertTrue(tracker.press(second, 1_300, 900))
    }
}
