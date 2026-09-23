package me.awabi2048.chanponutilities

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.config.ConfigClassification
import com.awabi2048.ccsystem.api.config.ConfigMigration
import com.awabi2048.ccsystem.api.config.ConfigValidator
import com.awabi2048.ccsystem.api.config.ManagedConfigSpec
import me.awabi2048.chanponutilities.command.ChanponManagementCommand
import me.awabi2048.chanponutilities.command.BedrockPlayerHeadCommand
import me.awabi2048.chanponutilities.command.ChanponModuleUnavailableCommand
import me.awabi2048.chanponutilities.freecam.FreecamCommand
import me.awabi2048.chanponutilities.freecam.CCSystemSwapHandInputClaimGate
import me.awabi2048.chanponutilities.freecam.FreecamItemGrantProvider
import me.awabi2048.chanponutilities.freecam.FreecamListener
import me.awabi2048.chanponutilities.freecam.FreecamSessionService
import me.awabi2048.chanponutilities.freecam.GSitPoseCaptureService
import me.awabi2048.chanponutilities.freecam.StandingPoseCaptureService
import me.awabi2048.chanponutilities.integration.MyWorldManagerIntegration
import me.awabi2048.chanponutilities.listener.LecternBookshelfLinkListener
import me.awabi2048.chanponutilities.module.ChanponModule
import me.awabi2048.chanponutilities.module.ChanponModuleState
import me.awabi2048.chanponutilities.safety.CommandSafeguardListener
import me.awabi2048.chanponutilities.safety.CommandSafeguardService
import me.awabi2048.chanponutilities.safety.TourSafetyCommand
import me.awabi2048.chanponutilities.safety.TourSafetyListener
import me.awabi2048.chanponutilities.safety.TourSafetyPreferenceStore
import me.awabi2048.chanponutilities.safety.TourSafetyService
import me.awabi2048.chanponutilities.service.LecternBookshelfLinkStore
import org.bukkit.Bukkit
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.event.HandlerList
import org.bukkit.plugin.java.JavaPlugin
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.logging.Level

class ChanponUtilitiesPlugin : JavaPlugin() {
    private var linkStore: LecternBookshelfLinkStore? = null
    private var myWorldManagerIntegration: AutoCloseable? = null
    private var freecamSessionService: FreecamSessionService? = null
    private var tourSafetyService: TourSafetyService? = null
    private var commandSafeguardService: CommandSafeguardService? = null
    private var moduleState: ChanponModuleState = ChanponModuleState.defaults()

    override fun onEnable() {
        registerManagedConfig()
        check(CCSystem.getAPI().getConfigSchemaService().prepare(CONFIG_OWNER).successful) {
            "Chanpon-Utilities Config preparation failed"
        }
        requireNotNull(getCommand("cu")) { "cu command is missing from plugin.yml" }.apply {
            val executor = ChanponManagementCommand(this@ChanponUtilitiesPlugin)
            setExecutor(executor)
            tabCompleter = executor
        }
        installCommand("beplayerhead", BedrockPlayerHeadCommand(this))

        startPlugin()
        logger.info("Chanpon-Utilities has been enabled.")
    }

    override fun onDisable() {
        stopPlugin()
        runCatching { CCSystem.getAPI().getConfigSchemaService().unregister(CONFIG_OWNER) }
        logger.info("Chanpon-Utilities has been disabled.")
    }

    internal fun isModuleEnabled(module: ChanponModule): Boolean = moduleState.isEnabled(module)

    internal fun currentModuleState(): ChanponModuleState = moduleState

    internal fun isModuleUnavailable(module: ChanponModule): Boolean =
        module == ChanponModule.PLAYER_STATE && !server.pluginManager.isPluginEnabled("MyWorldManager")

    /**
     * モジュール設定はサーバー全体で共有し、変更後はイベント登録とタスクを再構築します。
     * 途中で起動に失敗した場合は、旧設定を保存して旧状態へ戻します。
     */
    @Synchronized
    internal fun setModuleEnabled(module: ChanponModule, enabled: Boolean) {
        check(Bukkit.isPrimaryThread()) { "Chanpon-Utilities module changes must run on the Bukkit main thread" }
        check(lifecycleActive) { "Chanpon-Utilities runtime is not active" }
        val previous = moduleState
        if (previous.isEnabled(module) == enabled) return

        val next = previous.setEnabled(module, enabled)
        val previousConfig = readConfigBytes()
        try {
            writeModuleStateAtomically(next)
            restartPluginLifecycle("module ${module.id}=$enabled")
        } catch (failure: Throwable) {
            logger.log(Level.SEVERE, "Chanpon-Utilities module change failed; rolling back ${module.id}", failure)
            runCatching { stopPlugin() }.onFailure(failure::addSuppressed)
            try {
                restoreConfigBytes(previousConfig)
                startPlugin()
            } catch (rollbackFailure: Throwable) {
                failure.addSuppressed(rollbackFailure)
                throw IllegalStateException("Chanpon-Utilities module rollback failed", failure)
            }
            throw failure
        }
    }

