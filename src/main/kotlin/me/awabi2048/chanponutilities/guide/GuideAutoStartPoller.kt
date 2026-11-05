package me.awabi2048.chanponutilities.guide

import java.util.UUID

/** 定期判定の前回位置を保持し、ガイド中も進入状態を更新します。 */
internal class GuideAutoStartPoller {
    private val previous = HashMap<UUID, GuideAutoStart.Position>()

    fun record(id: UUID, current: GuideAutoStart.Position) { previous[id] = current }
    fun forget(id: UUID) { previous.remove(id) }
    fun clear() { previous.clear() }

    fun poll(id: UUID, current: GuideAutoStart.Position, routes: List<GuideRoute>, active: Boolean): GuideRoute? {
        val from = previous.put(id, current) ?: return null
        if (active || from == current) return null
        return GuideAutoStart.select(routes, from, current)
    }
}
