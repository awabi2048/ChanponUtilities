package me.awabi2048.chanponutilities.guide

/** 仮想アレイの描画と経路の占有判定で共有するサイズです。 */
internal object GuideAllayDimensions {
    const val SCALE = 2.0
    // バニラのアレイの幅0.35・高さ0.6にスケールを適用します。
    const val HALF_WIDTH = 0.175 * SCALE
    const val HEIGHT = 0.6 * SCALE
}
