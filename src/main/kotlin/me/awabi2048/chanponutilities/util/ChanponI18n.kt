package me.awabi2048.chanponutilities.util

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.localization.LocalizationKey
import org.bukkit.entity.Player

object ChanponI18n {
    fun text(player: Player?, key: LocalizationKey<String>, vararg placeholders: Pair<String, Any?>): String {
        return CCSystem.getAPI()
            .getLocalized(player, key, placeholdersMap(*placeholders))
            .replace('&', '§')
    }

    fun list(player: Player?, key: LocalizationKey<List<String>>, vararg placeholders: Pair<String, Any?>): List<String> {
        return CCSystem.getAPI()
            .getLocalized(player, key, placeholdersMap(*placeholders))
            .map { it.replace('&', '§') }
    }

    private fun placeholdersMap(vararg pairs: Pair<String, Any?>): Map<String, Any> {
        return pairs.filter { it.second != null }.associate { it.first to it.second!! }
    }
}
