package me.awabi2048.chanponutilities.guide

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class GuidePathfinderTest {
    @Test fun `clear path stays straight`() {
        val start = Triple(0.5, 2.0, 0.5); val end = Triple(5.5, 2.0, 0.5)
        assertEquals(listOf(start, end), GuidePathfinder.find(start, end) { _, _, _ -> true })
    }
    @Test fun `obstacle is avoided`() {
        val start = Triple(0.5, 2.0, 0.5); val end = Triple(5.5, 2.0, 0.5)
        val result = GuidePathfinder.find(start, end) { x, y, z -> !(x >= 2.0 && x <= 3.0 && y in 1.0..3.0 && z in -0.5..1.5) }
        assertNotNull(result)
        assertTrue(result!!.size > 2)
        for ((a, b) in result.zipWithNext()) for (i in 0..100) {
            val t = i / 100.0; val x = a.first + (b.first-a.first)*t; val y = a.second + (b.second-a.second)*t; val z = a.third + (b.third-a.third)*t
            assertFalse(x >= 2.0 && x <= 3.0 && y in 1.0..3.0 && z in -0.5..1.5)
        }
    }
    @Test fun `unusable start has no path`() {
        assertNull(GuidePathfinder.find(Triple(0.5, 2.0, 0.5), Triple(4.5, 2.0, 0.5)) { _, _, _ -> false })
    }
}
