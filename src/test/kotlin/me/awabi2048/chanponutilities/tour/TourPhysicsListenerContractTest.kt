package me.awabi2048.chanponutilities.tour

import java.lang.reflect.Proxy
import java.util.UUID
import com.destroystokyo.paper.event.block.BlockDestroyEvent
import org.bukkit.Material
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.block.data.BlockData
import org.bukkit.block.data.Powerable
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockPhysicsEvent
import org.bukkit.event.block.BlockRedstoneEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.inventory.EquipmentSlot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TourPhysicsListenerContractTest {
    private fun <T> proxy(type: Class<T>, body: (String) -> Any?): T = type.cast(
        Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, _ -> body(method.name) },
    )
    private val id = UUID.randomUUID()
    private val world = proxy(World::class.java) { if (it == "getUID") id else error("world.$it") }
    private val player = proxy(Player::class.java) { error("player.$it") }

    private fun block(x: Int, y: Int, material: Material, powered: Boolean = false, tick: () -> Unit = {}): Block {
        val data = if (material == Material.STONE_BUTTON) proxy(Powerable::class.java) {
            when (it) { "isPowered" -> powered; "getMaterial" -> material; else -> error("data.$it") }
        } else proxy(BlockData::class.java) { if (it == "getMaterial") material else error("data.$it") }
        return proxy(Block::class.java) {
            when (it) {
                "getWorld" -> world; "getX" -> x; "getY" -> y; "getZ" -> 0
                "getType" -> material; "getBlockData" -> data; "tick" -> { tick(); null }
                else -> error("block.$it")
            }
        }
    }

    private fun interact(block: Block) = PlayerInteractEvent(
        player, Action.RIGHT_CLICK_BLOCK, null, block, BlockFace.UP, EquipmentSlot.HAND,
    )

    @Test
    fun `観光操作の直後だけ隣接装飾の物理更新を止め回路は通す`() {
        val pending = mutableListOf<() -> Unit>()
        val listener = TourAdjacentPhysicsListener({ true }, { pending.add(it) })
        val lever = block(0, 64, Material.LEVER)
        val lantern = block(1, 64, Material.LANTERN)
        val diagonal = block(1, 65, Material.LANTERN)
        val wire = block(-1, 64, Material.REDSTONE_WIRE)
        listener.onInteract(interact(lever))
        fun physics(block: Block) = BlockPhysicsEvent(block, block.blockData, lever).also(listener::onPhysics)
        assertTrue(physics(lantern).isCancelled)
        val destroy = BlockDestroyEvent(lantern, lantern.blockData, lantern.blockData, 0, false)
        listener.onDestroy(destroy)
        assertTrue(destroy.isCancelled)
        assertFalse(physics(diagonal).isCancelled)
        assertFalse(physics(wire).isCancelled)
        pending.forEach { it() }
        assertFalse(physics(lantern).isCancelled)
        val denied = interact(lever).also { it.setUseInteractedBlock(Event.Result.DENY) }
        listener.onInteract(denied)
        assertFalse(physics(lantern).isCancelled)
    }

    @Test
    fun `既に押されたボタンは復帰するが通常の通電中や拒否操作には触れない`() {
        var ticks = 0
        val button = block(0, 64, Material.STONE_BUTTON, powered = true) { ticks++ }
        val listener = StuckPoweredControlListener({}, { _, _ -> }, {})
        listener.onButton(interact(button))
        assertEquals(1, ticks)
        listener.onRedstone(BlockRedstoneEvent(button, 0, 15))
        listener.onButton(interact(button))
        assertEquals(1, ticks)
        listener.onRedstone(BlockRedstoneEvent(button, 15, 0))
        listener.onButton(interact(button).also { it.setUseInteractedBlock(Event.Result.DENY) })
        assertEquals(1, ticks)
    }

    @Test
    fun `通電した感圧板への踏み込みは権限判定に通った場合だけ通常tickを行う`() {
        var ticks = 0
        lateinit var plate: Block
        val fakeWorld = proxy(World::class.java) {
            when (it) { "getUID" -> id; "getBlockAt" -> plate; "equals" -> true; "hashCode" -> 1; else -> error("world.$it") }
        }
        val powered = proxy(Powerable::class.java) { if (it == "isPowered") true else error("data.$it") }
        plate = proxy(Block::class.java) {
            when (it) {
                "getWorld" -> fakeWorld; "getX" -> 0; "getY" -> 64; "getZ" -> 0
                "getType" -> Material.STONE_PRESSURE_PLATE; "getBlockData" -> powered
                "tick" -> { ticks++; null }; else -> error("block.$it")
            }
        }
        val location = Location(fakeWorld, 0.5, 64.0, 0.5)
        val person = proxy(Player::class.java) {
            when (it) {
                "getGameMode" -> GameMode.ADVENTURE; "isDead" -> false; "isOnline" -> true
                "getUniqueId" -> id; "getLocation" -> location; else -> error("player.$it")
            }
        }
        val from = Location(fakeWorld, -1.5, 64.0, 0.5)
        val move = PlayerMoveEvent(person, from, location)
        val pending = mutableListOf<() -> Unit>()
        val denied = StuckPoweredControlListener({ pending.add(it) }, { _, _ -> }, {
            it.setUseInteractedBlock(Event.Result.DENY)
        })
        denied.onMove(move)
        pending.removeAt(0).invoke()
        assertEquals(0, ticks)
        val allowed = StuckPoweredControlListener({ pending.add(it) }, { _, _ -> }, {})
        allowed.onMove(move)
        pending.removeAt(0).invoke()
        assertEquals(1, ticks)
    }
}