    private fun registerManagedConfig() {
        CCSystem.getAPI().getConfigSchemaService().register(
            CONFIG_OWNER,
            listOf(
                ManagedConfigSpec(
                    owner = CONFIG_OWNER,
                    sourcePlugin = this,
                    resourcePath = "config.yml",
                    targetPath = dataFolder.resolve("config.yml").toPath(),
                    currentVersion = CONFIG_VERSION,
                    classification = ConfigClassification.MANAGED_CONFIG,
                    migrations = mapOf(
                        1 to ConfigMigration { config ->
                            config.set("features.freecam.enabled", config.getBoolean("features.freecam.enabled", true))
                            config.set("features.freecam.activation.action.enabled", config.getBoolean("features.freecam.activation.action.enabled", true))
                            config.set("features.freecam.activation.action.window_ticks", 10)
                            config.set("features.freecam.activation.action.window_milliseconds", null)
                            config.set("features.freecam.activation.item.enabled", config.getBoolean("features.freecam.activation.item.enabled", true))
                            config.set("features.freecam.activation.item.material", config.getString("features.freecam.activation.item.material", "ENDER_EYE"))
                            if (!config.isList("features.freecam.restrictions.allowed_commands")) {
                                config.set("features.freecam.restrictions.allowed_commands", listOf("freecam", "msg", "reply"))
                            }
                        },
                        2 to ConfigMigration { config ->
                            config.set("features.freecam.activation.action.window_ticks", 10)
                            config.set("features.freecam.activation.action.window_milliseconds", null)
                        },
                        3 to ConfigMigration { config ->
                            config.set("features.freecam.reset_distance_default", 128)
                        },
                        4 to ConfigMigration { config ->
                            config.set(
                                "features.freecam.restrictions.allowed_commands",
                                config.getStringList("features.freecam.restrictions.allowed_commands")
                                    .filterNot { it.equals("fcam", ignoreCase = true) },
                            )
                        },
                        5 to ConfigMigration { config ->
                            if (!config.contains("features.tour_safety.enabled")) {
                                config.set("features.tour_safety.enabled", true)
                            }
                            if (!config.contains("features.player_state.enabled")) {
                                config.set("features.player_state.enabled", true)
                            }
                            if (!config.contains("features.lectern_bookshelf_link.sneak_read.require_empty_hand")) {
                                config.set("features.lectern_bookshelf_link.sneak_read.require_empty_hand", true)
                            }
                            if (!config.contains("features.lectern_bookshelf_link.sneak_read.reverse")) {
                                config.set("features.lectern_bookshelf_link.sneak_read.reverse", false)
                            }
                        },
                        6 to ConfigMigration { config ->
                            if (!config.contains(COMMAND_SAFEGUARD_BLACKLIST_PATH)) {
                                config.set(
                                    COMMAND_SAFEGUARD_BLACKLIST_PATH,
                                    DEFAULT_COMMAND_SAFEGUARD_BLACKLIST,
                                )
                            }
                        },
                        7 to ConfigMigration { config ->
                            if (!config.contains(ChanponModule.COMMAND_SAFEGUARD.enabledConfigPath)) {
                                config.set(ChanponModule.COMMAND_SAFEGUARD.enabledConfigPath, true)
                            }
                        },
                    ),
                    validator = ConfigValidator(::validateConfigValues),
                    reloadAction = { restartPluginLifecycle("config reload") },
                ),
            ),
        )
    }

