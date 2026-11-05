package me.awabi2048.chanponutilities.guide

/** 出現・消滅の10 tick、1ブロック、1回転を共通の進行率で計算します。 */
internal object GuideLifecycleAnimation {
    const val DURATION_TICKS = 10
    data class Frame(val y: Double, val yaw: Float, val scale: Double)

    fun appearing(targetY: Double, yaw: Float, tick: Int): Frame {
        val t = tick.coerceIn(0, DURATION_TICKS).toDouble() / DURATION_TICKS
        return Frame(targetY - 1.0 + t, yaw + (360.0 * t).toFloat(), GuideAllayDimensions.SCALE * t)
    }

    fun disappearing(startY: Double, yaw: Float, scale: Double, tick: Int): Frame {
        val t = tick.coerceIn(0, DURATION_TICKS).toDouble() / DURATION_TICKS
        return Frame(startY + t, yaw + (360.0 * t).toFloat(), scale * (1.0 - t))
    }
}
