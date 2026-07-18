package me.awabi2048.chanponutilities.freecam

import com.awabi2048.ccsystem.api.item.ItemArgumentSuggestionProvider
import com.awabi2048.ccsystem.api.item.ItemGrantDefinition
import com.awabi2048.ccsystem.api.item.ItemGrantProvider
import com.awabi2048.ccsystem.api.item.ItemGrantRequest
import com.awabi2048.ccsystem.api.item.ItemGrantResult
import me.awabi2048.chanponutilities.ChanponUtilitiesPlugin
import me.awabi2048.chanponutilities.util.ChanponI18n
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

class FreecamItemGrantProvider(private val plugin: ChanponUtilitiesPlugin) : ItemGrantProvider {
    override val owner = "chanpon-utilities"
    override fun definitions() = listOf(ItemGrantDefinition(
        "chanpon_utilities.freecam_activator", "chanponutilities.freecam", 64,
        ItemArgumentSuggestionProvider { emptyList() }
    ))

    override fun grant(request: ItemGrantRequest): ItemGrantResult {
        var remaining = request.amount
        var granted = 0
        var dropped = 0
        while (remaining > 0) {
            val amount = minOf(remaining, 64)
            val item = createItem(request.target, amount)
            val leftovers = request.target.inventory.addItem(item)
            granted += amount - leftovers.values.sumOf { it.amount }
            leftovers.values.forEach { request.target.world.dropItemNaturally(request.target.location, it); dropped += it.amount }
            remaining -= amount
        }
        return ItemGrantResult(true, granted, dropped, null)
    }

    private fun createItem(player: org.bukkit.entity.Player, amount: Int): ItemStack {
        val material = Material.matchMaterial(plugin.config.getString("features.freecam.activation.item.material", "ENDER_EYE") ?: "ENDER_EYE") ?: Material.ENDER_EYE
        val item = ItemStack(material, amount)
        val meta = item.itemMeta
        meta.displayName(Component.text(ChanponI18n.text(player, "freecam.item.name")).decoration(TextDecoration.ITALIC, false))
        meta.lore(listOf(Component.text(ChanponI18n.text(player, "freecam.item.lore")).decoration(TextDecoration.ITALIC, false)))
        meta.persistentDataContainer.set(NamespacedKey(plugin, "freecam_activator"), PersistentDataType.BYTE, 1)
        item.itemMeta = meta
        return item
    }
}