    private fun startPlugin() {
        reloadConfig()
        moduleState = loadModuleState()

        // 有効化中は権限設定に左右されない安全境界として動作しますが、登録状態は
        // ChanponModule に統一します。これにより /cu status と enable/disable の表示・
        // 設定保存・ライフサイクル再構築が同じ状態を参照できます。
        if (isModuleEnabled(ChanponModule.COMMAND_SAFEGUARD)) {
            val commandSafeguard = CommandSafeguardService(this)
            commandSafeguardService = commandSafeguard
            server.pluginManager.registerEvents(CommandSafeguardListener(commandSafeguard), this)
        }

        if (isModuleEnabled(ChanponModule.FREECAM)) {
            val poseCaptureService = if (server.pluginManager.isPluginEnabled("GSit")) {
                GSitPoseCaptureService()
            } else {
                StandingPoseCaptureService()
            }
            val freecam = FreecamSessionService(this, poseCaptureService)
            freecamSessionService = freecam
            val interactionClaims = CCSystem.getAPI().getPlayerInteractionClaimService()
            server.pluginManager.registerEvents(
                FreecamListener(
                    this,
                    freecam,
                    // Gesture GUIなどがFキーを占有している間、Freecamは入力へ一切介入しません。
                    CCSystemSwapHandInputClaimGate(interactionClaims),
                ),
                this,
            )
            installCommand("freecam", FreecamCommand(freecam))
            CCSystem.getAPI().getItemGrantService().register(FreecamItemGrantProvider(this))
        } else {
            installCommand("freecam", ChanponModuleUnavailableCommand(ChanponModule.FREECAM))
        }

        if (isModuleEnabled(ChanponModule.LECTERN_BOOKSHELF_LINK)) {
            val store = LecternBookshelfLinkStore(this)
            store.load()
            linkStore = store
            server.pluginManager.registerEvents(LecternBookshelfLinkListener(this, store), this)
        }

        if (isModuleEnabled(ChanponModule.TOUR_SAFETY)) {
            val safetyService = TourSafetyService(this, TourSafetyPreferenceStore(this))
            tourSafetyService = safetyService
            server.pluginManager.registerEvents(TourSafetyListener(this, safetyService), this)
            installCommand("anzen", TourSafetyCommand(safetyService))
            server.onlinePlayers.forEach(safetyService::synchronize)
        } else {
            installCommand("anzen", ChanponModuleUnavailableCommand(ChanponModule.TOUR_SAFETY))
        }

        if (isModuleEnabled(ChanponModule.PLAYER_STATE) && server.pluginManager.isPluginEnabled("MyWorldManager")) {
            myWorldManagerIntegration = MyWorldManagerIntegration.enable(this)
        } else if (!server.pluginManager.isPluginEnabled("MyWorldManager")) {
            logger.info("MyWorldManager is unavailable; player-state integration is disabled.")
        }
        lifecycleActive = true
    }

    private fun stopPlugin() {
        lifecycleActive = false
        HandlerList.unregisterAll(this)
        server.scheduler.cancelTasks(this)
        commandSafeguardService?.shutdown()
        commandSafeguardService = null
        freecamSessionService?.shutdown()
        freecamSessionService = null
        tourSafetyService?.let { service -> server.onlinePlayers.forEach(service::clear) }
        tourSafetyService = null
        runCatching { CCSystem.getAPI().getItemGrantService().unregister("chanpon-utilities") }
        myWorldManagerIntegration?.let { integration -> runCatching { integration.close() } }
        myWorldManagerIntegration = null
        linkStore?.let { store -> runCatching(store::save).onFailure { failure -> logger.log(Level.WARNING, "Failed to save lectern links", failure) } }
        linkStore = null

        if (getCommand("freecam") != null) {
            installCommand("freecam", ChanponModuleUnavailableCommand(ChanponModule.FREECAM))
        }
        if (getCommand("anzen") != null) {
            installCommand("anzen", ChanponModuleUnavailableCommand(ChanponModule.TOUR_SAFETY))
        }
    }

    private fun restartPluginLifecycle(reason: String) {
        check(!lifecycleTransition) { "Chanpon-Utilities lifecycle restart is already in progress" }
        lifecycleTransition = true
        try {
            stopPlugin()
            try {
                startPlugin()
                logger.info("Chanpon-Utilities $reason lifecycle restarted.")
            } catch (failure: Throwable) {
                runCatching { stopPlugin() }.onFailure(failure::addSuppressed)
                throw failure
            }
        } finally {
            lifecycleTransition = false
        }
    }

    private fun loadModuleState(): ChanponModuleState = ChanponModuleState.fromConfig(
        ChanponModule.all.associate { module ->
            val raw = config.get(module.enabledConfigPath)
            require(raw is Boolean) { "${module.enabledConfigPath} must be boolean" }
            module.id to raw
        },
    )

