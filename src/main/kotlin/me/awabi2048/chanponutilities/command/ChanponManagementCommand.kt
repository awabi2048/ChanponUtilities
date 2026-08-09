package me.awabi2048.chanponutilities.command

import me.awabi2048.chanponutilities.ChanponUtilitiesPlugin
import me.awabi2048.chanponutilities.module.ChanponModule
import me.awabi2048.chanponutilities.util.ChanponI18n
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class ChanponManagementCommand(
    private val plugin: ChanponUtilitiesPlugin,
) : CommandExecutor, TabCompleter {
    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>,
    ): Boolean {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(ChanponI18n.text(sender as? Player, "management.no_permission"))
            return true
        }

        when (args.firstOrNull()?.lowercase()) {
            "status" -> sendStatus(sender)
            "enable", "disable" -> changeModule(sender, args)
            else -> sender.sendMessage(ChanponI18n.text(sender as? Player, "management.usage"))
        }
        return true
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>,
    ): List<String> {
        if (!sender.hasPermission(PERMISSION)) return emptyList()
        return when (args.size) {
            1 -> listOf("status", "enable", "disable").filter { it.startsWith(args[0], ignoreCase = true) }
            2 -> if (args[0].equals("enable", true) || args[0].equals("disable", true)) {
                ChanponModule.all.map(ChanponModule::id).filter { it.startsWith(args[1], ignoreCase = true) }
            } else {
                emptyList()
            }
            else -> emptyList()
        }
    }

    private fun sendStatus(sender: CommandSender) {
        val player = sender as? Player
        sender.sendMessage(ChanponI18n.text(player, "management.status.header"))
        ChanponModule.all.forEach { module ->
            val stateKey = when {
                plugin.isModuleUnavailable(module) -> "management.state.unavailable"
                plugin.isModuleEnabled(module) -> "management.state.enabled"
                else -> "management.state.disabled"
            }
            val state = ChanponI18n.text(player, stateKey)
            sender.sendMessage(ChanponI18n.text(player, "management.status.entry", "id" to module.id, "state" to state))
        }
    }

    private fun changeModule(sender: CommandSender, args: Array<out String>) {
        val player = sender as? Player
        val moduleId = args.getOrNull(1)
        val module = moduleId?.let(ChanponModule::find)
        if (module == null) {
            sender.sendMessage(ChanponI18n.text(player, "management.unknown_module", "id" to (moduleId ?: "")))
            return
        }

        val enabled = args[0].equals("enable", true)
        if (plugin.isModuleEnabled(module) == enabled) {
            sender.sendMessage(
                ChanponI18n.text(
                    player,
                    "management.unchanged",
                    "id" to module.id,
                    "state" to ChanponI18n.text(player, if (enabled) "management.state.enabled" else "management.state.disabled"),
                )
            )
            return
        }

        runCatching { plugin.setModuleEnabled(module, enabled) }
            .onSuccess {
                sender.sendMessage(
                    ChanponI18n.text(
                        player,
                        "management.changed",
                        "id" to module.id,
                        "state" to ChanponI18n.text(player, if (enabled) "management.state.enabled" else "management.state.disabled"),
                    )
                )
            }
            .onFailure { failure ->
                plugin.logger.warning("/cu ${if (enabled) "enable" else "disable"} ${module.id} failed: ${failure.message}")
                sender.sendMessage(ChanponI18n.text(player, "management.failed", "id" to module.id))
            }
    }

    private companion object {
        const val PERMISSION = "chanponutilities.admin"
    }
}

class ChanponModuleUnavailableCommand(
    private val module: ChanponModule,
) : CommandExecutor, TabCompleter {
    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>,
    ): Boolean {
        sender.sendMessage(ChanponI18n.text(sender as? Player, "management.feature_disabled", "id" to module.id))
        return true
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>,
    ): List<String> = emptyList()
}
