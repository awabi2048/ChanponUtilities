package me.awabi2048.chanponutilities.safety

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class TourSafetyCommand(
    private val service: TourSafetyService
) : CommandExecutor {
    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        val player = sender as? Player ?: return true
        service.toggle(player)
        return true
    }
}
