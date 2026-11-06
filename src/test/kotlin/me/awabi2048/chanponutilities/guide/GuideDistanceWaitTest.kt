package me.awabi2048.chanponutilities.guide

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class GuideDistanceWaitTest {
    @Test fun `delay and separate distances prevent boundary flicker`() {
        val wait = GuideDistanceWait()
        repeat(19) { assertFalse(wait.tick(true, 144.0)) }
        assertTrue(wait.tick(true, 144.0))
        repeat(50) { assertTrue(wait.tick(true, 121.0)) }
        repeat(19) { assertTrue(wait.tick(true, 99.0)) }
        assertFalse(wait.tick(true, 99.0))
    }

    @Test fun `interrupted transitions restart and leaving the course clears waiting`() {
        val wait = GuideDistanceWait()
        repeat(19) { wait.tick(true, 144.0) }
        assertFalse(wait.tick(true, 143.0))
        assertFalse(wait.tick(true, 144.0))
        repeat(19) { wait.tick(true, 144.0) }
        assertTrue(wait.paused)
        assertFalse(wait.tick(false, 400.0))
    }
}
