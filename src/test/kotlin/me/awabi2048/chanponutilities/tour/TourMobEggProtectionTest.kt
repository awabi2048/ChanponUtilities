package me.awabi2048.chanponutilities.tour

import java.lang.reflect.Proxy
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.entity.Entity
import org.bukkit.entity.Mob
import org.bukkit.event.entity.EntityInteractEvent
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TourMobEggProtectionTest {
    private fun <T> proxy(type: Class<T>, handler: (String) -> Any?): T = type.cast(
        Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, _ -> handler(method.name) }
    )

    private fun interaction(entityType: Class<out Entity>, material: Material): EntityInteractEvent {
        val entity = proxy(entityType) { error("想定外のエンティティ操作: $it") }
        val world = proxy(World::class.java) { error("想定外のワールド操作: $it") }
        val block = proxy(Block::class.java) { when (it) { "getType" -> material; "getWorld" -> world; else -> error("想定外のブロック操作: $it") } }
        return EntityInteractEvent(entity, block)
    }

    @Test fun `Mobによるカメの卵への作用だけを拒否する`() {
        val listener = TourMobEggProtection()
        val mobEgg = interaction(Mob::class.java, Material.TURTLE_EGG)
        val mobOther = interaction(Mob::class.java, Material.FARMLAND)
        val nonMobEgg = interaction(Entity::class.java, Material.TURTLE_EGG)
        listOf(mobEgg, mobOther, nonMobEgg).forEach(listener::onInteract)
        assertTrue(mobEgg.isCancelled)
        assertFalse(mobOther.isCancelled)
        assertFalse(nonMobEgg.isCancelled)
        val inactive = interaction(Mob::class.java, Material.TURTLE_EGG)
        TourMobEggProtection { false }.onInteract(inactive)
        assertFalse(inactive.isCancelled)
    }
}
