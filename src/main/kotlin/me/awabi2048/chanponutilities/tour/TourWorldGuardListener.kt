package me.awabi2048.chanponutilities.tour

import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldguard.WorldGuard
import com.sk89q.worldguard.bukkit.event.entity.UseEntityEvent
import com.sk89q.worldguard.protection.flags.Flags
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInteractEntityEvent

/** 提出ロックの interact 許可を、建築物扱いのエンティティにも適用します。 */
internal class TourWorldGuardListener : Listener {
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onUse(event: UseEntityEvent) {
        val original = event.originalEvent as? PlayerInteractEntityEvent ?: return
        if (original.isCancelled || isTourAdmin(original.player)) return
        val query = WorldGuard.getInstance().platform.regionContainer.createQuery()
        val regions = query.getApplicableRegions(BukkitAdapter.adapt(event.target))
        if (regions.isVirtual || regions.none { it.id == "chanpon_submitted_lock" }) return
        // WGの通常の優先順位・deny判定に任せ、他の保護やアイテム使用拒否を上書きしません。
        // 実際のエンティティ操作は後続の観光モードが取り消し、クリック通知だけを渡します。
        if (Flags.INTERACT !in event.relevantFlags) event.relevantFlags.add(Flags.INTERACT)
    }
}
