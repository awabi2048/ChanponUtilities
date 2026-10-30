package me.awabi2048.chanponutilities.guide

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class GuideWaypointProgressTest {
    @Test fun `cylinder includes its boundaries and excludes points beyond them`() {
        fun inside(x: Double, y: Double, z: Double) = GuideWaypointProgress.contains(x, y, z, 0.0, 64.0, 0.0, 3.0, 3.0)
        assertTrue(inside(3.0, 67.0, 0.0))
        assertTrue(inside(0.0, 61.0, 0.0))
        assertFalse(inside(3.001, 64.0, 0.0))
        assertFalse(inside(0.0, 67.001, 0.0))
    }
    @Test fun `leaving before two seconds resets the dwell`() {
        val progress = GuideWaypointProgress(40, 100)
        repeat(39) { assertEquals(GuideWaypointProgress.Event.NONE, progress.tick(true)) }
        assertEquals(GuideWaypointProgress.Event.NONE, progress.tick(false))
        repeat(39) { assertEquals(GuideWaypointProgress.Event.NONE, progress.tick(true)) }
        assertEquals(GuideWaypointProgress.Event.MESSAGE, progress.tick(true))
    }
    @Test fun `message wait continues outside without resending`() {
        val progress = GuideWaypointProgress(40, 100)
        repeat(39) { progress.tick(true) }
        assertEquals(GuideWaypointProgress.Event.MESSAGE, progress.tick(true))
        repeat(99) { assertEquals(GuideWaypointProgress.Event.NONE, progress.tick(false)) }
        assertEquals(GuideWaypointProgress.Event.ADVANCE, progress.tick(false))
    }
    @Test fun `zero lines advances immediately after dwell`() {
        val progress = GuideWaypointProgress(40, 0)
        repeat(39) { progress.tick(true) }
        assertEquals(GuideWaypointProgress.Event.MESSAGE_AND_ADVANCE, progress.tick(true))
    }
}
