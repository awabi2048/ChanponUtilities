package me.awabi2048.chanponutilities.freecam

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class DoublePressTracker {
    private val presses = ConcurrentHashMap<UUID, Long>()

    fun press(playerId: UUID, now: Long, windowMillis: Long): Boolean {
        val previous = presses.put(playerId, now)
        if (previous == null || now - previous > windowMillis || now < previous) return false
        presses.remove(playerId)
        return true
    }

    fun clear(playerId: UUID) {
        presses.remove(playerId)
    }
}
