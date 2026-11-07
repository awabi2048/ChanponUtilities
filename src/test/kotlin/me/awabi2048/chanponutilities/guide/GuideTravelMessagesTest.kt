package me.awabi2048.chanponutilities.guide

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class GuideTravelMessagesTest {
    @Test fun `counts only movement and sends at independently specified times`() {
        val messages = GuideTravelMessages(listOf(GuideMessageLine("later", 3), GuideMessageLine("now", 0), GuideMessageLine("first", 1)))
        assertEquals(listOf("now"), messages.departure())
        assertEquals(emptyList<String>(), messages.tick(false))
        assertEquals(listOf("first"), messages.tick(true))
        assertEquals(2L, messages.remainingTicks)
        assertEquals(emptyList<String>(), messages.tick(false))
        assertEquals(emptyList<String>(), messages.tick(true))
        assertEquals(listOf("later"), messages.tick(true))
        assertTrue(messages.isEmpty)
    }

    @Test fun `speed cap reaches destination when final delayed line is sent`() {
        val messages = GuideTravelMessages(listOf(GuideMessageLine("last", 100)))
        var distance = 10.0
        repeat(99) {
            distance -= GuideDelayedMessageTiming.maximumStep(distance, messages.remainingTicks, 0.2)
            assertTrue(messages.tick(true).isEmpty())
            assertTrue(distance > 0.0)
        }
        distance -= GuideDelayedMessageTiming.maximumStep(distance, messages.remainingTicks, 0.2)
        assertEquals(listOf("last"), messages.tick(true))
        assertEquals(0.0, distance, 1e-8)
    }
}
