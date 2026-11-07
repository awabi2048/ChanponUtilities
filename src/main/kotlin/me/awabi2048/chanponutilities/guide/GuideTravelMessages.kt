package me.awabi2048.chanponutilities.guide

/** 出発後の移動 tick のみで遅延を進め、順序を維持して期日到来分を返します。 */
internal class GuideTravelMessages(messages: List<GuideMessageLine>) {
    private val pending = messages.filter { it.delay != null }.toMutableList()
    private var movingTicks = 0L

    val remainingTicks: Long get() = (pending.maxOfOrNull { it.delay!! }?.minus(movingTicks) ?: 0L).coerceAtLeast(0L)
    val isEmpty: Boolean get() = pending.isEmpty()

    fun departure(): List<String> = due()

    fun tick(moving: Boolean): List<String> {
        if (moving) movingTicks++
        return due()
    }

    private fun due(): List<String> {
        val due = pending.filter { it.delay!! <= movingTicks }
        pending.removeAll(due.toSet())
        return due.map { it.text }
    }
}
