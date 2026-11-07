package me.awabi2048.chanponutilities.guide

/** メッセージのない地点を通過点とし、次に停止する地点または終点までを1区間とします。 */
internal object GuideStopPlan {
    fun nextStopIndex(current: Int, hasMessage: List<Boolean>): Int? {
        if (current >= hasMessage.lastIndex) return null
        for (i in current + 1..hasMessage.lastIndex) {
            if (hasMessage[i]) return i
        }
        return hasMessage.lastIndex
    }
}
