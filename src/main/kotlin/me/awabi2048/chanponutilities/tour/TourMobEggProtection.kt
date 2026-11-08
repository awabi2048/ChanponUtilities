package me.awabi2048.chanponutilities.tour

import org.bukkit.Material
import org.bukkit.entity.Mob
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityInteractEvent

/** 観光期間中はMobの踏みつけやゾンビの卵破壊からカメの卵を守ります。 */
internal class TourMobEggProtection : Listener {
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onInteract(event: EntityInteractEvent) {
        if (event.entity is Mob && event.block.type == Material.TURTLE_EGG) {
            event.isCancelled = true
        }
    }
}
