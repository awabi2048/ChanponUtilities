package me.awabi2048.chanponutilities.safety

/**
 * コマンド受付時点で解決したセレクターごとの対象数です。
 *
 * 実際にコマンドが変更する件数ではなく、Vanillaセレクターが現在一致する件数を
 * 表します。コマンドごとの実行結果を先に発生させず、確認画面へ表示するための
 * スナップショットとして扱います。
 */
internal data class CommandSelectorTargetCount(
    val selector: String,
    val count: Int,
)

internal data class CommandSafeguardSnapshot(
    val totalLoadedEntities: Long,
    val selectorTargets: List<CommandSelectorTargetCount>,
) {
    val maxSelectorTargets: Int
        get() = selectorTargets.maxOfOrNull(CommandSelectorTargetCount::count) ?: 0

    val targetPercentage: Double
        get() = if (totalLoadedEntities <= 0L) {
            0.0
        } else {
            maxSelectorTargets.toDouble() * 100.0 / totalLoadedEntities.toDouble()
        }

    val shouldActivate: Boolean
        get() = CommandSafeguardPolicy.shouldActivate(
            totalLoadedEntities,
            selectorTargets.map(CommandSelectorTargetCount::count),
        )
}

/**
 * コマンドセーフガードの発動条件を純粋な判定として集約します。
 *
 * 「10%以上」だけでは、エンティティが少ないワールドで1体の操作まで確認対象に
 * なります。また比率だけでは、全体数が非常に多い環境で数百体への操作がすり抜ける
 * ため、最大対象数の絶対下限を必ず併用します。
 */
internal object CommandSafeguardPolicy {
    const val MIN_SELECTOR_TARGETS: Long = 50L
    fun shouldActivate(totalLoadedEntities: Long, selectorCounts: Iterable<Int>): Boolean {
        if (totalLoadedEntities <= 0L) return false
        val maxSelectorTargets = selectorCounts
            .map(Int::toLong)
            .maxOrNull()
            ?: return false

        // 小数比較や整数除算による境界誤差を避け、10%を整数の積で判定します。
        return maxSelectorTargets >= MIN_SELECTOR_TARGETS &&
            maxSelectorTargets * 10L >= totalLoadedEntities
    }
}
