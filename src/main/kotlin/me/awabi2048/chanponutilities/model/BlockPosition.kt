package me.awabi2048.chanponutilities.model

import org.bukkit.Bukkit
import org.bukkit.block.Block
import java.util.UUID

data class BlockPosition(
    val worldId: UUID,
    val x: Int,
    val y: Int,
    val z: Int
) {
    fun serialize(): String {
        return "$worldId;$x;$y;$z"
    }

    fun resolveBlock(): Block? {
        val world = Bukkit.getWorld(worldId) ?: return null
        return world.getBlockAt(x, y, z)
    }

    companion object {
        fun fromBlock(block: Block): BlockPosition {
            return BlockPosition(
                worldId = block.world.uid,
                x = block.x,
                y = block.y,
                z = block.z
            )
        }

        fun parse(serialized: String): BlockPosition? {
            val parts = serialized.split(';')
            if (parts.size != 4) {
                return null
            }

            val world = runCatching { UUID.fromString(parts[0]) }.getOrNull() ?: return null
            val x = parts[1].toIntOrNull() ?: return null
            val y = parts[2].toIntOrNull() ?: return null
            val z = parts[3].toIntOrNull() ?: return null
            return BlockPosition(world, x, y, z)
        }
    }
}
