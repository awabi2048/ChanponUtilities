package me.awabi2048.chanponutilities.guide

import com.awabi2048.ccsystem.api.localization.generated.ChanponUtilitiesKeys as Keys
import me.awabi2048.chanponutilities.util.ChanponI18n
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask

/** ガイドの自動開始判定とセッションのライフサイクル境界です。 */
class GuideListener(
    private val sessionService: GuideSessionService,
    private val routeStore: GuideRouteStore,
    private val plugin: JavaPlugin,
) : Listener {
    private val poller = GuideAutoStartPoller()
    private var pollTask: BukkitTask? = null

    /** 再読込時に領域内にいるだけで新しいガイドが始まらないよう、初期位置を記録します。 */
    fun start() {
        plugin.server.onlinePlayers.forEach { player -> position(player.location)?.let { poller.record(player.uniqueId, it) } }
        pollTask = plugin.server.scheduler.runTaskTimer(plugin, Runnable { pollPlayers() }, 5L, 5L)
    }

    fun shutdown() {
        pollTask?.cancel()
        pollTask = null
        poller.clear()
    }

    /** 位置はガイド中も記録し、ガイド終了後に同じ領域で再開始されないようにします。 */
    private fun pollPlayers() {
        for (player in plugin.server.onlinePlayers) {
            val current = position(player.location) ?: continue
            val active = sessionService.isGuiding(player)
            val route = poller.poll(player.uniqueId, current, routeStore.autoStartRoutes(current.world).filterNot { routeStore.autoStartHistory.hasStarted(player.uniqueId, it.id) }, active)
            if (route != null) startSelectedRoute(player, route)
        }
    }

    /** 初期接続処理が終わった次tickにログイン位置を判定します。 */
    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        val player = event.player
        plugin.server.scheduler.runTask(plugin, Runnable {
            if (!player.isOnline || !sessionService.isRunning() || pollTask == null) return@Runnable
            val current = position(player.location) ?: return@Runnable
            poller.record(player.uniqueId, current)
            tryAutoStart(player, null, current)
        })
    }

    /** 同一ワールドのテレポートは経路を通過せず、移動元と移動先だけを判定します。 */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onTeleport(event: PlayerTeleportEvent) {
        val from = position(event.from) ?: return
        val to = position(event.to) ?: return
        // ワールド移動は完了後のイベントで旧セッションを終了してから判定します。
        if (from.world != to.world) return
        poller.record(event.player.uniqueId, to)
        tryAutoStart(event.player, from, to)
    }

    private fun position(loc: Location): GuideAutoStart.Position? =
        loc.world?.name?.let { GuideAutoStart.Position(it, loc.x, loc.y, loc.z) }

    private fun tryAutoStart(player: Player, from: GuideAutoStart.Position?, to: GuideAutoStart.Position) {
        if (sessionService.isGuiding(player)) return
        val route = GuideAutoStart.select(routeStore.autoStartRoutes(to.world).filterNot { routeStore.autoStartHistory.hasStarted(player.uniqueId, it.id) }, from, to) ?: return
        startSelectedRoute(player, route)
    }

    private fun startSelectedRoute(player: Player, route: GuideRoute) {
        try {
            routeStore.autoStartHistory.record(player.uniqueId, route.id)
        } catch (error: Exception) {
            plugin.logger.log(java.util.logging.Level.SEVERE, "自動開始履歴を保存できません: ${player.uniqueId} / ${route.id}", error)
            return
        }
        sessionService.startGuide(player, route)

    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        poller.forget(event.player.uniqueId)
        sessionService.onQuit(event.player)
    }

    @EventHandler
    fun onWorldChange(event: PlayerChangedWorldEvent) {
        sessionService.onWorldLeft(event.player)
        val current = position(event.player.location) ?: return
        poller.record(event.player.uniqueId, current)
        tryAutoStart(event.player, null, current)
    }
}
