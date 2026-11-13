package me.awabi2048.chanponutilities.tour

import me.awabi2048.chanponutilities.ChanponUtilitiesPlugin
import me.awabi2048.chanponutilities.api.TourEntityClickEvent
import org.bukkit.Material
import org.bukkit.entity.Entity
import org.bukkit.entity.AbstractArrow
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.entity.Villager
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.event.hanging.HangingBreakByEntityEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.LecternInventory
import org.bukkit.inventory.MerchantInventory

/** 観光中のプレイヤー操作だけを制限し、ホッパー等の搬送には干渉しません。 */
internal class TourInteractionListener(private val plugin: ChanponUtilitiesPlugin) : Listener {
    private fun bypass(player: Player) = isTourAdmin(player) || !plugin.isTourActive(player.world)

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun open(event: InventoryOpenEvent) {
        val player = event.player as? Player ?: return
        // ブロック収納は許可し、収納付きエンティティの画面を保護します。
        if (!bypass(player) && event.inventory !is LecternInventory && isWorldStorage(event.inventory)) {
            event.isCancelled = true
        }
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
        // ドラゴンの卵は破壊イベントを経由せず、左右クリックのどちらでも移動します。
        if (!bypass(event.player) && event.clickedBlock?.type == Material.DRAGON_EGG &&
            event.action in setOf(Action.LEFT_CLICK_BLOCK, Action.RIGHT_CLICK_BLOCK)
        ) {
            event.setUseInteractedBlock(Event.Result.DENY)
            return
        }
        if (event.action == Action.PHYSICAL && !bypass(event.player)) {
            // 亀の卵の踏みつけと耕地の踏み荒らしは、建築権限や破壊イベントを経由しません。
            if (event.clickedBlock?.type in setOf(Material.TURTLE_EGG, Material.FARMLAND)) {
                event.setUseInteractedBlock(Event.Result.DENY)
            }
            return
        }
        // ブロックコンテナの内容物は観光中も操作可能とし、他の保護の判定に委ねます。
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun entity(event: PlayerInteractEntityEvent) {
        // 村人の取引操作は観光中も許可し、拒否時専用の通知も発行しません。
        if (bypass(event.player) || isTourTradeTarget(event.rightClicked)) return
        event.isCancelled = true
        plugin.server.pluginManager.callEvent(TourEntityClickEvent(event.player, event.rightClicked, event.hand))
    }

    /** 着地した矢とトライデントを回収し、飛行中の挙動や命中時の処理は維持します。 */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    fun projectileHit(event: ProjectileHitEvent) {
        val projectile = event.entity as? AbstractArrow ?: return
        if (event.hitBlock == null) return
        val player = projectile.shooter as? Player ?: return
        if (bypass(player)) return
        // 回収可能な投射物のみ返却し、無限の矢やマルチショットの複製を増やしません。
        val item = projectile.itemStack.takeIf { projectile.pickupStatus == AbstractArrow.PickupStatus.ALLOWED }
        // 命中イベントの処理が終わってから回収し、ブロック側の命中処理を妨げません。
        plugin.server.scheduler.runTask(plugin, Runnable {
            if (!projectile.isValid) return@Runnable
            val location = if (player.isOnline) player.location else projectile.location
            projectile.remove()
            if (item != null) {
                val remaining = if (player.isOnline) player.inventory.addItem(item).values else listOf(item)
                remaining.forEach { location.world.dropItemNaturally(location, it) }
            }
        })
    }

    /**
     * 観光期間の閲覧専用ポリシーとして、プレイヤー発のエンティティダメージを全面拒否します。
     * WGの damage-animals / pvp / entity-item-frame-destroy / entity-painting-destroy /
     * destroy-vehicle 各フラグの deny 相当です。
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun damage(event: EntityDamageByEntityEvent) {
        // 直接攻撃に加え、弓・三叉槍・卵・雪玉など発射者を追跡できる投射物も拒否します。
        // TNT・Creeper等の爆発は発射者経路に載らないため、提出保護リージョンの爆発フラグで防ぎます。
        val player = event.damager as? Player
            ?: (event.damager as? Projectile)?.shooter as? Player
            ?: return
        if (bypass(player)) return
        event.isCancelled = true
    }

    /**
     * 額縁・絵画などの吊り下げエンティティはダメージではなく専用の破壊イベントで消えるため、
     * プレイヤーが関与する破壊を別途拒否します。
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun hangingBreak(event: HangingBreakByEntityEvent) {
        // 近接攻撃だけでなく、矢などで射抜いた場合も発射者をたどって拒否します。
        val player = event.remover as? Player
            ?: (event.remover as? Projectile)?.shooter as? Player
            ?: return
        if (bypass(player)) return
        event.isCancelled = true
    }
}

/** ブロック収納と独自GUIを許可し、収納付きエンティティの保護を維持します。 */
internal fun isWorldStorage(inventory: Inventory): Boolean {
    val holder = inventory.holder
    if (inventory is MerchantInventory && holder is Villager) return false
    return holder is Entity && holder !is Player
}

/** 村人との取引操作だけを観光モードのエンティティ保護から除外します。 */
internal fun isTourTradeTarget(entity: Entity): Boolean = entity is Villager
