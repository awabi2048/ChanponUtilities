package me.awabi2048.chanponutilities.api

import org.bukkit.World

/** 個別ワールドの観光シミュレーション状態を外部プラグインから取得します。 */
fun interface TourSimulationProvider {
    fun isSimulated(world: World): Boolean
}
