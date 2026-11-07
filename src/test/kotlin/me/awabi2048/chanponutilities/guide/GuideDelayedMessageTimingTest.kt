package me.awabi2048.chanponutilities.guide

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GuideDelayedMessageTimingTest {
    @Test fun `slows when normal travel would arrive before the final message`() {
        assertEquals(0.1, GuideDelayedMessageTiming.maximumStep(10.0, 100, 0.2), 1e-9)
    }

    @Test fun `never accelerates normal movement and restores it after delivery`() {
        assertEquals(0.2, GuideDelayedMessageTiming.maximumStep(30.0, 100, 0.2), 1e-9)
        assertEquals(0.2, GuideDelayedMessageTiming.maximumStep(1.0, 0, 0.2), 1e-9)
    }
}
