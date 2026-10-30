package me.awabi2048.chanponutilities.guide

/** 中継点での連続滞在と、送信後の中断しない待機を管理します。 */
internal class GuideWaypointProgress(private val dwellTicks: Long, private val waitTicks: Long) {
    enum class Event { NONE, MESSAGE, MESSAGE_AND_ADVANCE, ADVANCE }

    companion object {
        /** 円柱は水平半径と中心からの高さをそれぞれ境界を含めて判定します。 */
        fun contains(x: Double, y: Double, z: Double, centerX: Double, centerY: Double, centerZ: Double, radius: Double, verticalRadius: Double): Boolean {
            val dx = x - centerX
            val dz = z - centerZ
            return dx * dx + dz * dz <= radius * radius && kotlin.math.abs(y - centerY) <= verticalRadius
        }
    }

    private var elapsed: Long = 0
    var messageSent: Boolean = false
        private set

    /** 送信前は範囲外で滞在をリセットし、送信後は範囲に関係なく待ちます。 */
    fun tick(inRange: Boolean): Event {
        if (!messageSent) {
            if (!inRange) {
                elapsed = 0
                return Event.NONE
            }
            if (++elapsed < dwellTicks) return Event.NONE
            elapsed = 0
            messageSent = true
            return if (waitTicks == 0L) Event.MESSAGE_AND_ADVANCE else Event.MESSAGE
        }
        return if (++elapsed >= waitTicks) Event.ADVANCE else Event.NONE
    }
}
