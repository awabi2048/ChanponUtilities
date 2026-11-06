package me.awabi2048.chanponutilities.guide

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class GuideWaypointProgressTest {
    @Test fun `cylinder includes boundaries`() {
        fun inside(x: Double, y: Double, z: Double) = GuideWaypointProgress.contains(x, y, z, 0.0, 64.0, 0.0, 3.0, 3.0)
        assertTrue(inside(3.0, 67.0, 0.0))
        assertFalse(inside(3.001, 64.0, 0.0))
    }

    @Test fun `leaving before dwell completion resets it`() {
        val progress = GuideWaypointProgress(40, listOf("abc"))
        repeat(39) { assertEquals(GuideWaypointProgress.Event(), progress.tick(true)) }
        assertEquals(GuideWaypointProgress.Event(), progress.tick(false))
        repeat(39) { assertEquals(GuideWaypointProgress.Event(), progress.tick(true)) }
        assertEquals(GuideWaypointProgress.Event(started = true, lineIndices = listOf(0)), progress.tick(true))
    }

    @Test fun `lines and last wait continue outside with color codes excluded`() {
        val progress = GuideWaypointProgress(1, listOf("&aあい", "&b猫"))
        assertEquals(GuideWaypointProgress.Event(started = true, lineIndices = listOf(0)), progress.tick(true))
        repeat(9) { assertEquals(GuideWaypointProgress.Event(), progress.tick(false)) }
        assertEquals(GuideWaypointProgress.Event(lineIndices = listOf(1)), progress.tick(false))
        repeat(4) { assertEquals(GuideWaypointProgress.Event(), progress.tick(false)) }
        assertEquals(GuideWaypointProgress.Event(advance = true), progress.tick(false))
    }

    @Test fun `empty lines do not introduce extra delay`() {
        val progress = GuideWaypointProgress(1, listOf("", "&a", "a"))
        assertEquals(GuideWaypointProgress.Event(started = true, lineIndices = listOf(0, 1, 2)), progress.tick(true))
        repeat(4) { assertEquals(GuideWaypointProgress.Event(), progress.tick(false)) }
        assertEquals(GuideWaypointProgress.Event(advance = true), progress.tick(false))
    }

    @Test fun `no lines advance as soon as dwell completes`() {
        val progress = GuideWaypointProgress(1, emptyList())
        assertEquals(GuideWaypointProgress.Event(started = true, advance = true), progress.tick(true))
    }

    @Test fun `surrogate pair counts as one character`() {
        assertEquals(1, GuideWaypointProgress.displayLength("&e😀"))
    }
}
