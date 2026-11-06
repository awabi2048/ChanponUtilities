package me.awabi2048.chanponutilities.guide

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class GuideParticleSpacingTest {
    @Test fun `distance remainder carries across ticks and resets after teleport`() {
        val spacing = GuideParticleSpacing()
        assertEquals(listOf(Triple(0.0, 0.0, 0.0)), spacing.points(Triple(0.0, 0.0, 0.0)))
        assertTrue(spacing.points(Triple(0.04, 0.0, 0.0)).isEmpty())
        val first = spacing.points(Triple(0.12, 0.0, 0.0))
        assertEquals(1, first.size)
        assertEquals(0.1, first.single().first, 1e-9)
        val second = spacing.points(Triple(0.31, 0.0, 0.0))
        assertEquals(2, second.size)
        assertEquals(0.2, second[0].first, 1e-9)
        assertEquals(0.3, second[1].first, 1e-9)
        spacing.reset()
        assertEquals(listOf(Triple(40.0, 0.0, 0.0)), spacing.points(Triple(40.0, 0.0, 0.0)))
    }
}
