package me.awabi2048.chanponutilities.guide

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class GuideStopPlanTest {
    @Test fun `passes intermediate points and selects next stop`() {
        val points = listOf(true, false, false, true, false)
        assertEquals(3, GuideStopPlan.nextStopIndex(0, points))
        assertEquals(4, GuideStopPlan.nextStopIndex(3, points))
        assertNull(GuideStopPlan.nextStopIndex(4, points))
    }
}
