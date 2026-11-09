package me.awabi2048.chanponutilities.api

import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/** シミュレーションの設定変更後、オンラインプレイヤーを現在状態へ再同期します。 */
class TourSimulationChangedEvent : Event() {
    override fun getHandlers(): HandlerList = HANDLERS
    companion object {
        private val HANDLERS = HandlerList()
        @JvmStatic fun getHandlerList(): HandlerList = HANDLERS
    }
}
