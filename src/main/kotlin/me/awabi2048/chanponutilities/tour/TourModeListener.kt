package me.awabi2048.chanponutilities.tour

import com.awabi2048.ccsystem.api.localization.generated.ChanponUtilitiesKeys
import me.awabi2048.chanponutilities.ChanponUtilitiesPlugin
import me.awabi2048.chanponutilities.util.ChanponI18n
import org.bukkit.GameMode
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerGameModeChangeEvent
import org.bukkit.event.player.PlayerJoinEvent

/** 定期監視を使わず、参加・移動・モード変更の境界で観光状態を整えます。 */
internal class TourModeListener(private val plugin: ChanponUtilitiesPlugin) : Listener {
    private fun bypass(player: Player): Boolean = player.hasPermission("chanponutilities.admin")

    fun sync(player: Player) {
        if (!player.isOnline || bypass(player)) return
        if (player.gameMode != GameMode.ADVENTURE) {
            player.gameMode = GameMode.ADVENTURE
            if (player.gameMode == GameMode.ADVENTURE) notify(player)
        }
        if (player.gameMode != GameMode.ADVENTURE) return
        val allow = !plugin.server.pluginManager.isPluginEnabled("MyWorldManager") || TourModeWorlds.allowsFlight(player)
        if (!allow && player.isFlying) player.isFlying = false
        player.allowFlight = allow
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onJoin(event: PlayerJoinEvent) = later(event.player)

    @EventHandler(priority = EventPriority.MONITOR)
    fun onWorldChange(event: PlayerChangedWorldEvent) = later(event.player)

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onGameMode(event: PlayerGameModeChangeEvent) {
        if (bypass(event.player) || event.newGameMode == GameMode.ADVENTURE) return
        event.isCancelled = true
        notify(event.player)
    }

    private fun later(player: Player) {
        plugin.server.scheduler.runTask(plugin, Runnable { sync(player) })
    }

    private fun notify(player: Player) {
        player.sendMessage(ChanponI18n.text(player, ChanponUtilitiesKeys.CHANPON_UTILITIES_TOUR_MODE_GAME_MODE_LOCKED))
    }
}
