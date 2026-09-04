package me.awabi2048.chanponutilities.listener

import me.awabi2048.myworldmanager.api.MyWorldManagerApi
import me.awabi2048.myworldmanager.api.event.MwmWorldCreatedEvent
import me.awabi2048.myworldmanager.api.event.MwmWorldWarpedEvent
import me.awabi2048.chanponutilities.policy.ChanponPlayerStateRules
import org.bukkit.GameMode
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerGameModeChangeEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerToggleFlightEvent
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.plugin.java.JavaPlugin

class PlayerStatePolicyListener(
    private val plugin: JavaPlugin
) : Listener {
    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        sync(event.player)
    }

    @EventHandler
    fun onWorldChanged(event: PlayerChangedWorldEvent) {
        sync(event.player)
    }

    @EventHandler
    fun onMwmWarp(event: MwmWorldWarpedEvent) {
        event.playerUuid.let { org.bukkit.Bukkit.getPlayer(it) }?.let(::sync)
    }

    @EventHandler
    fun onWorldCreated(event: MwmWorldCreatedEvent) {
        org.bukkit.Bukkit.getPlayer(event.ownerUuid)?.let(::sync)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onGameModeChange(event: PlayerGameModeChangeEvent) {
        // ゲームモードの変更はポリシーで上書きしません。変更後の現在モードを基準に、
        // 建築・飛行制約だけを再評価するために同期します。
        if (event.newGameMode == GameMode.SPECTATOR) return
        org.bukkit.Bukkit.getScheduler().runTask(
            plugin,
            Runnable { sync(event.player) }
        )
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onFlightToggle(event: PlayerToggleFlightEvent) {
        val player = event.player
        if (!ChanponPlayerStateRules.flightRestrictionApplies(player.gameMode)) return
        val worldData = MyWorldManagerApi.getWorldRepository()?.findByWorldKey(player.world.key.toString()) ?: return
        if (worldData.allowFlight) return
        event.isCancelled = true
        player.allowFlight = false
        if (player.isFlying) player.isFlying = false
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        if (!canBuild(event.player)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onBlockPlace(event: BlockPlaceEvent) {
        if (!canBuild(event.player)) event.isCancelled = true
    }

    private fun sync(player: org.bukkit.entity.Player) {
        val worldData = MyWorldManagerApi.getWorldRepository()?.findByWorldKey(player.world.key.toString()) ?: return
        MyWorldManagerApi.syncWorldPlayerState(player, worldData)
    }

    private fun canBuild(player: org.bukkit.entity.Player): Boolean {
        val worldData = MyWorldManagerApi.getWorldRepository()?.findByWorldKey(player.world.key.toString()) ?: return true
        return MyWorldManagerApi.canBuildInWorld(player, worldData)
    }
}
