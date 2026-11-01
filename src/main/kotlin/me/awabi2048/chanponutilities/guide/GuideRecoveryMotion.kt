package me.awabi2048.chanponutilities.guide

/** 経路への接近を検出し、停止後も指定 tick 数だけ誘導を継続します。 */
internal class GuideRecoveryMotion(private val graceTicks: Int = 100) {
    private var previousX = Double.NaN
    private var previousY = Double.NaN
    private var previousZ = Double.NaN
    private var previousDistance = Double.NaN
    private var idleTicks = 0
    private var active = false

    fun update(x: Double, y: Double, z: Double, distance: Double): Boolean {
        if (previousX.isNaN()) {
            previousX = x; previousY = y; previousZ = z; previousDistance = distance
            return false
        }
        val moved = GuideCourseMath.distanceSq(x, y, z, previousX, previousY, previousZ) >= 0.0001
        if (moved) {
            active = distance < previousDistance - 0.001
            idleTicks = 0
            previousX = x; previousY = y; previousZ = z; previousDistance = distance
        } else if (active && ++idleTicks >= graceTicks) {
            active = false
        }
        return active
    }
}
