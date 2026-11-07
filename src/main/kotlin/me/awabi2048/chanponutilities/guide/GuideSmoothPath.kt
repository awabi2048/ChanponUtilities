package me.awabi2048.chanponutilities.guide

import kotlin.math.ceil
import kotlin.math.sqrt

/** 指定した全点を通る区分的なエルミート曲線を、衝突を検査しながら標本化します。 */
internal object GuideSmoothPath {
    fun build(points: List<Triple<Double, Double, Double>>, canOccupy: (Double, Double, Double) -> Boolean): List<Triple<Double, Double, Double>>? {
        if (points.size < 2) return null
        fun distance(a: Triple<Double, Double, Double>, b: Triple<Double, Double, Double>): Double =
            sqrt(GuideCourseMath.distanceSq(a.first, a.second, a.third, b.first, b.second, b.third))
        // 壁の近くでは接線を縮小し、最後は接線ゼロで確実に元の線分へ寄せます。
        for (tension in listOf(0.5, 0.25, 0.1, 0.0)) {
            val result = mutableListOf(points.first())
            var valid = canOccupy(points.first().first, points.first().second, points.first().third)
            for (i in 0 until points.lastIndex) {
                val a = points[i]; val b = points[i + 1]
                val prev = points[(i - 1).coerceAtLeast(0)]
                val next = points[(i + 2).coerceAtMost(points.lastIndex)]
                val steps = ceil((distance(prev, a) + distance(a, b) + distance(b, next)) * 30.0).toInt().coerceAtLeast(1)
                for (j in 1..steps) {
                    val t = j.toDouble() / steps; val t2 = t*t; val t3 = t2*t
                    val h00 = 2*t3 - 3*t2 + 1; val h10 = t3 - 2*t2 + t
                    val h01 = -2*t3 + 3*t2; val h11 = t3 - t2
                    fun coordinate(p: Double, q: Double, before: Double, after: Double) =
                        h00*p + h10*tension*(q-before) + h01*q + h11*tension*(after-p)
                    val p = Triple(coordinate(a.first,b.first,prev.first,next.first), coordinate(a.second,b.second,prev.second,next.second), coordinate(a.third,b.third,prev.third,next.third))
                    if (!canOccupy(p.first, p.second, p.third)) { valid = false; break }
                    result.add(p)
                }
                if (!valid) break
            }
            if (valid) return result
        }
        return null
    }
}
