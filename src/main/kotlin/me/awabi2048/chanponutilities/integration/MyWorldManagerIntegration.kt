package me.awabi2048.chanponutilities.integration

import me.awabi2048.chanponutilities.ChanponUtilitiesPlugin
import me.awabi2048.chanponutilities.listener.PlayerStatePolicyListener
import me.awabi2048.chanponutilities.policy.ChanponPlayerStatePolicy
import me.awabi2048.myworldmanager.api.MyWorldManagerApi

/** MWMが実際に有効な場合だけロードする任意連携境界。 */
class MyWorldManagerIntegration private constructor(
    private val policy: ChanponPlayerStatePolicy
) : AutoCloseable {
    override fun close() {
        MyWorldManagerApi.unregisterWorldPlayerStatePolicy(policy)
    }

    companion object {
        fun enable(plugin: ChanponUtilitiesPlugin): MyWorldManagerIntegration {
            val policy = ChanponPlayerStatePolicy { _, worldData ->
                val key = org.bukkit.NamespacedKey.fromString(worldData.worldKey)
                val world = key?.let(org.bukkit.Bukkit::getWorld)
                if (world == null) plugin.isModuleEnabled(me.awabi2048.chanponutilities.module.ChanponModule.TOUR_MODE)
                else plugin.isTourActive(world)
            }
            return try {
                MyWorldManagerApi.registerWorldPlayerStatePolicy(policy)
                plugin.server.pluginManager.registerEvents(PlayerStatePolicyListener(plugin), plugin)
                MyWorldManagerIntegration(policy)
            } catch (failure: Throwable) {
                // API登録後のイベント登録失敗でも、外部にポリシーだけを残さない。
                runCatching { MyWorldManagerApi.unregisterWorldPlayerStatePolicy(policy) }
                    .onFailure(failure::addSuppressed)
                throw failure
            }
        }
    }
}
