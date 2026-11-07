package me.awabi2048.chanponutilities.guide

import kotlin.math.sqrt

/** 前回からの移動距離を持ち越し、軌跡上で0.1ブロックごとに粒子を配置します。 */
internal class GuideParticleSpacing {
    private var previous: Triple<Double, Double, Double>? = null
    private var untilNext = 0.1

    fun reset() { previous = null; untilNext = 0.1 }

    fun points(to: Triple<Double, Double, Double>): List<Triple<Double, Double, Double>> {
        val from = previous
        previous = to
        if (from == null) return listOf(to)
        val dx = to.first - from.first; val dy = to.second - from.second; val dz = to.third - from.third
        val length = sqrt(dx * dx + dy * dy + dz * dz)
        if (length < 1.0e-12) return emptyList()
        val points = mutableListOf<Triple<Double, Double, Double>>()
        var distance = untilNext
        while (distance <= length + 1.0e-10) {
            val t = (distance / length).coerceAtMost(1.0)
            points.add(Triple(from.first + dx * t, from.second + dy * t, from.third + dz * t))
            distance += 0.1
        }
        untilNext = (distance - length).coerceIn(1.0e-10, 0.1)
        return points
    }
}
