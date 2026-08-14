package me.awabi2048.chanponutilities.safety

import com.awabi2048.ccsystem.api.localization.generated.ChanponUtilitiesKeys as Keys

import java.util.UUID
import me.awabi2048.chanponutilities.util.ChanponI18n
import org.bukkit.NamespacedKey
import org.bukkit.attribute.Attribute
import org.bukkit.attribute.AttributeModifier
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

class TourSafetyService(
    private val plugin: JavaPlugin,
    private val preferences: TourSafetyPreferenceStore
) {
    private val lastNoticeAt = mutableMapOf<UUID, Long>()
    private val blockRangeKey = NamespacedKey(plugin, "tour_safety_block_range")
    private val entityRangeKey = NamespacedKey(plugin, "tour_safety_entity_range")

    fun isEnabled(player: Player): Boolean = preferences.isEnabled(player.uniqueId)

    fun toggle(player: Player): Boolean {
        val enabled = !isEnabled(player)
        preferences.setEnabled(player.uniqueId, enabled)
        synchronize(player)
        player.sendMessage(ChanponI18n.text(player, if (enabled) Keys.CHANPON_UTILITIES_SAFETY_ENABLED else Keys.CHANPON_UTILITIES_SAFETY_DISABLED))
        return enabled
    }

    fun synchronize(player: Player) {
        synchronizeAttribute(player, Attribute.BLOCK_INTERACTION_RANGE, blockRangeKey, isEnabled(player))
        synchronizeAttribute(player, Attribute.ENTITY_INTERACTION_RANGE, entityRangeKey, isEnabled(player))
    }

    fun clear(player: Player) {
        synchronizeAttribute(player, Attribute.BLOCK_INTERACTION_RANGE, blockRangeKey, false)
        synchronizeAttribute(player, Attribute.ENTITY_INTERACTION_RANGE, entityRangeKey, false)
    }

    fun reject(player: Player, nowMillis: Long = System.currentTimeMillis()) {
        val last = lastNoticeAt[player.uniqueId]
        if (shouldShowTourSafetyNotice(last, nowMillis)) {
            lastNoticeAt[player.uniqueId] = nowMillis
            player.sendMessage(ChanponI18n.text(player, Keys.CHANPON_UTILITIES_SAFETY_BLOCKED))
        }
    }

    private fun synchronizeAttribute(
        player: Player,
        attribute: Attribute,
        key: NamespacedKey,
        enabled: Boolean
    ) {
        val instance = player.getAttribute(attribute) ?: return
        instance.removeModifier(key)
        if (enabled) {
            instance.addTransientModifier(
                AttributeModifier(key, -1.0, AttributeModifier.Operation.ADD_SCALAR)
            )
        }
    }

    companion object {
        internal const val NOTICE_COOLDOWN_MILLIS = 60_000L
    }
}

internal fun shouldShowTourSafetyNotice(lastShownAtMillis: Long?, nowMillis: Long): Boolean =
    lastShownAtMillis == null ||
        nowMillis < lastShownAtMillis ||
        nowMillis - lastShownAtMillis >= TourSafetyService.NOTICE_COOLDOWN_MILLIS
