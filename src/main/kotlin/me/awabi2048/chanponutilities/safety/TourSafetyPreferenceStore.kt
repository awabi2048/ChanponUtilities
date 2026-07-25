package me.awabi2048.chanponutilities.safety

import java.io.File
import java.util.UUID
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin

class TourSafetyPreferenceStore(plugin: JavaPlugin) {
    private val file = File(plugin.dataFolder, "data/tour-safety.yml")
    private val yaml = YamlConfiguration.loadConfiguration(file)

    fun isEnabled(playerId: UUID): Boolean =
        yaml.getBoolean("players.$playerId.enabled", false)

    fun setEnabled(playerId: UUID, enabled: Boolean) {
        if (enabled) {
            yaml.set("players.$playerId.enabled", true)
        } else {
            yaml.set("players.$playerId", null)
        }
        file.parentFile?.mkdirs()
        yaml.save(file)
    }
}
