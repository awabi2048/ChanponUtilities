package me.awabi2048.chanponutilities.freecam

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.UUID

class FreecamPreferenceStore(plugin: JavaPlugin) {
    private val plugin = plugin
    private val file = File(plugin.dataFolder, "data/freecam-preferences.yml")
    private val yaml = YamlConfiguration.loadConfiguration(file)

    @Synchronized
    fun isShortcutEnabled(playerId: UUID): Boolean =
        yaml.getBoolean("players.$playerId.shortcut_enabled", true)

    @Synchronized
    fun toggleShortcut(playerId: UUID): Boolean {
        val enabled = !isShortcutEnabled(playerId)
        yaml.set("players.$playerId.shortcut_enabled", enabled)
        file.parentFile.mkdirs()
        yaml.save(file)
        return enabled
    }

    @Synchronized
    fun resetDistance(playerId: UUID): Int =
        yaml.getInt(
            "players.$playerId.reset_distance",
            plugin.config.getInt("features.freecam.reset_distance_default", 128)
        )

    @Synchronized
    fun setResetDistance(playerId: UUID, distance: Int) {
        yaml.set("players.$playerId.reset_distance", distance)
        persist()
    }

    @Synchronized
    fun resetResetDistance(playerId: UUID): Int {
        yaml.set("players.$playerId.reset_distance", null)
        persist()
        return resetDistance(playerId)
    }

    @Synchronized
    fun shouldShowStartMessage(
        playerId: UUID,
        nowMillis: Long = System.currentTimeMillis(),
        cooldownMillis: Long = 5 * 60 * 1000L
    ): Boolean {
        val path = "players.$playerId.last_start_message_at"
        val lastShownAt = yaml.getLong(path).takeIf { yaml.contains(path) }
        if (!shouldShowFreecamStartMessage(lastShownAt, nowMillis, cooldownMillis)) {
            return false
        }
        yaml.set(path, nowMillis)
        persist()
        return true
    }

    private fun persist() {
        file.parentFile.mkdirs()
        yaml.save(file)
    }
}
