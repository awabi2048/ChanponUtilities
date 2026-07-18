package me.awabi2048.chanponutilities.freecam

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SpectatorTargetSourceContractTest {
    @Test
    fun `active freecam cancels entity spectating`() {
        val source = Files.readString(
            Path.of("src/main/kotlin/me/awabi2048/chanponutilities/freecam/FreecamListener.kt")
        )
        assertTrue(source.contains("PlayerStartSpectatingEntityEvent"))
        assertTrue(source.contains("if (service.isActive(event.player.uniqueId))"))
        assertTrue(source.contains("event.isCancelled = true"))
    }
}
