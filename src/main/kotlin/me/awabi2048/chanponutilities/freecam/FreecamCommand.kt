package me.awabi2048.chanponutilities.freecam

import com.awabi2048.ccsystem.api.localization.generated.ChanponUtilitiesKeys as Keys

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
            "start" -> if (!service.start(player)) player.sendMessage(ChanponI18n.text(player, Keys.CHANPON_UTILITIES_FREECAM_UNAVAILABLE))
            "stop" -> if (!service.stop(player.uniqueId, FreecamExitReason.MANUAL)) player.sendMessage(ChanponI18n.text(player, Keys.CHANPON_UTILITIES_FREECAM_NOT_ACTIVE))
            "status" -> player.sendMessage(ChanponI18n.text(player, if (service.isActive(player.uniqueId)) Keys.CHANPON_UTILITIES_FREECAM_STATUS_ACTIVE else Keys.CHANPON_UTILITIES_FREECAM_STATUS_INACTIVE))
            "toggle_shortcut" -> {
                val enabled = service.toggleShortcut(player.uniqueId)
                player.sendMessage(ChanponI18n.text(player, if (enabled) Keys.CHANPON_UTILITIES_FREECAM_SHORTCUT_ENABLED else Keys.CHANPON_UTILITIES_FREECAM_SHORTCUT_DISABLED))
            }
            "reset_distance" -> handleResetDistance(player, args.getOrNull(1))
            else -> player.sendMessage(ChanponI18n.text(player, Keys.CHANPON_UTILITIES_FREECAM_USAGE))
        }
        return true
    }

    private fun handleResetDistance(player: Player, argument: String?) {
        if (argument.equals("reset", ignoreCase = true)) {
            val distance = service.resetResetDistance(player.uniqueId)
            player.sendMessage(ChanponI18n.text(player, Keys.CHANPON_UTILITIES_FREECAM_RESET_DISTANCE_RESET, "distance" to distance))
            return
        }
        val distance = argument?.toIntOrNull()
        if (distance == null || distance !in 0..30_000_000) {
            player.sendMessage(ChanponI18n.text(player, Keys.CHANPON_UTILITIES_FREECAM_RESET_DISTANCE_USAGE))
            return
        }
        service.setResetDistance(player.uniqueId, distance)
        player.sendMessage(ChanponI18n.text(player, Keys.CHANPON_UTILITIES_FREECAM_RESET_DISTANCE_CHANGED, "distance" to distance))
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> = when (args.size) {
        1 -> listOf("start", "stop", "status", "toggle_shortcut", "reset_distance").filter { it.startsWith(args[0], true) }
        2 -> if (args[0].equals("reset_distance", true)) listOf("reset").filter { it.startsWith(args[1], true) } else emptyList()
        else -> emptyList()
    }
}
