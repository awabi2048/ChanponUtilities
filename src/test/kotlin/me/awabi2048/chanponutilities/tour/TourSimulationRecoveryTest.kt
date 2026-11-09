package me.awabi2048.chanponutilities.tour

import org.bukkit.GameMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID

class TourSimulationRecoveryTest {
    @Test
    fun `再起動後も最初のゲームモードを保持し復元後に消す`(@TempDir directory: Path) {
        val file = directory.resolve("recovery.yml").toFile()
        val player = UUID.randomUUID()
        val before = TourSimulationRecovery(file)
        before.remember(player, GameMode.CREATIVE)
        before.remember(player, GameMode.ADVENTURE)
        val restarted = TourSimulationRecovery(file)
        assertEquals(GameMode.CREATIVE, restarted.get(player))
        restarted.forget(player)
        assertNull(TourSimulationRecovery(file).get(player))
    }
}
