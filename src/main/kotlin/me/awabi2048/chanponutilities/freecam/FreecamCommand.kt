package me.awabi2048.chanponutilities.freecam

import me.awabi2048.chanponutilities.util.ChanponI18n
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class FreecamCommand(private val service: FreecamSessionService) : CommandExecutor, TabCompleter {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        val player = sender as? Player ?: return true
        when (args.firstOrNull()?.lowercase()) {
            null -> if (service.isActive(player.uniqueId)) service.stop(player.uniqueId, FreecamExitReason.MANUAL) else service.start(player)
            "start" -> if (!service.start(player)) player.sendMessage(ChanponI18n.text(player, "freecam.unavailable"))
            "stop" -> if (!service.stop(player.uniqueId, FreecamExitReason.MANUAL)) player.sendMessage(ChanponI18n.text(player, "freecam.not_active"))
            "status" -> player.sendMessage(ChanponI18n.text(player, if (service.isActive(player.uniqueId)) "freecam.status.active" else "freecam.status.inactive"))
            "toggle_shortcut" -> {
                val enabled = service.toggleShortcut(player.uniqueId)
                player.sendMessage(ChanponI18n.text(player, if (enabled) "freecam.shortcut.enabled" else "freecam.shortcut.disabled"))
            }
            else -> player.sendMessage(ChanponI18n.text(player, "freecam.usage"))
        }
        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> = when (args.size) {
        1 -> listOf("start", "stop", "status", "toggle_shortcut").filter { it.startsWith(args[0], true) }
        else -> emptyList()
    }
}
