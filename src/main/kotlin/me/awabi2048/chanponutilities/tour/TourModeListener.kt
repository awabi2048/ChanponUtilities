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
import java.util.UUID

/** 定期監視を使わず、参加・移動・モード変更の境界で観光状態を整えます。 */
internal class TourModeListener(
    private val plugin: ChanponUtilitiesPlugin,
    private val isFreecamActive: (UUID) -> Boolean,
) : Listener {
    private fun bypass(player: Player): Boolean = isTourAdmin(player)

    fun sync(player: Player) {
        if (!player.isOnline || bypass(player)) return
        val expected = tourGameMode(isFreecamActive(player.uniqueId))
        if (player.gameMode != expected) {
            player.gameMode = expected
            if (player.gameMode == expected) notify(player)
        }
        // FreeCam中の飛行はスペクテイターモード自身が管理します。
        if (expected == GameMode.SPECTATOR) return
        if (player.gameMode != GameMode.ADVENTURE) return
        val allow = !plugin.server.pluginManager.isPluginEnabled("MyWorldManager") || TourModeWorlds.allowsFlight(player)
        if (!allow && player.isFlying) player.isFlying = false
        player.allowFlight = allow
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onJoin(event: PlayerJoinEvent) = later(event.player)

    @EventHandler(priority = EventPriority.MONITOR)
    fun onWorldChange(event: PlayerChangedWorldEvent) = later(event.player)

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun afterGameModeChange(event: PlayerGameModeChangeEvent) {
        // モード変更時にMinecraft自身が飛行権限を初期化するため、変更完了後に再適用します。
        later(event.player)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onGameMode(event: PlayerGameModeChangeEvent) {
        if (bypass(event.player) || event.newGameMode == tourGameMode(isFreecamActive(event.player.uniqueId))) return
        event.isCancelled = true
        notify(event.player)
        // イベント中に再変更せず、現在のFreeCam状態から次tickで正しいモードを再評価します。
        later(event.player)
    }

    fun later(player: Player) {
        plugin.server.scheduler.runTask(plugin, Runnable { sync(player) })
    }

    private fun notify(player: Player) {
        player.sendMessage(ChanponI18n.text(player, ChanponUtilitiesKeys.CHANPON_UTILITIES_TOUR_MODE_GAME_MODE_LOCKED))
    }
}

/** FreeCamの開始・終了と観光モードの同期が同じ判定を使うようにします。 */
internal fun tourGameMode(freecamActive: Boolean): GameMode =
    if (freecamActive) GameMode.SPECTATOR else GameMode.ADVENTURE
