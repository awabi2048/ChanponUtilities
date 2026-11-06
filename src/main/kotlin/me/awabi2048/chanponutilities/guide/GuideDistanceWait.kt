package me.awabi2048.chanponutilities.guide

/** 距離の境界で状態が振動しないよう、待機開始と再開の両方を連続20 tickで確定します。 */
internal class GuideDistanceWait {
    var paused: Boolean = false
        private set
    private var transitionTicks = 0

    fun reset() {
        paused = false
        transitionTicks = 0
    }

    fun tick(eligible: Boolean, distanceSquared: Double): Boolean {
        if (!eligible) {
            reset()
            return false
        }
        val changeRequested = if (paused) distanceSquared < 100.0 else distanceSquared >= 144.0
        if (!changeRequested) {
            transitionTicks = 0
        } else if (++transitionTicks >= 20) {
            paused = !paused
            transitionTicks = 0
        }
        return paused
    }
}
