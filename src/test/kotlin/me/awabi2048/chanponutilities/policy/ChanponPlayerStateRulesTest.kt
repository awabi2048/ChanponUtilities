package me.awabi2048.chanponutilities.policy

import java.util.UUID
import org.bukkit.GameMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChanponPlayerStateRulesTest {
    private val owner = UUID.randomUUID()
    private val member = UUID.randomUUID()
    private val guest = UUID.randomUUID()

    @Test
    fun `owner and member are builders but guest is not`() {
        assertTrue(ChanponPlayerStateRules.isBuilder(owner, owner, listOf(member), admin = false))
        assertTrue(ChanponPlayerStateRules.isBuilder(member, owner, listOf(member), admin = false))
        assertFalse(ChanponPlayerStateRules.isBuilder(guest, owner, listOf(member), admin = false))
    }

    @Test
    fun `admin bypasses build and game mode assignment`() {
        assertTrue(ChanponPlayerStateRules.isBuilder(guest, owner, emptyList(), admin = true))
        assertNull(ChanponPlayerStateRules.gameModeForBuilder(builder = true, admin = true))
        assertTrue(ChanponPlayerStateRules.flightAllowed(true, builder = true, admin = true))
        assertFalse(ChanponPlayerStateRules.flightAllowed(false, builder = true, admin = true))
    }

    @Test
    fun `ordinary builders use survival and guests use adventure`() {
        assertEquals(GameMode.SURVIVAL, ChanponPlayerStateRules.gameModeForBuilder(true, admin = false))
        assertEquals(GameMode.ADVENTURE, ChanponPlayerStateRules.gameModeForBuilder(false, admin = false))
        assertFalse(ChanponPlayerStateRules.flightAllowed(true, builder = false, admin = false))
    }

    @Test
    fun `flight restriction excludes creative and spectator modes`() {
        assertFalse(ChanponPlayerStateRules.flightRestrictionApplies(GameMode.CREATIVE))
        assertFalse(ChanponPlayerStateRules.flightRestrictionApplies(GameMode.SPECTATOR))
        assertTrue(ChanponPlayerStateRules.flightRestrictionApplies(GameMode.SURVIVAL))
        assertTrue(ChanponPlayerStateRules.flightRestrictionApplies(GameMode.ADVENTURE))
    }
}
