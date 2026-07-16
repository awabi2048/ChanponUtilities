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
            val policy = ChanponPlayerStatePolicy()
            MyWorldManagerApi.registerWorldPlayerStatePolicy(policy)
            plugin.server.pluginManager.registerEvents(PlayerStatePolicyListener(plugin), plugin)
            return MyWorldManagerIntegration(policy)
        }
    }
}
