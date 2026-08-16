package me.awabi2048.chanponutilities.listener

/**
 * 書見台のスニーク読みを発動する入力条件です。
 * 実際のイベントやBukkit状態を参照しないため、reverseと空手条件を単体で検証できます。
 */
internal fun shouldUseLecternSneakRead(
    isSneaking: Boolean,
    handEmpty: Boolean,
    requireEmptyHand: Boolean,
    reverse: Boolean,
): Boolean =
    isSneaking != reverse && (!requireEmptyHand || handEmpty)
