package me.awabi2048.chanponutilities.guide

/** 未配信の最終時刻より前に次の停止地点へ着かないための移動速度です。 */
internal object GuideDelayedMessageTiming {
    fun maximumStep(remainingDistance: Double, remainingTicks: Long, ordinaryStep: Double): Double {
        if (remainingTicks <= 0L) return ordinaryStep
        return minOf(ordinaryStep, remainingDistance.coerceAtLeast(0.0) / remainingTicks)
    }
}
