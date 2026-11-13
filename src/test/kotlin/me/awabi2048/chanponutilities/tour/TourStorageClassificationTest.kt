package me.awabi2048.chanponutilities.tour

import java.lang.reflect.Proxy
import org.bukkit.block.Dropper
import org.bukkit.entity.minecart.HopperMinecart
import org.bukkit.entity.Player
import org.bukkit.entity.Villager
import org.bukkit.entity.Cow
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.MerchantInventory
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TourStorageClassificationTest {
    private fun <T> proxy(type: Class<T>): T = type.cast(Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, _ ->
        error("想定外の呼び出し: ${method.name}")
    })

    private fun inventory(holder: InventoryHolder?): Inventory = Inventory::class.java.cast(
        Proxy.newProxyInstance(Inventory::class.java.classLoader, arrayOf(Inventory::class.java)) { _, method, _ ->
            if (method.name == "getHolder") holder else error("想定外の呼び出し: ${method.name}")
        }
    )

    @Test fun `村人だけを取引対象として扱う`() {
        assertTrue(isTourTradeTarget(proxy(Villager::class.java)))
        assertFalse(isTourTradeTarget(proxy(Cow::class.java)))
    }

    @Test fun `ワールドの収納を制限し独自GUIとプレイヤー自身の収納は除外する`() {
        assertTrue(isWorldStorage(inventory(proxy(Dropper::class.java))))
        assertTrue(isWorldStorage(inventory(proxy(HopperMinecart::class.java))))
        assertFalse(isWorldStorage(inventory(proxy(Player::class.java))))
        assertFalse(isWorldStorage(inventory(null)))
        val merchant = MerchantInventory::class.java.cast(Proxy.newProxyInstance(MerchantInventory::class.java.classLoader, arrayOf(MerchantInventory::class.java)) { _, method, _ ->
            if (method.name == "getHolder") proxy(Villager::class.java) else error("想定外の呼び出し: ${method.name}")
        })
        assertFalse(isWorldStorage(merchant))
    }
}
