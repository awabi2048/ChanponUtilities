package me.awabi2048.chanponutilities.policy

import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import java.util.UUID
import me.awabi2048.myworldmanager.model.ManagedDimension
import me.awabi2048.myworldmanager.model.WorldData
import org.bukkit.Material
import org.bukkit.entity.Player
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 建築可否を WorldGuard 側へ一元化した後のポリシー契約を固定する。
 *
 * `buildAllowed` は常に null（意見なし）でなければならない。
 * これにより `MyWorldManagerApi.canBuildInWorld` の連言合成が
 * このポリシー単独で拒否を生まないことを保証する。
 */
class ChanponPlayerStatePolicyTest {
    private val owner = UUID.randomUUID()
    private val member = UUID.randomUUID()
    private val guest = UUID.randomUUID()

    private fun worldData(): WorldData = WorldData(
        uuid = UUID.randomUUID(),
        dimension = ManagedDimension.OVERWORLD,
        name = "test",
        description = "",
        icon = Material.STONE,
        sourceWorld = "template",
        expireDate = "",
        owner = owner,
        members = mutableListOf(member),
        allowFlight = true
    )

    private fun player(uuid: UUID, admin: Boolean): Player {
        val handler = InvocationHandler { _, method, args ->
            when (method.name) {
                "getUniqueId" -> uuid
                "hasPermission" -> admin
                else -> throw UnsupportedOperationException("unexpected call: ${method.name}")
            }
        }
        return Proxy.newProxyInstance(
            Player::class.java.classLoader,
            arrayOf(Player::class.java),
            handler
        ) as Player
    }

    @Test
    fun `policy returns no build opinion for owner member and guest`() {
        val policy = ChanponPlayerStatePolicy()
        val world = worldData()
        for (uuid in listOf(owner, member, guest)) {
            val decision = policy.evaluate(player(uuid, admin = false), world)
            assertNull(decision.buildAllowed, "buildAllowed must stay null for $uuid")
        }
    }

    @Test
    fun `flight decision still distinguishes builders from guests`() {
        val policy = ChanponPlayerStatePolicy()
        val world = worldData()
        assertEquals(true, policy.evaluate(player(owner, admin = false), world).flightAllowed)
        assertEquals(true, policy.evaluate(player(member, admin = false), world).flightAllowed)
        assertEquals(false, policy.evaluate(player(guest, admin = false), world).flightAllowed)
        assertEquals(true, policy.evaluate(player(guest, admin = true), world).flightAllowed)
    }

    @Test
    fun `policy id is stable for canBuildInWorld composition`() {
        assertTrue(ChanponPlayerStatePolicy.POLICY_ID.isNotBlank())
    }
}
