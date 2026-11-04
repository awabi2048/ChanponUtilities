package me.awabi2048.chanponutilities.guide

import com.awabi2048.ccsystem.api.localization.generated.ChanponUtilitiesKeys as Keys
import me.awabi2048.chanponutilities.util.ChanponI18n
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

/**
 * ガイドセッションのライフサイクル境界です。
 *
 * 進捗は永続化しない仕様のため、ログアウト・ガイド対象ワールドからの退出で
 * セッションを破棄します。同一ワールド内のテレポートやデスは進行を維持します。
 */
class GuideListener(
    private val sessionService: GuideSessionService,
    private val routeStore: GuideRouteStore,
    private val plugin: JavaPlugin,
) : Listener {

    /** 座標の変化のみを判定し、視点だけの移動では領域を走査しません。 */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onMove(event: PlayerMoveEvent) {
        val from = event.from
        val to = event.to
        // ワールドを跨ぐ場合は移動完了後の PlayerChangedWorldEvent で判定します。
        if (from.world != to.world) return
        if (from.x == to.x && from.y == to.y && from.z == to.z) return
        tryAutoStart(event.player, from, to)
    }

    /** 接続直後の初期化が終わってから仮想アレイを表示します。 */
    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        val player = event.player
        plugin.server.scheduler.runTask(plugin, Runnable {
            if (player.isOnline && sessionService.isRunning()) tryAutoStart(player, null, player.location)
        })
    }

    private fun position(loc: Location): GuideAutoStart.Position? =
        loc.world?.name?.let { GuideAutoStart.Position(it, loc.x, loc.y, loc.z) }

    private fun tryAutoStart(player: Player, from: Location?, to: Location) {
        if (sessionService.statusOf(player) != null) return
        val toPos = position(to) ?: return
        val route = GuideAutoStart.select(routeStore.all(), from?.let(::position), toPos) ?: return
        sessionService.startGuide(player, route)
        player.sendMessage(ChanponI18n.text(player, Keys.CHANPON_UTILITIES_GUIDE_STARTED, "route" to route.label))
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        sessionService.onQuit(event.player)
    }

    @EventHandler
    fun onWorldChange(event: PlayerChangedWorldEvent) {
        if (sessionService.onWorldLeft(event.player)) {
            event.player.sendMessage(ChanponI18n.text(event.player, Keys.CHANPON_UTILITIES_GUIDE_WORLD_LEFT))
        }
        // 移動前ワールドのセッションを終了してから、移動先で新しい開始領域を判定します。
        tryAutoStart(event.player, null, event.player.location)
    }
}
