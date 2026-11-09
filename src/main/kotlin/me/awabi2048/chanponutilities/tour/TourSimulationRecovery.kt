package me.awabi2048.chanponutilities.tour

import org.bukkit.GameMode
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.UUID

/** 再接続・再起動をまたいでも、一時的な観光モードの前の状態を復元します。 */
internal class TourSimulationRecovery(private val file: File) {
    private val data = YamlConfiguration.loadConfiguration(file)

    fun get(player: UUID): GameMode? = data.getString(player.toString())?.let { name ->
        runCatching { GameMode.valueOf(name) }.getOrNull()
    }

    fun remember(player: UUID, mode: GameMode) {
        if (get(player) != null) return
        data.set(player.toString(), mode.name)
        save()
    }

    fun forget(player: UUID) {
        data.set(player.toString(), null)
        save()
    }

    private fun save() {
        file.parentFile.mkdirs()
        val temp = File(file.parentFile, "${file.name}.tmp")
        data.save(temp)
        FileChannel.open(temp.toPath(), StandardOpenOption.WRITE).use { it.force(true) }
        try {
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
