package me.awabi2048.chanponutilities.guide

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID

class GuideAutoStartHistoryTest {
    @TempDir lateinit var directory: Path

    @Test fun `history is retained per player and route and can be cleared`() {
        val file = directory.resolve("history.yml")
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()
        val initial = GuideAutoStartHistory(file)
        initial.record(first, "station")
        initial.record(first, "museum")
        initial.record(second, "station")
        val reloaded = GuideAutoStartHistory(file)
        assertTrue(reloaded.hasStarted(first, "station"))
        assertTrue(reloaded.clear(first, "station"))
        val afterClear = GuideAutoStartHistory(file)
        assertFalse(afterClear.hasStarted(first, "station"))
        assertTrue(afterClear.hasStarted(first, "museum"))
        assertTrue(afterClear.hasStarted(second, "station"))
    }
}
