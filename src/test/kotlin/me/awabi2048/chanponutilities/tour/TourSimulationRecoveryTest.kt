package me.awabi2048.chanponutilities.tour

import org.bukkit.GameMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
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

    @Test
    fun `保存失敗後も復元データを保存済みと誤認しない`(@TempDir directory: Path) {
        val parent = directory.resolve("not-a-directory").toFile().also { it.writeText("x") }
        val recovery = TourSimulationRecovery(parent.resolve("state.yml"))
        val player = UUID.randomUUID()
        assertThrows(Exception::class.java) { recovery.remember(player, GameMode.CREATIVE) }
        assertNull(recovery.get(player))
    }
}
