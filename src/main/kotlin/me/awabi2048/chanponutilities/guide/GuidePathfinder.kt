package me.awabi2048.chanponutilities.guide

import java.util.PriorityQueue
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sqrt

/** 空間内の通過可能な位置を探索し、確認できない経路は返しません。 */
internal object GuidePathfinder {
    private data class Cell(val x: Int, val y: Int, val z: Int) {
        fun point() = Triple(x + 0.5, y.toDouble(), z + 0.5)
    }
    private data class Entry(val cell: Cell, val score: Double)

    fun find(start: Triple<Double, Double, Double>, goal: Triple<Double, Double, Double>,
             canOccupy: (Double, Double, Double) -> Boolean): List<Triple<Double, Double, Double>>? {
        fun distance(a: Triple<Double, Double, Double>, b: Triple<Double, Double, Double>): Double =
            sqrt(GuideCourseMath.distanceSq(a.first, a.second, a.third, b.first, b.second, b.third))
        // 間隔0.1以下で占有判定する。呼び出し側では当たり判定をさらに0.1広げる。
        fun clear(a: Triple<Double, Double, Double>, b: Triple<Double, Double, Double>): Boolean {
            val steps = ceil(distance(a, b) / 0.1).toInt().coerceAtLeast(1)
            return (0..steps).all { i ->
                val t = i.toDouble() / steps
                canOccupy(a.first + (b.first-a.first)*t, a.second + (b.second-a.second)*t, a.third + (b.third-a.third)*t)
            }
        }
        if (clear(start, goal)) return listOf(start, goal)
        fun cell(p: Triple<Double, Double, Double>) = Cell(floor(p.first).toInt(), floor(p.second).toInt(), floor(p.third).toInt())
        val first = cell(start)
        val last = cell(goal)
        if (!clear(start, first.point()) || !clear(last.point(), goal)) return null
        val margin = 12
        val minX = minOf(first.x, last.x) - margin; val maxX = maxOf(first.x, last.x) + margin
        val minY = minOf(first.y, last.y) - margin; val maxY = maxOf(first.y, last.y) + margin
        val minZ = minOf(first.z, last.z) - margin; val maxZ = maxOf(first.z, last.z) + margin
        val open = PriorityQueue(compareBy<Entry> { it.score })
        val costs = mutableMapOf(first to 0.0)
        val previous = mutableMapOf<Cell, Cell>()
        val closed = mutableSetOf<Cell>()
        open.add(Entry(first, distance(first.point(), goal)))
        while (open.isNotEmpty() && closed.size < 1200) {
            val current = open.remove().cell
            if (!closed.add(current)) continue
            if (current == last) {
                val route = mutableListOf(goal, last.point())
                var cursor = last
                while (cursor != first) { cursor = previous[cursor] ?: return null; route.add(cursor.point()) }
                route.add(start); route.reverse()
                // 視線が通る範囲を直線化し、不要な格子状の折れ曲がりを減らします。
                val result = mutableListOf(route.first())
                var i = 0
                while (i < route.lastIndex) {
                    var j = route.lastIndex
                    while (j > i + 1 && !clear(route[i], route[j])) j--
                    result.add(route[j]); i = j
                }
                return result
            }
            for (dx in -1..1) for (dy in -1..1) for (dz in -1..1) {
                if (dx == 0 && dy == 0 && dz == 0) continue
                val next = Cell(current.x + dx, current.y + dy, current.z + dz)
                if (next.x !in minX..maxX || next.y !in minY..maxY || next.z !in minZ..maxZ || next in closed) continue
                val cost = costs.getValue(current) + sqrt((dx*dx + dy*dy + dz*dz).toDouble())
                if (cost >= (costs[next] ?: Double.POSITIVE_INFINITY) || !clear(current.point(), next.point())) continue
                costs[next] = cost; previous[next] = current
                open.add(Entry(next, cost + distance(next.point(), goal)))
            }
        }
        return null
    }
}
