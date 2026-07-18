package me.awabi2048.chanponutilities.freecam

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.UUID

class FreecamPreferenceStore(plugin: JavaPlugin) {
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
}
