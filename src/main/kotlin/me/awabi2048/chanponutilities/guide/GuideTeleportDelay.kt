package me.awabi2048.chanponutilities.guide

/** 32ブロック以上の状態が100 tick続いた場合に再配置を許可します。 */
internal class GuideTeleportDelay {
    private var ticks = 0

    fun tick(distanceSquared: Double): Boolean {
        ticks = if (GuideCourseMath.shouldTeleport(distanceSquared)) (ticks + 1).coerceAtMost(100) else 0
        return ticks >= 100
    }
}
