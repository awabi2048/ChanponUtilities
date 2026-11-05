package me.awabi2048.chanponutilities.tour

import io.papermc.paper.event.player.PlayerInsertLecternBookEvent
import me.awabi2048.chanponutilities.ChanponUtilitiesPlugin
import me.awabi2048.chanponutilities.api.TourEntityClickEvent
import org.bukkit.Material
import org.bukkit.block.BlockState
import org.bukkit.block.DoubleChest
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerTakeLecternBookEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder

/** 観光中のプレイヤー操作だけを制限し、ホッパー等の搬送には干渉しません。 */
internal class TourInteractionListener(private val plugin: ChanponUtilitiesPlugin) : Listener {
    private fun bypass(player: Player) = isTourAdmin(player)

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun open(event: InventoryOpenEvent) {
        val player = event.player as? Player ?: return
        if (!bypass(player) && isWorldStorage(event.inventory)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun click(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        if (!bypass(player) && isWorldStorage(event.view.topInventory)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun drag(event: InventoryDragEvent) {
        val player = event.whoClicked as? Player ?: return
        if (!bypass(player) && isWorldStorage(event.view.topInventory)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun interact(event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_BLOCK || bypass(event.player)) return
        val block = event.clickedBlock ?: return
        // 書見台は閲覧を残し、挿入・取り出しだけ専用イベントで拒否します。
        if (block.type == Material.LECTERN) return
        // 棚・模様入りの本棚・飾り壺・ジュークボックス等、GUIを開かない収納も含みます。
        if (block.state !is InventoryHolder) return
        event.setUseInteractedBlock(Event.Result.DENY)
        event.setUseItemInHand(Event.Result.DENY)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun insert(event: PlayerInsertLecternBookEvent) {
        if (!bypass(event.player)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun take(event: PlayerTakeLecternBookEvent) {
        if (!bypass(event.player)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun entity(event: PlayerInteractEntityEvent) {
        if (bypass(event.player)) return
        event.isCancelled = true
        plugin.server.pluginManager.callEvent(TourEntityClickEvent(event.player, event.rightClicked, event.hand))
    }
}

/** 独自GUIとプレイヤー自身のインベントリは、ワールド上の収納とは扱いません。 */
internal fun isWorldStorage(inventory: Inventory): Boolean {
    val holder = inventory.holder
    return holder is BlockState || holder is DoubleChest || (holder is Entity && holder !is Player)
}
