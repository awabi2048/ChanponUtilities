package me.awabi2048.chanponutilities

import com.awabi2048.ccsystem.CCSystem
import me.awabi2048.chanponutilities.listener.LecternBookshelfLinkListener
import me.awabi2048.chanponutilities.service.LecternBookshelfLinkStore
import org.bukkit.plugin.java.JavaPlugin

class ChanponUtilitiesPlugin : JavaPlugin() {
    lateinit var linkStore: LecternBookshelfLinkStore
        private set

    companion object {
        private const val I18N_SOURCE_ID = "chanpon_utilities"
    }

    override fun onEnable() {
        saveDefaultConfig()

        CCSystem.getAPI().registerI18nSource(I18N_SOURCE_ID, this, setOf("chanpon_utilities.yml"))

        linkStore = LecternBookshelfLinkStore(this)
        linkStore.load()

        server.pluginManager.registerEvents(LecternBookshelfLinkListener(this, linkStore), this)

        logger.info("Chanpon-Utilities has been enabled.")
    }

    override fun onDisable() {
        CCSystem.getAPI().unregisterI18nSource(I18N_SOURCE_ID)

        if (::linkStore.isInitialized) {
            linkStore.save()
        }
        logger.info("Chanpon-Utilities has been disabled.")
    }
}
