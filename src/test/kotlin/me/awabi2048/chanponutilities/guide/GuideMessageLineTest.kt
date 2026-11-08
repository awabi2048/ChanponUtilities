package me.awabi2048.chanponutilities.guide

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class GuideMessageLineTest {
    @Test fun `distinguishes waypoint messages from delayed messages`() {
        assertEquals(GuideMessageLine("案内", null), GuideMessageLine.parse(mapOf("text" to "案内")))
        assertEquals(GuideMessageLine("案内", 3L), GuideMessageLine.parse(mapOf("text" to "案内", "delay" to 3)))
        assertEquals(GuideMessageLine("案内", 0L), GuideMessageLine.parse(mapOf("text" to "案内", "delay" to 0)))
    }
    @Test fun `rejects invalid message definitions`() {
        assertNull(GuideMessageLine.parse(mapOf("delay" to 3)))
        assertNull(GuideMessageLine.parse(mapOf("text" to "x", "delay" to -1)))
        assertNull(GuideMessageLine.parse(mapOf("text" to "x", "delay" to 1.5)))
        assertNull(GuideMessageLine.parse(mapOf("text" to "x", "delay" to "3")))
    }
}
