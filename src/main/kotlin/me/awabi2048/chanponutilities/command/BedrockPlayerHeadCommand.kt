package me.awabi2048.chanponutilities.command

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.localization.generated.ChanponUtilitiesKeys as Keys
import com.destroystokyo.paper.profile.ProfileProperty
import me.awabi2048.chanponutilities.ChanponUtilitiesPlugin
import me.awabi2048.chanponutilities.util.ChanponI18n
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.logging.Level

internal class BedrockPlayerHeadCommand(private val plugin: ChanponUtilitiesPlugin) : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        val player = sender as? Player ?: return true
        if (args.size != 1 || args[0].isBlank()) {
            player.sendMessage(ChanponI18n.text(player, Keys.CHANPON_UTILITIES_BEPLAYERHEAD_USAGE))
            return true
        }
        val requestName = args[0]
        player.sendMessage(ChanponI18n.text(player, Keys.CHANPON_UTILITIES_BEPLAYERHEAD_LOADING))
        CCSystem.getAPI().getBedrockSkinService().fetch(requestName).whenComplete { skin, failure ->
            // 外部APIの完了スレッドからBukkitのアイテム・プレイヤー状態へ触れません。
            Bukkit.getScheduler().runTask(plugin, Runnable {
                if (!player.isOnline) return@Runnable
                if (failure != null) {
                    plugin.logger.log(Level.WARNING, "Bedrock skin lookup failed for $requestName", failure)
                    player.sendMessage(ChanponI18n.text(player, Keys.CHANPON_UTILITIES_BEPLAYERHEAD_FAILED))
                } else if (skin == null) {
                    player.sendMessage(ChanponI18n.text(player, Keys.CHANPON_UTILITIES_BEPLAYERHEAD_NOT_FOUND))
                } else {
                    val head = ItemStack(Material.PLAYER_HEAD)
                    val meta = head.itemMeta as SkullMeta
                    val uuid = UUID.nameUUIDFromBytes("bedrock:${skin.xuid}".toByteArray(StandardCharsets.UTF_8))
                    val profile = Bukkit.createProfile(uuid, skin.gamertag)
                    profile.setProperty(ProfileProperty("textures", skin.value, skin.signature))
                    meta.playerProfile = profile
                    head.itemMeta = meta
                    val excess = player.inventory.addItem(head)
                    if (excess.isNotEmpty()) {
                        player.sendMessage(ChanponI18n.text(player, Keys.CHANPON_UTILITIES_BEPLAYERHEAD_INVENTORY_FULL))
                    } else {
                        player.sendMessage(ChanponI18n.text(player, Keys.CHANPON_UTILITIES_BEPLAYERHEAD_SUCCESS, "name" to skin.gamertag))
                    }
                }
            })
        }
        return true
    }
}
