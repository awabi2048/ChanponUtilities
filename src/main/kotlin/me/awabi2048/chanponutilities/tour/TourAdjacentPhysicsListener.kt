package me.awabi2048.chanponutilities.tour

import org.bukkit.block.Block
import java.util.UUID

internal data class BlockPosition(val world: UUID, val x: Int, val y: Int, val z: Int) {
    fun isAdjacentTo(other: BlockPosition): Boolean = world == other.world &&
        kotlin.math.abs(x - other.x) + kotlin.math.abs(y - other.y) + kotlin.math.abs(z - other.z) == 1

    fun adjacent(): List<BlockPosition> = listOf(copy(x = x + 1), copy(x = x - 1), copy(y = y + 1),
        copy(y = y - 1), copy(z = z + 1), copy(z = z - 1))

    companion object { fun of(block: Block) = BlockPosition(block.world.uid, block.x, block.y, block.z) }
}

