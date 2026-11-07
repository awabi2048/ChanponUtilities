package me.awabi2048.chanponutilities.guide

import org.bukkit.configuration.file.YamlConfiguration
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID

/** プレイヤーとルートの組ごとに自動開始済みを永続化します。 */
internal class GuideAutoStartHistory(private val file: Path) {
    private val entries = mutableMapOf<UUID, MutableSet<String>>()

    init {
        if (Files.exists(file)) {
            val yaml = YamlConfiguration()
            yaml.load(file.toFile())
            for (key in yaml.getKeys(false)) {
                val id = UUID.fromString(key)
                entries[id] = yaml.getStringList(key).toMutableSet()
            }
        }
    }

    fun hasStarted(player: UUID, route: String): Boolean = entries[player]?.contains(route) == true

    /** 記録が保存できない場合は開始せず、次の機会に再試行できる状態を保ちます。 */
    fun record(player: UUID, route: String) {
        val updated = entries.mapValues { it.value.toMutableSet() }.toMutableMap()
        updated.getOrPut(player) { mutableSetOf() }.add(route)
        save(updated)
        entries.clear(); entries.putAll(updated)
    }

    fun clear(player: UUID, route: String): Boolean {
        if (!hasStarted(player, route)) return false
        val updated = entries.mapValues { it.value.toMutableSet() }.toMutableMap()
        updated[player]?.remove(route)
        if (updated[player].isNullOrEmpty()) updated.remove(player)
        save(updated)
        entries.clear(); entries.putAll(updated)
        return true
    }

    private fun save(updated: Map<UUID, Set<String>>) {
        Files.createDirectories(file.parent)
        val yaml = YamlConfiguration()
        updated.forEach { (id, routes) -> yaml.set(id.toString(), routes.sorted()) }
        val temp = file.resolveSibling("${file.fileName}.tmp")
        yaml.save(temp.toFile())
        try {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
