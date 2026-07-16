package me.awabi2048.chanponutilities.policy

import me.awabi2048.myworldmanager.api.extension.WorldPlayerStateDecision
import me.awabi2048.myworldmanager.api.extension.WorldPlayerStatePolicy
import me.awabi2048.myworldmanager.model.WorldData
import org.bukkit.GameMode
import org.bukkit.entity.Player

class ChanponPlayerStatePolicy : WorldPlayerStatePolicy {
    override fun getId(): String = POLICY_ID

    override fun getPriority(): Int = 10

    override fun evaluate(player: Player, worldData: WorldData): WorldPlayerStateDecision {
        val admin = isAdmin(player)
        val builder = ChanponPlayerStateRules.isBuilder(player.uniqueId, worldData.owner, worldData.members, admin)
        return WorldPlayerStateDecision(
            gameMode = ChanponPlayerStateRules.gameModeForBuilder(builder, admin),
            buildAllowed = builder,
            flightAllowed = ChanponPlayerStateRules.flightAllowed(worldData.allowFlight, builder, admin)
        )
    }

    private fun isAdmin(player: Player): Boolean =
        player.hasPermission("myworldmanager.admin") ||
            player.hasPermission("mwmchanpon.admin") ||
            player.hasPermission("chanponutilities.admin")

    companion object {
        const val POLICY_ID = "chanpon-utilities.player_state"
    }
}
