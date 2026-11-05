package me.awabi2048.chanponutilities.api

import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import org.bukkit.inventory.EquipmentSlot

/** 観光モードで通常操作を拒否したクリックを、他プラグインへ通知します。 */
class TourEntityClickEvent(val player: Player, val target: Entity, val hand: EquipmentSlot) : Event() {
    override fun getHandlers(): HandlerList = handlers
    companion object {
        private val handlers = HandlerList()
        @JvmStatic fun getHandlerList(): HandlerList = handlers
    }
}
