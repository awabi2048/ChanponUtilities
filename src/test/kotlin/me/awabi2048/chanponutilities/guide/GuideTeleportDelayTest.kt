package me.awabi2048.chanponutilities.guide

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class GuideTeleportDelayTest {
    @Test fun `requires one hundred consecutive ticks beyond the threshold`() {
        val delay = GuideTeleportDelay()
        repeat(99) { assertFalse(delay.tick(1024.0)) }
        assertFalse(delay.tick(1023.9))
        repeat(99) { assertFalse(delay.tick(1024.0)) }
        assertTrue(delay.tick(1024.0))
        assertTrue(delay.tick(1024.0))
        assertFalse(delay.tick(0.0))
    }

    @Test fun `only the player closer to previous stop is behind`() {
        assertTrue(GuideCourseMath.playerBehind(25.0, 36.0))
        assertFalse(GuideCourseMath.playerBehind(36.0, 36.0))
        assertFalse(GuideCourseMath.playerBehind(49.0, 36.0))
    }
}
