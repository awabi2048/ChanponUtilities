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
}
