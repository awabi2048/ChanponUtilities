package me.awabi2048.chanponutilities.api

import java.lang.reflect.Proxy
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.inventory.EquipmentSlot
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class TourEntityClickEventTest {
    private fun <T> proxy(type: Class<T>): T = type.cast(
        Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, _ ->
            error("想定外の呼び出し: ${method.name}")
        }
    )

    @Test fun `インスタンスと静的APIが同じハンドラー一覧を返す`() {
        val event = TourEntityClickEvent(proxy(Player::class.java), proxy(Entity::class.java), EquipmentSlot.HAND)
        assertSame(TourEntityClickEvent.getHandlerList(), event.handlers)
    }
}