    private fun validateConfigValues(config: YamlConfiguration) {
        ChanponModule.all.forEach { module ->
            require(config.get(module.enabledConfigPath) is Boolean) { "${module.enabledConfigPath} must be boolean" }
        }
        require(config.get("features.lectern_bookshelf_link.sneak_read.require_empty_hand") is Boolean) {
            "lectern sneak_read require_empty_hand must be boolean"
        }
        require(config.get("features.lectern_bookshelf_link.sneak_read.reverse") is Boolean) {
            "lectern sneak_read reverse must be boolean"
        }
        require(config.get("features.freecam.activation.action.enabled") is Boolean) { "freecam action enabled must be boolean" }
        require(config.getInt("features.freecam.activation.action.window_ticks") in 1..60) { "freecam action window_ticks must be 1..60" }
        require(config.get("features.freecam.reset_distance_default") is Int) {
            "freecam reset_distance_default must be an integer"
        }
        require(config.getInt("features.freecam.reset_distance_default") in 0..30_000_000) {
            "freecam reset_distance_default must be an integer between 0 and 30000000"
        }
        require(config.get("features.freecam.activation.item.enabled") is Boolean) { "freecam item enabled must be boolean" }
        require(org.bukkit.Material.matchMaterial(config.getString("features.freecam.activation.item.material").orEmpty()) != null) {
            "freecam item material is invalid"
        }
        require(config.isList("features.freecam.restrictions.allowed_commands")) { "freecam allowed_commands must be list" }
        val rawBlacklist = config.get(COMMAND_SAFEGUARD_BLACKLIST_PATH)
        require(rawBlacklist is List<*>) {
            "$COMMAND_SAFEGUARD_BLACKLIST_PATH must be list"
        }
        val normalizedBlacklist = rawBlacklist.mapIndexed { index, value ->
            require(value is String) {
                "$COMMAND_SAFEGUARD_BLACKLIST_PATH[$index] must be string"
            }
            val command = value.trim()
            require(command.startsWith('/')) {
                "$COMMAND_SAFEGUARD_BLACKLIST_PATH[$index] must start with /"
            }
            require(command.isNotEmpty()) {
                "$COMMAND_SAFEGUARD_BLACKLIST_PATH[$index] must not be blank"
            }
            command
        }
        require(normalizedBlacklist.distinct().size == normalizedBlacklist.size) {
            "$COMMAND_SAFEGUARD_BLACKLIST_PATH must not contain duplicates"
        }
    }

    private fun configPath(): Path = dataFolder.resolve("config.yml").toPath()

    private fun readConfigBytes(): ByteArray = Files.readAllBytes(configPath())

    private fun writeModuleStateAtomically(state: ChanponModuleState) {
        val path = configPath()
        val yaml = YamlConfiguration()
        yaml.load(path.toFile())
        ChanponModule.all.forEach { module ->
            yaml.set(module.enabledConfigPath, state.isEnabled(module))
        }
        validateConfigValues(yaml)
        atomicSaveYaml(yaml, path)
    }

    private fun restoreConfigBytes(bytes: ByteArray) {
        val path = configPath()
        val temporary = path.resolveSibling("${path.fileName}.rollback.tmp")
        Files.write(temporary, bytes)
        val verified = YamlConfiguration()
        verified.load(temporary.toFile())
        runCatching {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        }.getOrElse {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun atomicSaveYaml(yaml: YamlConfiguration, path: Path) {
        val temporary = path.resolveSibling("${path.fileName}.cu.tmp")
        yaml.save(temporary.toFile())
        val verified = YamlConfiguration()
        verified.load(temporary.toFile())
        runCatching {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        }.getOrElse {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private var lifecycleActive = false
    private var lifecycleTransition = false

    private fun installCommand(name: String, executor: org.bukkit.command.CommandExecutor) {
        val command = requireNotNull(getCommand(name)) { "$name command is missing from plugin.yml" }
        command.setExecutor(executor)
        command.tabCompleter = executor as? org.bukkit.command.TabCompleter
    }

    private companion object {
        const val CONFIG_OWNER = "chanpon-utilities"
        const val CONFIG_VERSION = 8
        const val COMMAND_SAFEGUARD_BLACKLIST_PATH = "command_safeguard.blacklist"
        val DEFAULT_COMMAND_SAFEGUARD_BLACKLIST = listOf("/kill @e[]", "/kill @e")
    }
}
