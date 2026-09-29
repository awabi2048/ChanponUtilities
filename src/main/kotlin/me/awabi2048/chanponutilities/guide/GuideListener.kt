package me.awabi2048.chanponutilities.guide

import com.awabi2048.ccsystem.api.localization.generated.ChanponUtilitiesKeys as Keys
import me.awabi2048.chanponutilities.util.ChanponI18n
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerQuitEvent

/**
 * ガイドセッションのライフサイクル境界です。
 *
 * 進捗は永続化しない仕様のため、ログアウト・ガイド対象ワールドからの退出で
 * セッションを破棄します。同一ワールド内のテレポートやデスは進行を維持します。
 */
class GuideListener(
    private val sessionService: GuideSessionService,
) : Listener {

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        sessionService.onQuit(event.player)
    }

    @EventHandler
    fun onWorldChange(event: PlayerChangedWorldEvent) {
        if (sessionService.onWorldLeft(event.player)) {
            event.player.sendMessage(ChanponI18n.text(event.player, Keys.CHANPON_UTILITIES_GUIDE_WORLD_LEFT))
        }
    }
}
