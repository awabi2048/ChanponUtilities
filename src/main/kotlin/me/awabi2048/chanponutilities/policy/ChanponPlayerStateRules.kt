package me.awabi2048.chanponutilities.policy

import java.util.UUID
import org.bukkit.GameMode

object ChanponPlayerStateRules {
    fun isBuilder(playerUuid: UUID, ownerUuid: UUID, memberUuids: Collection<UUID>, admin: Boolean): Boolean =
        admin || playerUuid == ownerUuid || playerUuid in memberUuids

    fun flightAllowed(worldAllowsFlight: Boolean, builder: Boolean, admin: Boolean): Boolean =
        worldAllowsFlight && (builder || admin)

    fun flightRestrictionApplies(gameMode: GameMode): Boolean =
        gameMode != GameMode.CREATIVE && gameMode != GameMode.SPECTATOR
}
