package me.awabi2048.chanponutilities.module

/**
 * Chanpon-Utilities が提供する独立した機能単位です。
 *
 * ID と設定パスを一か所に集約しておくことで、管理コマンド・設定移行・
 * プラグインライフサイクルがそれぞれ別の文字列を持つ状態を防ぎます。
 * Bukkit に依存しない定義にしているため、設定解釈や状態遷移を単体テストできます。
 */
enum class ChanponModule(
    val id: String,
    val enabledConfigPath: String,
) {
    FREECAM("freecam", "features.freecam.enabled"),
    LECTERN_BOOKSHELF_LINK("lectern_bookshelf_link", "features.lectern_bookshelf_link.enabled"),
    TOUR_SAFETY("tour_safety", "features.tour_safety.enabled"),
    PLAYER_STATE("player_state", "features.player_state.enabled"),
    ;

    companion object {
        val all: List<ChanponModule> = entries.toList()

        fun find(id: String): ChanponModule? =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) }
    }
}
