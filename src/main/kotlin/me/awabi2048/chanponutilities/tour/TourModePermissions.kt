package me.awabi2048.chanponutilities.tour

import org.bukkit.entity.Player

/** マイワールドの既存ポリシーと同じ管理者を観光モードの対象外にします。 */
internal fun isTourAdmin(player: Player): Boolean =
    player.hasPermission("myworldmanager.admin") || player.hasPermission("mwmchanpon.admin") ||
        player.hasPermission("chanponutilities.admin")
