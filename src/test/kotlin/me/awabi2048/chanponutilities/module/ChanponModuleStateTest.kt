package me.awabi2048.chanponutilities.module

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChanponModuleStateTest {
    @Test
    fun `all modules have stable ids and config paths`() {
        assertEquals(
            listOf("freecam", "lectern_bookshelf_link", "tour_safety", "player_state", "command_safeguard"),
            ChanponModule.all.map(ChanponModule::id),
        )
        assertEquals(
            listOf(
                "features.freecam.enabled",
                "features.lectern_bookshelf_link.enabled",
                "features.tour_safety.enabled",
                "features.player_state.enabled",
                "features.command_safeguard.enabled",
            ),
            ChanponModule.all.map(ChanponModule::enabledConfigPath),
        )
    }

    @Test
    fun `partial config uses explicit values and defaults the rest`() {
        val state = ChanponModuleState.fromConfig(
            mapOf(
                "freecam" to false,
                "PLAYER_STATE" to false,
            ),
        )

        assertFalse(state.isEnabled(ChanponModule.FREECAM))
        assertTrue(state.isEnabled(ChanponModule.LECTERN_BOOKSHELF_LINK))
        assertTrue(state.isEnabled(ChanponModule.TOUR_SAFETY))
        assertFalse(state.isEnabled(ChanponModule.PLAYER_STATE))
    }

    @Test
    fun `state changes return a new value without mutating the original`() {
        val original = ChanponModuleState.defaults()
        val changed = original.setEnabled(ChanponModule.TOUR_SAFETY, false)

        assertTrue(original.isEnabled(ChanponModule.TOUR_SAFETY))
        assertFalse(changed.isEnabled(ChanponModule.TOUR_SAFETY))
        assertEquals(
            setOf(
                ChanponModule.FREECAM,
                ChanponModule.LECTERN_BOOKSHELF_LINK,
                ChanponModule.PLAYER_STATE,
                ChanponModule.COMMAND_SAFEGUARD,
            ),
            changed.enabledModules(),
        )
        assertEquals(false, changed.asConfigValues()["tour_safety"])
    }

    @Test
    fun `unknown config ids fail instead of being silently ignored`() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            ChanponModuleState.fromConfig(mapOf("unknown" to true))
        }

        assertTrue(exception.message.orEmpty().contains("unknown"))
    }
}
