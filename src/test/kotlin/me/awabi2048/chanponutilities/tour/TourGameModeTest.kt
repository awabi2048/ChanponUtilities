package me.awabi2048.chanponutilities.tour

import org.bukkit.GameMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TourGameModeTest {
    @Test fun `FreeCamの開始と終了で強制するモードを切り替える`() {
        assertEquals(GameMode.ADVENTURE, tourGameMode(false))
        assertEquals(GameMode.SPECTATOR, tourGameMode(true))
        assertEquals(GameMode.ADVENTURE, tourGameMode(false))
    }
}
