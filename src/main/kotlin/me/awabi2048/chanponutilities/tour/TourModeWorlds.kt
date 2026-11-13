package me.awabi2048.chanponutilities.tour

import me.awabi2048.myworldmanager.api.MyWorldManagerApi
import org.bukkit.entity.Player

/** MWM のクラスはプラグインが有効な場合に限って参照します。 */
internal object TourModeWorlds {
    fun allowsFlight(player: Player): Boolean =
        MyWorldManagerApi.getWorldRepository()?.findByWorldKey(player.world.key.toString())
            ?.let { MyWorldManagerApi.isFlightAllowed(player, it) }
            ?: (MyWorldManagerApi.localFlightRequest(player) ?: true)

    fun restore(player: Player): Boolean {
        val data = MyWorldManagerApi.getWorldRepository()?.findByWorldKey(player.world.key.toString()) ?: return false
        MyWorldManagerApi.syncWorldPlayerState(player, data)
        return true
    }
}
