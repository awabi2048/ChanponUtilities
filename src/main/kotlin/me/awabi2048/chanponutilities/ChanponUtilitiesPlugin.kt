package me.awabi2048.chanponutilities

import com.awabi2048.ccsystem.CCSystem
import me.awabi2048.chanponutilities.integration.MyWorldManagerIntegration
import me.awabi2048.chanponutilities.freecam.FreecamCommand
import me.awabi2048.chanponutilities.freecam.FreecamItemGrantProvider
import me.awabi2048.chanponutilities.freecam.FreecamListener
import me.awabi2048.chanponutilities.freecam.FreecamSessionService
import me.awabi2048.chanponutilities.freecam.GSitPoseCaptureService
import me.awabi2048.chanponutilities.freecam.StandingPoseCaptureService
import me.awabi2048.chanponutilities.listener.LecternBookshelfLinkListener
import me.awabi2048.chanponutilities.safety.TourSafetyCommand
import me.awabi2048.chanponutilities.safety.TourSafetyListener
import me.awabi2048.chanponutilities.safety.TourSafetyPreferenceStore
import me.awabi2048.chanponutilities.safety.TourSafetyService
import me.awabi2048.chanponutilities.service.LecternBookshelfLinkStore
import org.bukkit.plugin.java.JavaPlugin

class ChanponUtilitiesPlugin : JavaPlugin() {
    lateinit var linkStore: LecternBookshelfLinkStore
        private set
    private var myWorldManagerIntegration: AutoCloseable? = null
    private var freecamSessionService: FreecamSessionService? = null
    private var tourSafetyService: TourSafetyService? = null

    override fun onEnable() {
        CCSystem.getAPI().getConfigSchemaService().register(
            "chanpon-utilities",
            listOf(
                com.awabi2048.ccsystem.api.config.ManagedConfigSpec(
                    owner = "chanpon-utilities",
                    sourcePlugin = this,
                    resourcePath = "config.yml",
                    targetPath = dataFolder.resolve("config.yml").toPath(),
                    currentVersion = 4,
                    classification = com.awabi2048.ccsystem.api.config.ConfigClassification.MANAGED_CONFIG,
                    migrations = mapOf(1 to com.awabi2048.ccsystem.api.config.ConfigMigration { config ->
                        config.set("features.freecam.enabled", config.getBoolean("features.freecam.enabled", true))
                        config.set("features.freecam.activation.action.enabled", config.getBoolean("features.freecam.activation.action.enabled", true))
                        config.set("features.freecam.activation.action.window_ticks", 10)
                        config.set("features.freecam.activation.action.window_milliseconds", null)
                        config.set("features.freecam.activation.item.enabled", config.getBoolean("features.freecam.activation.item.enabled", true))
                        config.set("features.freecam.activation.item.material", config.getString("features.freecam.activation.item.material", "ENDER_EYE"))
                        if (!config.isList("features.freecam.restrictions.allowed_commands")) {
                            config.set("features.freecam.restrictions.allowed_commands", listOf("freecam", "fcam", "msg", "reply"))
                        }
                    },
                    2 to com.awabi2048.ccsystem.api.config.ConfigMigration { config ->
                        config.set("features.freecam.activation.action.window_ticks", 10)
                        config.set("features.freecam.activation.action.window_milliseconds", null)
                    },
                    3 to com.awabi2048.ccsystem.api.config.ConfigMigration { config ->
                        config.set("features.freecam.reset_distance_default", 128)
                    }),
                    validator = com.awabi2048.ccsystem.api.config.ConfigValidator { config ->
                        require(config.get("features.freecam.enabled") is Boolean) { "features.freecam.enabled must be boolean" }
                        require(config.get("features.freecam.activation.action.enabled") is Boolean) { "freecam action enabled must be boolean" }
                        require(config.getInt("features.freecam.activation.action.window_ticks") in 1..60) { "freecam action window_ticks must be 1..60" }
                        require(config.get("features.freecam.reset_distance_default") is Int &&
                            config.getInt("features.freecam.reset_distance_default") in 0..30_000_000
                        ) { "freecam reset_distance_default must be an integer between 0 and 30000000" }
                        require(config.get("features.freecam.activation.item.enabled") is Boolean) { "freecam item enabled must be boolean" }
                        require(org.bukkit.Material.matchMaterial(config.getString("features.freecam.activation.item.material").orEmpty()) != null) { "freecam item material is invalid" }
                        require(config.isList("features.freecam.restrictions.allowed_commands")) { "freecam allowed_commands must be list" }
                    },
                    reloadAction = { reloadConfig() }
                )
            )
        )
        check(CCSystem.getAPI().getConfigSchemaService().prepare("chanpon-utilities").successful) {
            "Chanpon-Utilities Config preparation failed"
        }
        reloadConfig()

        if (config.getBoolean("features.freecam.enabled", true)) {
            val poseCaptureService = if (server.pluginManager.isPluginEnabled("GSit")) {
                GSitPoseCaptureService()
            } else {
                StandingPoseCaptureService()
            }
            val freecam = FreecamSessionService(this, poseCaptureService)
            freecamSessionService = freecam
            server.pluginManager.registerEvents(FreecamListener(this, freecam), this)
            val executor = FreecamCommand(freecam)
            listOf("freecam", "fcam").forEach { commandName ->
                val command = requireNotNull(getCommand(commandName)) {
                    "$commandName command is missing from plugin.yml"
                }
                command.setExecutor(executor)
                command.tabCompleter = executor
            }
            CCSystem.getAPI().getItemGrantService().register(FreecamItemGrantProvider(this))
        }

        linkStore = LecternBookshelfLinkStore(this)
        linkStore.load()

        server.pluginManager.registerEvents(LecternBookshelfLinkListener(this, linkStore), this)

        val safetyService = TourSafetyService(this, TourSafetyPreferenceStore(this))
        tourSafetyService = safetyService
        server.pluginManager.registerEvents(TourSafetyListener(safetyService), this)
        requireNotNull(getCommand("anzen")) { "anzen command is missing from plugin.yml" }
            .setExecutor(TourSafetyCommand(safetyService))
        server.onlinePlayers.forEach(safetyService::synchronize)

        if (server.pluginManager.isPluginEnabled("MyWorldManager")) {
            // MWM型は専用クラスへ隔離し、任意依存がない環境でメインクラスの解決に失敗させない。
            myWorldManagerIntegration = MyWorldManagerIntegration.enable(this)
        } else {
            logger.info("MyWorldManager is unavailable; player-state integration is disabled.")
        }

        logger.info("Chanpon-Utilities has been enabled.")
    }

    override fun onDisable() {
        freecamSessionService?.shutdown()
        freecamSessionService = null
        tourSafetyService?.let { service -> server.onlinePlayers.forEach(service::clear) }
        tourSafetyService = null
        runCatching { CCSystem.getAPI().getItemGrantService().unregister("chanpon-utilities") }
        runCatching { CCSystem.getAPI().getConfigSchemaService().unregister("chanpon-utilities") }
        myWorldManagerIntegration?.close()
        myWorldManagerIntegration = null

        if (::linkStore.isInitialized) {
            linkStore.save()
        }
        logger.info("Chanpon-Utilities has been disabled.")
    }
}
