package me.awabi2048.chanponutilities.guide

/** 中継点での連続滞在と、送信開始後に中断しない1行ずつの進行を管理します。 */
internal class GuideWaypointProgress(private val dwellTicks: Long, private val lines: List<String>) {
    data class Event(val started: Boolean = false, val lineIndices: List<Int> = emptyList(), val advance: Boolean = false)

    companion object {
        /** 円柱は水平半径と中心からの高さをそれぞれ境界を含めて判定します。 */
        fun contains(x: Double, y: Double, z: Double, centerX: Double, centerY: Double, centerZ: Double, radius: Double, verticalRadius: Double): Boolean {
            val dx = x - centerX
            val dz = z - centerZ
            return dx * dx + dz * dz <= radius * radius && kotlin.math.abs(y - centerY) <= verticalRadius
        }

        /** 画面に現れないレガシーカラー・装飾コードを除き、Unicodeコードポイントで数えます。 */
        fun displayLength(text: String): Int {
            val visible = text.replace(Regex("(?i)[&§][0-9a-fk-orx]"), "")
            return visible.codePointCount(0, visible.length)
        }
    }

    private var dwellElapsed: Long = 0
    private var remainingTicks: Long = 0
    private var nextLine = 0
    var messageSent: Boolean = false
        private set

    /** 最初の送信前だけ範囲外で滞在をリセットし、以降は範囲外でも進行します。 */
    fun tick(inRange: Boolean): Event {
        var started = false
        if (!messageSent) {
            if (!inRange) {
                dwellElapsed = 0
                return Event()
            }
            if (++dwellElapsed < dwellTicks) return Event()
            messageSent = true
            started = true
        } else if (remainingTicks > 0 && --remainingTicks > 0) {
            return Event()
        }
        val send = mutableListOf<Int>()
        while (nextLine < lines.size && remainingTicks == 0L) {
            val index = nextLine++
            send.add(index)
            remainingTicks = displayLength(lines[index]).toLong() * 5L
        }
        return Event(started = started, lineIndices = send, advance = nextLine == lines.size && remainingTicks == 0L)
    }
}
