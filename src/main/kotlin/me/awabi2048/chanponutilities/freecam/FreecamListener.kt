package me.awabi2048.chanponutilities.freecam

import com.destroystokyo.paper.event.player.PlayerStartSpectatingEntityEvent
import me.awabi2048.chanponutilities.ChanponUtilitiesPlugin
import org.bukkit.GameMode
import org.bukkit.entity.Mannequin
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.player.PlayerCommandPreprocessEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerItemConsumeEvent
import org.bukkit.event.player.PlayerGameModeChangeEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerKickEvent
import org.bukkit.event.player.PlayerPortalEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import org.bukkit.event.player.PlayerToggleSneakEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.persistence.PersistentDataType
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class FreecamListener(
    private val plugin: ChanponUtilitiesPlugin,
    private val service: FreecamSessionService,
    private val swapHandInputClaimGate: SwapHandInputClaimGate,
) : Listener {
    private val fPresses = DoublePressTracker()
    private val shiftPresses = DoublePressTracker()
    private val firstShiftLocations = ConcurrentHashMap<UUID, org.bukkit.Location>()

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        service.recover(event.player)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onSwap(event: PlayerSwapHandItemsEvent) {
        val player = event.player
        val claimedBy = swapHandInputClaimGate.ownerOf(player.uniqueId)
        val disposition = resolveFreecamSwapDisposition(
            // 入力claim中はキャンセルや二度押し記録も行わず、入力をCC-Systemへ完全に譲ります。
            inputClaimed = claimedBy != null,
            sneaking = player.isSneaking,
            shortcutAvailable = actionsEnabled() && service.isShortcutEnabled(player.uniqueId) && !service.isActive(player.uniqueId),
            mainHandEmpty = player.inventory.itemInMainHand.type.isAir,
            offHandEmpty = player.inventory.itemInOffHand.type.isAir,
        )
        when (disposition) {
            FreecamSwapDisposition.HAND_OVER -> return
            FreecamSwapDisposition.SNEAKING_RESET -> {
                fPresses.clear(player.uniqueId)
                return
            }
            FreecamSwapDisposition.IGNORE,
            FreecamSwapDisposition.NOT_EMPTY_HANDED -> return
            FreecamSwapDisposition.TRACK_PRESS -> {}
        }
        event.isCancelled = true
        val now = System.currentTimeMillis()
        if (fPresses.press(player.uniqueId, now, actionWindow())) {
            service.start(player, FreecamStartMode.PREVIOUS_CAMERA_POSITION)
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    fun onSneak(event: PlayerToggleSneakEvent) {
        val player = event.player
        if (!actionsEnabled() || !service.isActive(player.uniqueId) || !event.isSneaking) return
        val now = System.currentTimeMillis()
        if (shiftPresses.press(player.uniqueId, now, actionWindow())) {
            val finalCameraPosition = firstShiftLocations.remove(player.uniqueId)
            service.stop(
                player.uniqueId,
                FreecamExitReason.MANUAL,
                finalCameraPosition = finalCameraPosition
            )
        } else {
            firstShiftLocations[player.uniqueId] = player.location.clone()
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onActivator(event: PlayerInteractEvent) {
        if (service.isActive(event.player.uniqueId)) {
            event.isCancelled = true
            return
        }
        if (plugin.config.getBoolean("features.freecam.activation.item.enabled", true)) {
            val item = event.item
            val marker = org.bukkit.NamespacedKey(plugin, "freecam_activator")
            if (item?.itemMeta?.persistentDataContainer?.get(marker, PersistentDataType.BYTE)?.toInt() == 1) {
                event.isCancelled = true
                service.start(event.player)
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onDamage(event: EntityDamageEvent) {
        val mannequin = event.entity as? Mannequin ?: return
        val owner = service.ownerOf(mannequin) ?: return
        event.isCancelled = true
        service.stop(owner, FreecamExitReason.BODY_DAMAGED)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onPortal(event: PlayerPortalEvent) {
        if (service.isActive(event.player.uniqueId)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onTeleport(event: PlayerTeleportEvent) {
        if (service.isActive(event.player.uniqueId) && !service.isInternalTeleport(event.player.uniqueId)) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onStartSpectatingEntity(event: PlayerStartSpectatingEntityEvent) {
        if (service.isActive(event.player.uniqueId)) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onInventory(event: InventoryOpenEvent) {
        val player = event.player as? org.bukkit.entity.Player ?: return
        if (service.isActive(player.uniqueId)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? org.bukkit.entity.Player ?: return
        if (service.isActive(player.uniqueId)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onEntityInteract(event: PlayerInteractEntityEvent) {
        if (service.isActive(event.player.uniqueId)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onDrop(event: PlayerDropItemEvent) {
        if (service.isActive(event.player.uniqueId)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onPickup(event: EntityPickupItemEvent) {
        val player = event.entity as? org.bukkit.entity.Player ?: return
        if (service.isActive(player.uniqueId)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onConsume(event: PlayerItemConsumeEvent) {
        if (service.isActive(event.player.uniqueId)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onCommand(event: PlayerCommandPreprocessEvent) {
        if (!service.isActive(event.player.uniqueId)) return
        val root = event.message.removePrefix("/").substringBefore(' ').substringAfter(':').lowercase()
        val allowed = plugin.config.getStringList("features.freecam.restrictions.allowed_commands")
            .map { it.lowercase() }.toSet()
        if (root !in allowed) event.isCancelled = true
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        service.stop(event.player.uniqueId, FreecamExitReason.PLAYER_QUIT, false)
        clear(event.player.uniqueId)
    }

    @EventHandler
    fun onKick(event: PlayerKickEvent) {
        service.stop(event.player.uniqueId, FreecamExitReason.PLAYER_KICK, false)
        clear(event.player.uniqueId)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onGameMode(event: PlayerGameModeChangeEvent) {
        if (service.isActive(event.player.uniqueId) && event.newGameMode != GameMode.SPECTATOR) {
            plugin.server.scheduler.runTask(plugin, Runnable {
                service.stop(event.player.uniqueId, FreecamExitReason.GAME_MODE_CHANGED)
            })
        }
        if (event.newGameMode != GameMode.SPECTATOR) clear(event.player.uniqueId)
    }

    private fun actionsEnabled() = plugin.config.getBoolean("features.freecam.activation.action.enabled", true)
    private fun actionWindow() = plugin.config
        .getLong("features.freecam.activation.action.window_ticks", 10L)
        .coerceIn(1L, 60L) * MILLISECONDS_PER_TICK

    private companion object {
        const val MILLISECONDS_PER_TICK = 50L
    }
    private fun clear(uuid: UUID) {
        fPresses.clear(uuid)
        shiftPresses.clear(uuid)
        firstShiftLocations.remove(uuid)
    }
}
