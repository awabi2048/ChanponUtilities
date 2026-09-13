package me.awabi2048.chanponutilities.safety

import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerCommandPreprocessEvent
import org.bukkit.event.player.PlayerKickEvent
import org.bukkit.event.player.PlayerQuitEvent

/**
 * コマンドセーフガードのイベント境界です。
 * LOWESTで受付し、通常のコマンド権限判定や既存のFreecamコマンド制限より前に
 * 広範囲コマンドを停止します。権限値による分岐は行いません。
 */
internal class CommandSafeguardListener(
    private val service: CommandSafeguardService,
) : Listener {
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    fun onCommand(event: PlayerCommandPreprocessEvent) {
        if (service.intercept(event.player, event.message)) {
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        service.cancel(event.player)
    }

    @EventHandler
    fun onKick(event: PlayerKickEvent) {
        service.cancel(event.player)
    }
}
