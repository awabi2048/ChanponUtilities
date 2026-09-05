package me.awabi2048.chanponutilities.freecam

/**
 * Fキー押下1回ごとのFreecamショートカット介入方針です。
 * 実際のイベントやBukkit状態を参照せず、入力状態だけから介入の要否を決定するため単体で検証できます。
 */
internal enum class FreecamSwapDisposition {
    /** CC-System等がSWAP_HAND入力を占有しているため、キャンセルも二度押し記録もせず入力を譲ります。 */
    HAND_OVER,

    /** スニーク中は誤発動防止のため不介入とし、保持中の二度押し記録を破棄します。 */
    SNEAKING_RESET,

    /** 設定・プレイヤー設定でショートカットが使えない、またはFreecam実行中のため不介入です。 */
    IGNORE,

    /** 手に何か所持しているため不介入です。アイテム所持時のFはバニラの手入れ替えとして成立させます。 */
    NOT_EMPTY_HANDED,

    /** 二度押し判定の対象として、イベントキャンセルと押下記録を行います。 */
    TRACK_PRESS,
}

/**
 * FreecamショートカットのF二度押しは、両手が空のときだけ判定対象とします。
 * 片手でもアイテムを持っている状態で毎回キャンセルすると、盾やブロックを使うプレイヤーの
 * 通常の手入れ替え操作が常に失敗するため、その場合は一切介入しません。
 */
internal fun resolveFreecamSwapDisposition(
    inputClaimed: Boolean,
    sneaking: Boolean,
    shortcutAvailable: Boolean,
    mainHandEmpty: Boolean,
    offHandEmpty: Boolean,
): FreecamSwapDisposition = when {
    inputClaimed -> FreecamSwapDisposition.HAND_OVER
    sneaking -> FreecamSwapDisposition.SNEAKING_RESET
    !shortcutAvailable -> FreecamSwapDisposition.IGNORE
    !(mainHandEmpty && offHandEmpty) -> FreecamSwapDisposition.NOT_EMPTY_HANDED
    else -> FreecamSwapDisposition.TRACK_PRESS
}
