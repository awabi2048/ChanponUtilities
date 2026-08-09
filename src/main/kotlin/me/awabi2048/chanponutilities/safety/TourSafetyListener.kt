package me.awabi2048.chanponutilities.safety

import me.awabi2048.chanponutilities.ChanponUtilitiesPlugin
import me.awabi2048.chanponutilities.listener.shouldUseLecternSneakRead
import me.awabi2048.chanponutilities.module.ChanponModule
import org.bukkit.Material
import org.bukkit.block.Lectern
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.event.Cancellable
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityPlaceEvent
import org.bukkit.event.entity.EntityShootBowEvent
import org.bukkit.event.entity.PlayerLeashEntityEvent
import org.bukkit.event.hanging.HangingBreakByEntityEvent
import org.bukkit.event.hanging.HangingPlaceEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.event.player.PlayerArmorStandManipulateEvent
import org.bukkit.event.player.PlayerBucketEmptyEvent
import org.bukkit.event.player.PlayerBucketFillEvent
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerFishEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.event.player.PlayerShearEntityEvent
import org.bukkit.event.player.PlayerTakeLecternBookEvent
import org.bukkit.inventory.EquipmentSlot

class TourSafetyListener(
    private val plugin: ChanponUtilitiesPlugin,
    private val service: TourSafetyService,
) : Listener {
    @EventHandler fun onJoin(event: PlayerJoinEvent) = service.synchronize(event.player)
    @EventHandler fun onWorldChanged(event: PlayerChangedWorldEvent) = service.synchronize(event.player)
    @EventHandler fun onRespawn(event: PlayerRespawnEvent) = service.synchronize(event.player)

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onInteract(event: PlayerInteractEvent) {
        if (!service.isEnabled(event.player)) return
        val clicked = event.clickedBlock
        if (
            clicked?.type == Material.LECTERN &&
            (clicked.state as? Lectern)?.inventory?.getItem(0)?.type in setOf(Material.WRITTEN_BOOK, Material.WRITABLE_BOOK)
        ) {
            val handEmpty = event.item == null || event.item!!.type.isAir
            val featureMode = event.action == Action.RIGHT_CLICK_BLOCK &&
                event.hand == EquipmentSlot.HAND &&
                plugin.isModuleEnabled(ChanponModule.LECTERN_BOOKSHELF_LINK) &&
                event.player.hasPermission("chanponutilities.read") &&
                shouldUseLecternSneakRead(
                    isSneaking = event.player.isSneaking,
                    handEmpty = handEmpty,
                    requireEmptyHand = plugin.config.getBoolean(
                        "features.lectern_bookshelf_link.sneak_read.require_empty_hand",
                        true,
                    ),
                    reverse = plugin.config.getBoolean("features.lectern_bookshelf_link.sneak_read.reverse", false),
                )
            if (featureMode) return
        }
        reject(event.player, event)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onBreak(event: BlockBreakEvent) = reject(event.player, event)

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onPlace(event: BlockPlaceEvent) = reject(event.player, event)

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onInteractEntity(event: PlayerInteractEntityEvent) = reject(event.player, event)

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onArmorStand(event: PlayerArmorStandManipulateEvent) = reject(event.player, event)

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onEntityPlace(event: EntityPlaceEvent) = event.player?.let { reject(it, event) } ?: Unit

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onDamage(event: EntityDamageByEntityEvent) {
        val player = when (val damager = event.damager) {
            is Player -> damager
            is Projectile -> damager.shooter as? Player
            else -> null
        } ?: return
        reject(player, event)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onShoot(event: EntityShootBowEvent) {
        val player = event.entity as? Player ?: return
        reject(player, event)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onBucketFill(event: PlayerBucketFillEvent) = reject(event.player, event)

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onBucketEmpty(event: PlayerBucketEmptyEvent) = reject(event.player, event)

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onDrop(event: PlayerDropItemEvent) = reject(event.player, event)

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onFish(event: PlayerFishEvent) = reject(event.player, event)

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onShear(event: PlayerShearEntityEvent) = reject(event.player, event)

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onLeash(event: PlayerLeashEntityEvent) = reject(event.player, event)

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onTakeLecternBook(event: PlayerTakeLecternBookEvent) = reject(event.player, event)

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onHangingPlace(event: HangingPlaceEvent) = event.player?.let { reject(it, event) } ?: Unit

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onHangingBreak(event: HangingBreakByEntityEvent) {
        val player = event.remover as? Player ?: return
        reject(player, event)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        if (event.view.topInventory.type in PERSONAL_INVENTORIES) return
        reject(player, event)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onInventoryDrag(event: InventoryDragEvent) {
        val player = event.whoClicked as? Player ?: return
        if (event.view.topInventory.type in PERSONAL_INVENTORIES) return
        reject(player, event)
    }

    private fun reject(player: Player, event: Cancellable) {
        if (!service.isEnabled(player)) return
        event.isCancelled = true
        service.reject(player)
    }

    companion object {
        private val PERSONAL_INVENTORIES = setOf(InventoryType.CRAFTING, InventoryType.PLAYER, InventoryType.CREATIVE)
    }
}
