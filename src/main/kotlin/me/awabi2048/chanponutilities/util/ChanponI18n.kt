package me.awabi2048.chanponutilities.util

import com.awabi2048.ccsystem.CCSystem
import org.bukkit.entity.Player

object ChanponI18n {
    private const val SOURCE_ID = "chanpon_utilities"

    fun text(player: Player?, key: String, vararg placeholders: Pair<String, Any?>): String {
        return CCSystem.getAPI()
            .getI18nString(player, "$SOURCE_ID.$key", placeholdersMap(*placeholders))
            .replace('&', '§')
    }

    fun list(player: Player?, key: String, vararg placeholders: Pair<String, Any?>): List<String> {
        return CCSystem.getAPI()
            .getI18nStringList(player, "$SOURCE_ID.$key", placeholdersMap(*placeholders))
            .map { it.replace('&', '§') }
    }

    private fun placeholdersMap(vararg pairs: Pair<String, Any?>): Map<String, Any> {
        return pairs.filter { it.second != null }.associate { it.first to it.second!! }
    }
}
