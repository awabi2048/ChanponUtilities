package me.awabi2048.chanponutilities.freecam

import java.util.UUID
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue

class DoublePressTrackerTest {
    private val tenTicks = 500L

    @Test
    fun `second press inside ten ticks triggers once`() {
        val tracker = DoublePressTracker()
        val player = UUID.randomUUID()
        assertFalse(tracker.press(player, 1_000, tenTicks))
        assertTrue(tracker.press(player, 1_500, tenTicks))
        assertFalse(tracker.press(player, 1_600, tenTicks))
    }

    @Test
    fun `expired and reversed timestamps do not trigger`() {
        val tracker = DoublePressTracker()
        val player = UUID.randomUUID()
        assertFalse(tracker.press(player, 1_000, tenTicks))
        assertFalse(tracker.press(player, 1_501, tenTicks))
        assertFalse(tracker.press(player, 1_000, tenTicks))
    }

    @Test
    fun `players are isolated`() {
        val tracker = DoublePressTracker()
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()
        assertFalse(tracker.press(first, 1_000, tenTicks))
        assertFalse(tracker.press(second, 1_100, tenTicks))
        assertTrue(tracker.press(first, 1_200, tenTicks))
        assertTrue(tracker.press(second, 1_300, tenTicks))
    }
}
