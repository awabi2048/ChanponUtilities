package me.awabi2048.chanponutilities.freecam

import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.UUID

class FreecamRecoveryStore(plugin: JavaPlugin) {
    private val file = File(plugin.dataFolder, "data/freecam-recovery.yml")
    private val yaml = YamlConfiguration.loadConfiguration(file)

    @Synchronized
    fun save(snapshot: BodySnapshot) {
        val path = "players.${snapshot.playerId}"
        yaml.set("$path.world_key", snapshot.anchor.world.key.toString())
        yaml.set("$path.x", snapshot.anchor.x)
        yaml.set("$path.y", snapshot.anchor.y)
        yaml.set("$path.z", snapshot.anchor.z)
        yaml.set("$path.yaw", snapshot.anchor.yaw.toDouble())
        yaml.set("$path.pitch", snapshot.anchor.pitch.toDouble())
        yaml.set("$path.game_mode", snapshot.originalGameMode.name)
        yaml.set("$path.allow_flight", snapshot.allowFlight)
        yaml.set("$path.flying", snapshot.flying)
        persist()
    }

    @Synchronized
    fun load(playerId: UUID): RecoveryEntry? {
        val path = "players.$playerId"
        val key = org.bukkit.NamespacedKey.fromString(yaml.getString("$path.world_key") ?: return null) ?: return null
        val world = Bukkit.getWorld(key) ?: return null
        val mode = runCatching { GameMode.valueOf(yaml.getString("$path.game_mode") ?: "SURVIVAL") }.getOrDefault(GameMode.SURVIVAL)
        return RecoveryEntry(
            Location(world, yaml.getDouble("$path.x"), yaml.getDouble("$path.y"), yaml.getDouble("$path.z"), yaml.getDouble("$path.yaw").toFloat(), yaml.getDouble("$path.pitch").toFloat()),
            mode,
            yaml.getBoolean("$path.allow_flight"),
            yaml.getBoolean("$path.flying")
        )
    }

    @Synchronized
    fun remove(playerId: UUID) {
        yaml.set("players.$playerId", null)
        persist()
    }

    @Synchronized
    fun savePreviousCamera(playerId: UUID, location: Location) {
        val path = "previous_camera.$playerId"
        yaml.set("$path.world_key", location.world.key.toString())
        yaml.set("$path.x", location.x)
        yaml.set("$path.y", location.y)
        yaml.set("$path.z", location.z)
        yaml.set("$path.yaw", location.yaw.toDouble())
        yaml.set("$path.pitch", location.pitch.toDouble())
        persist()
    }

    @Synchronized
    fun loadPreviousCamera(playerId: UUID): Location? {
        val path = "previous_camera.$playerId"
        val key = org.bukkit.NamespacedKey.fromString(yaml.getString("$path.world_key") ?: return null) ?: return null
        val world = Bukkit.getWorld(key) ?: return null
        return Location(
            world,
            yaml.getDouble("$path.x"),
            yaml.getDouble("$path.y"),
            yaml.getDouble("$path.z"),
            yaml.getDouble("$path.yaw").toFloat(),
            yaml.getDouble("$path.pitch").toFloat()
        )
    }

    private fun persist() {
        file.parentFile.mkdirs()
        yaml.save(file)
    }
}

data class RecoveryEntry(val location: Location, val gameMode: GameMode, val allowFlight: Boolean, val flying: Boolean)
