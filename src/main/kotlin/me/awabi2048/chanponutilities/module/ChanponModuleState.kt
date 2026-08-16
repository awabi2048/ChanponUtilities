package me.awabi2048.chanponutilities.module

/**
 * Chanpon-Utilities の有効／無効状態を表す不変値です。
 *
 * ここでは設定ファイルの書き換えやリスナー登録を行いません。状態を先に純粋な値として
 * 確定させ、後続のプラグインライフサイクルがその値を適用する構造にすることで、
 * enable/disable の途中で一部機能だけが切り替わる不整合を避けます。
 */
class ChanponModuleState private constructor(
    private val enabledByModule: Map<ChanponModule, Boolean>,
) {
    fun isEnabled(module: ChanponModule): Boolean = enabledByModule.getValue(module)

    fun setEnabled(module: ChanponModule, enabled: Boolean): ChanponModuleState =
        ChanponModuleState(enabledByModule + (module to enabled))

    fun enabledModules(): Set<ChanponModule> =
        enabledByModule.filterValues { it }.keys

    /** 設定保存・検証へ渡すための読み取り専用スナップショットです。 */
    fun asConfigValues(): Map<String, Boolean> =
        enabledByModule.mapKeys { (module, _) -> module.id }

    override fun equals(other: Any?): Boolean =
        other is ChanponModuleState && enabledByModule == other.enabledByModule

    override fun hashCode(): Int = enabledByModule.hashCode()

    override fun toString(): String = "ChanponModuleState(${asConfigValues()})"

    companion object {
        fun defaults(enabled: Boolean = true): ChanponModuleState =
            ChanponModuleState(ChanponModule.all.associateWith { enabled })

        /**
         * ID→状態の設定値から状態を構成します。
         * 未指定の機能は既定値で補完しますが、未知のIDは設定ミスとして拒否します。
         */
        fun fromConfig(
            values: Map<String, Boolean>,
            defaultEnabled: Boolean = true,
        ): ChanponModuleState {
            val unknownIds = values.keys.filter { ChanponModule.find(it) == null }
            require(unknownIds.isEmpty()) {
                "Unknown Chanpon-Utilities module IDs: ${unknownIds.sorted().joinToString(", ")}"
            }

            val enabledByModule = ChanponModule.all.associateWith { module ->
                values.entries.firstOrNull { it.key.equals(module.id, ignoreCase = true) }?.value
                    ?: defaultEnabled
            }
            return ChanponModuleState(enabledByModule)
        }
    }
}
