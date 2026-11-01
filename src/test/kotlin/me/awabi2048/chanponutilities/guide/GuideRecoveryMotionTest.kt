package me.awabi2048.chanponutilities.guide

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class GuideRecoveryMotionTest {
    @Test fun `recovery continues for five seconds of inactivity`() {
        val motion = GuideRecoveryMotion(100)
        assertFalse(motion.update(10.0, 0.0, 0.0, 10.0))
        assertTrue(motion.update(9.8, 0.0, 0.0, 9.8))
        repeat(99) { assertTrue(motion.update(9.8, 0.0, 0.0, 9.8)) }
        assertFalse(motion.update(9.8, 0.0, 0.0, 9.8))
    }
    @Test fun `moving away cancels recovery and approach restarts it`() {
        val motion = GuideRecoveryMotion()
        motion.update(10.0, 0.0, 0.0, 10.0)
        assertTrue(motion.update(9.8, 0.0, 0.0, 9.8))
        assertFalse(motion.update(10.0, 0.0, 0.0, 10.0))
        assertTrue(motion.update(9.8, 0.0, 0.0, 9.8))
    }
}
