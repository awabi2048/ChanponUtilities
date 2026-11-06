package me.awabi2048.chanponutilities.guide

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class GuideSmoothPathTest {
    @Test fun `passes through each specified point with a smooth bend`() {
        val points = listOf(Triple(0.0, 0.0, 0.0), Triple(4.0, 0.0, 2.0), Triple(8.0, 0.0, 0.0))
        val result = requireNotNull(GuideSmoothPath.build(points) { _, _, _ -> true })
        val i = result.indexOf(points[1])
        assertTrue(i > 0 && i < result.lastIndex)
        assertTrue(result[i - 1].first < result[i].first && result[i + 1].first > result[i].first)
        assertEquals(points.first(), result.first())
        assertEquals(points.last(), result.last())
    }

    @Test fun `does not return an obstructed path`() {
        val points = listOf(Triple(0.0, 0.0, 0.0), Triple(4.0, 0.0, 0.0))
        assertNull(GuideSmoothPath.build(points) { x, _, _ -> x < 1.9 || x > 2.1 })
    }
}
