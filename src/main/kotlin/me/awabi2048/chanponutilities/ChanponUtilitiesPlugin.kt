package me.awabi2048.chanponutilities

import com.awabi2048.ccsystem.CCSystem
import me.awabi2048.chanponutilities.integration.MyWorldManagerIntegration
import me.awabi2048.chanponutilities.listener.LecternBookshelfLinkListener
import me.awabi2048.chanponutilities.service.LecternBookshelfLinkStore
import org.bukkit.plugin.java.JavaPlugin

class ChanponUtilitiesPlugin : JavaPlugin() {
    lateinit var linkStore: LecternBookshelfLinkStore
        private set
    private var myWorldManagerIntegration: AutoCloseable? = null

    companion object {
        private const val I18N_SOURCE_ID = "chanpon_utilities"
    }

    override fun onEnable() {
        saveDefaultConfig()

        CCSystem.getAPI().registerI18nSource(I18N_SOURCE_ID, this, setOf("chanpon_utilities.yml"))

        linkStore = LecternBookshelfLinkStore(this)
        linkStore.load()

        server.pluginManager.registerEvents(LecternBookshelfLinkListener(this, linkStore), this)

        if (server.pluginManager.isPluginEnabled("MyWorldManager")) {
            // MWM型は専用クラスへ隔離し、任意依存がない環境でメインクラスの解決に失敗させない。
            myWorldManagerIntegration = MyWorldManagerIntegration.enable(this)
        } else {
            logger.info("MyWorldManager is unavailable; player-state integration is disabled.")
        }

        logger.info("Chanpon-Utilities has been enabled.")
    }

    override fun onDisable() {
        CCSystem.getAPI().unregisterI18nSource(I18N_SOURCE_ID)

        myWorldManagerIntegration?.close()
        myWorldManagerIntegration = null

        if (::linkStore.isInitialized) {
            linkStore.save()
        }
        logger.info("Chanpon-Utilities has been disabled.")
    }
}
