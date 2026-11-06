package me.awabi2048.chanponutilities.guide

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GuideParticleRadiusTest {
    @Test fun `radius changes linearly and retargets from the current radius`() {
        val radius = GuideParticleRadius(2.0)
        repeat(9) { radius.tick(6.0) }
        assertEquals(4.0, radius.tick(6.0), 1e-9)
        assertEquals(3.8375, radius.tick(0.75), 1e-9)
        repeat(19) { radius.tick(0.75) }
        assertEquals(0.75, radius.tick(0.75), 1e-9)
    }
}
