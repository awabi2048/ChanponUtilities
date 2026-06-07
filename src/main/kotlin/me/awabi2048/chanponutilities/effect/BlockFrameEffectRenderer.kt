package me.awabi2048.chanponutilities.effect

import org.bukkit.Color
import org.bukkit.Particle
import org.bukkit.World
import org.bukkit.block.Block

object BlockFrameEffectRenderer {
    fun render(world: World, block: Block, color: Color, size: Float, pointsPerEdge: Int) {
        val edgePoints = pointsPerEdge.coerceAtLeast(1)
        val dust = Particle.DustOptions(color, size)
        val x0 = block.x.toDouble()
        val y0 = block.y.toDouble()
        val z0 = block.z.toDouble()
        val x1 = x0 + 1.0
        val y1 = y0 + 1.0
        val z1 = z0 + 1.0

        for (i in 0..edgePoints) {
            val t = i.toDouble() / edgePoints.toDouble()

            spawn(world, x0 + t, y0, z0, dust)
            spawn(world, x0 + t, y0, z1, dust)
            spawn(world, x0 + t, y1, z0, dust)
            spawn(world, x0 + t, y1, z1, dust)

            spawn(world, x0, y0 + t, z0, dust)
            spawn(world, x1, y0 + t, z0, dust)
            spawn(world, x0, y0 + t, z1, dust)
            spawn(world, x1, y0 + t, z1, dust)

            spawn(world, x0, y0, z0 + t, dust)
            spawn(world, x1, y0, z0 + t, dust)
            spawn(world, x0, y1, z0 + t, dust)
            spawn(world, x1, y1, z0 + t, dust)
        }
    }

    private fun spawn(world: World, x: Double, y: Double, z: Double, dust: Particle.DustOptions) {
        world.spawnParticle(Particle.DUST, x, y, z, 1, 0.0, 0.0, 0.0, 0.0, dust)
    }
}
