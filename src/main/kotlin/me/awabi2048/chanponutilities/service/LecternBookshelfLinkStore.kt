package me.awabi2048.chanponutilities.service

import me.awabi2048.chanponutilities.model.BlockPosition
import org.bukkit.block.Block
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

class LecternBookshelfLinkStore(
    private val plugin: JavaPlugin
) {
    private val links = mutableMapOf<BlockPosition, BlockPosition>()

    fun load() {
        links.clear()
        val file = storageFile()
        if (!file.exists()) {
            return
        }

        val yaml = YamlConfiguration.loadConfiguration(file)
        val section = yaml.getConfigurationSection("links") ?: return
        for (bookshelfKey in section.getKeys(false)) {
            val lecternKey = section.getString(bookshelfKey) ?: continue
            val bookshelf = BlockPosition.parse(bookshelfKey)
            val lectern = BlockPosition.parse(lecternKey)
            if (bookshelf == null || lectern == null) {
                plugin.logger.warning("[LecternBookshelf] 不正なリンクデータをスキップしました: $bookshelfKey -> $lecternKey")
                continue
            }
            links[bookshelf] = lectern
        }
    }

    fun save() {
        val file = storageFile()
        file.parentFile?.mkdirs()

        val yaml = YamlConfiguration()
        for ((bookshelf, lectern) in links) {
            yaml.set("links.${bookshelf.serialize()}", lectern.serialize())
        }
        yaml.save(file)
    }

    fun link(bookshelf: Block, lectern: Block) {
        val bookshelfPos = BlockPosition.fromBlock(bookshelf)
        val lecternPos = BlockPosition.fromBlock(lectern)
        links[bookshelfPos] = lecternPos
        save()
    }

    fun linkedLecternPosition(bookshelf: Block): BlockPosition? {
        val bookshelfPos = BlockPosition.fromBlock(bookshelf)
        return links[bookshelfPos]
    }

    fun unlinkByBookshelf(bookshelf: Block): Boolean {
        val removed = links.remove(BlockPosition.fromBlock(bookshelf)) != null
        if (removed) {
            save()
        }
        return removed
    }

    fun unlinkByLectern(lectern: Block): Int {
        val lecternPos = BlockPosition.fromBlock(lectern)
        val before = links.size
        links.entries.removeIf { it.value == lecternPos }
        val removed = before - links.size
        if (removed > 0) {
            save()
        }
        return removed
    }

    private fun storageFile(): File {
        return File(plugin.dataFolder, "data/lectern_bookshelf_links.yml")
    }
}
