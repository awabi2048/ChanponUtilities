package me.awabi2048.chanponutilities.guide

/** 状態の切替時は現在値を起点に、20 tickかけて半径を線形に変えます。 */
internal class GuideParticleRadius(initial: Double = 2.0) {
    private var current = initial
    private var start = initial
    private var target = initial
    private var elapsed = 20

    fun tick(next: Double): Double {
        if (next != target) {
            start = current
            target = next
            elapsed = 0
        }
        elapsed = (elapsed + 1).coerceAtMost(20)
        current = start + (target - start) * elapsed / 20.0
        return current
    }
}
