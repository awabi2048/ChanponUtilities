package me.awabi2048.chanponutilities.guide

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** 逸脱時、プレイヤーを中心とした追従候補を計算します。 */
internal object GuideFollowAnchor {
    fun candidates(x: Double, y: Double, z: Double, targetX: Double, targetZ: Double): List<Triple<Double, Double, Double>> {
        val angle = atan2(targetZ - z, targetX - x)
        return listOf(0.0, 25.0, -25.0, 50.0, -50.0, 90.0, -90.0).map { offset ->
            val direction = angle + Math.toRadians(offset)
            Triple(x + 2.0 * cos(direction), y + 1.0, z + 2.0 * sin(direction))
        }
    }
}
