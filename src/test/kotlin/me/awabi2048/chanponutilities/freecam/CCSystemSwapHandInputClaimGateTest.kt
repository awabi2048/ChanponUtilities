package me.awabi2048.chanponutilities.freecam

import com.awabi2048.ccsystem.api.input.PlayerInteractionChannel
import com.awabi2048.ccsystem.api.input.PlayerInteractionClaim
import com.awabi2048.ccsystem.api.input.PlayerInteractionClaimService
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CCSystemSwapHandInputClaimGateTest {
    @Test
    fun `FキーのclaimだけをFreecamの競合として扱う`() {
        val playerId = UUID.randomUUID()
        val service = FakeClaimService()
        val gate = CCSystemSwapHandInputClaimGate(service)

        service.claim(playerId, PlayerInteractionChannel.PRIMARY, "other")
        assertFalse(gate.isClaimed(playerId))

        service.claim(playerId, PlayerInteractionChannel.SWAP_HAND, "gesture-gui")
        assertTrue(gate.isClaimed(playerId))
    }

    private class FakeClaimService : PlayerInteractionClaimService {
        private val owners = mutableMapOf<Pair<UUID, PlayerInteractionChannel>, String>()

        override fun claim(
            playerId: UUID,
            channel: PlayerInteractionChannel,
            owner: String,
        ): PlayerInteractionClaim? {
            val key = playerId to channel
            if (owners.putIfAbsent(key, owner) != null) return null
            return object : PlayerInteractionClaim {
                override val playerId = playerId
                override val channel = channel
                override val owner = owner
                override val active: Boolean get() = owners[key] == owner
                override fun close() {
                    owners.remove(key, owner)
                }
            }
        }

        override fun ownerOf(playerId: UUID, channel: PlayerInteractionChannel): String? =
            owners[playerId to channel]

        override fun release(playerId: UUID, channel: PlayerInteractionChannel, owner: String): Boolean =
            owners.remove(playerId to channel, owner)

        override fun releaseAll(playerId: UUID) {
            owners.keys.removeIf { it.first == playerId }
        }

        override fun releaseAll(playerId: UUID, owner: String) {
            owners.entries.removeIf { it.key.first == playerId && it.value == owner }
        }
    }
}
