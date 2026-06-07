package me.awabi2048.chanponutilities.listener

import me.awabi2048.chanponutilities.ChanponUtilitiesPlugin
import me.awabi2048.chanponutilities.effect.BlockFrameEffectRenderer
import me.awabi2048.chanponutilities.service.LecternBookshelfLinkStore
import me.awabi2048.chanponutilities.util.ChanponI18n
import org.bukkit.Color
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.Lectern
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BookMeta


class LecternBookshelfLinkListener(
    private val plugin: ChanponUtilitiesPlugin,
    private val linkStore: LecternBookshelfLinkStore
) : Listener {
    private companion object {
        const val PERMISSION_LINK = "chanponutilities.link"
        const val PERMISSION_READ = "chanponutilities.read"
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBookshelfPlace(event: BlockPlaceEvent) {
        if (!isFeatureEnabled()) {
            return
        }
        val placed = event.blockPlaced
        if (placed.type != Material.BOOKSHELF) {
            return
        }
        if (!event.player.hasPermission(PERMISSION_LINK)) {
            return
        }

        val against = event.blockAgainst
        if (against.type != Material.LECTERN) {
            return
        }
        linkStore.link(placed, against)
        playLinkSuccessEffects(placed, against)
    }

    @EventHandler(ignoreCancelled = true)
    fun onBookshelfInteract(event: PlayerInteractEvent) {
        if (!isFeatureEnabled()) {
            return
        }
        if (event.action != Action.RIGHT_CLICK_BLOCK) {
            return
        }
        if (event.hand != EquipmentSlot.HAND) {
            return
        }

        val clicked = event.clickedBlock ?: return
        if (clicked.type != Material.BOOKSHELF) {
            return
        }
        if (!event.player.hasPermission(PERMISSION_READ)) {
            return
        }

        val lecternPosition = linkStore.linkedLecternPosition(clicked) ?: return

        if (event.player.isSneaking) {
            linkStore.unlinkByBookshelf(clicked)
            event.player.playSound(event.player.location, "minecraft:block.wooden_button.click_off", 1.0f, 1.0f)
            event.player.sendMessage(ChanponI18n.text(event.player, "unlink.success"))
            event.isCancelled = true
            return
        }

        val lecternBlock = lecternPosition.resolveBlock()
        if (lecternBlock == null || lecternBlock.type != Material.LECTERN) {
            linkStore.unlinkByBookshelf(clicked)
            return
        }

        val lecternState = lecternBlock.state as? Lectern ?: return
        val rawBook = lecternState.inventory.getItem(0)?.takeUnless { it.type.isAir } ?: return
        val bookToOpen = when (rawBook.type) {
            Material.WRITTEN_BOOK -> rawBook.clone()
            Material.WRITABLE_BOOK -> {
                val meta = rawBook.itemMeta as? BookMeta ?: return
                val pages = meta.pages ?: return
                val written = ItemStack(Material.WRITTEN_BOOK)
                val writtenMeta = written.itemMeta as? BookMeta ?: return
                writtenMeta.title = "\uD83D\uDCDD"
                writtenMeta.author = event.player.name
                writtenMeta.pages = pages
                written.itemMeta = writtenMeta
                written
            }
            else -> return
        }
        event.player.openBook(bookToOpen)
        event.player.playSound(event.player.location, "minecraft:item.book.page_turn", 1.0f, 1.0f)
        event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        if (!isFeatureEnabled()) {
            return
        }
        when (event.block.type) {
            Material.BOOKSHELF -> linkStore.unlinkByBookshelf(event.block)
            Material.LECTERN -> linkStore.unlinkByLectern(event.block)
            else -> Unit
        }
    }

    private fun isFeatureEnabled(): Boolean {
        return plugin.config.getBoolean("features.lectern_bookshelf_link.enabled", true)
    }

    private fun playLinkSuccessEffects(bookshelfBlock: Block, lecternBlock: Block) {
        if (!plugin.config.getBoolean("effects.link_success.enabled", true)) {
            return
        }

        if (plugin.config.getBoolean("effects.link_success.particle.enabled", true)) {
            val points = plugin.config.getInt("effects.link_success.particle.points_per_edge", 6).coerceAtLeast(1)
            val size = plugin.config.getDouble("effects.link_success.particle.size", 1.1).toFloat().coerceAtLeast(0.1f)
            val lecternColor = colorFromConfig("effects.link_success.particle.lectern_color", Color.LIME)
            val bookshelfColor = colorFromConfig("effects.link_success.particle.bookshelf_color", Color.YELLOW)

            BlockFrameEffectRenderer.render(lecternBlock.world, lecternBlock, lecternColor, size, points)
            BlockFrameEffectRenderer.render(bookshelfBlock.world, bookshelfBlock, bookshelfColor, size, points)
        }

        if (plugin.config.getBoolean("effects.link_success.sound.enabled", true)) {
            val soundKey = plugin.config.getString("effects.link_success.sound.key", "minecraft:entity.experience_orb.pickup")
                ?: "minecraft:entity.experience_orb.pickup"
            val volume = plugin.config.getDouble("effects.link_success.sound.volume", 1.0).toFloat().coerceAtLeast(0f)
            val pitch = plugin.config.getDouble("effects.link_success.sound.pitch", 2.0).toFloat().coerceAtLeast(0f)
            val center = bookshelfBlock.location.toCenterLocation()
            bookshelfBlock.world.players.forEach { player ->
                player.playSound(center, soundKey, volume, pitch)
            }
        }
    }

    private fun colorFromConfig(path: String, fallback: Color): Color {
        val r = plugin.config.getInt("$path.r", fallback.red).coerceIn(0, 255)
        val g = plugin.config.getInt("$path.g", fallback.green).coerceIn(0, 255)
        val b = plugin.config.getInt("$path.b", fallback.blue).coerceIn(0, 255)
        return Color.fromRGB(r, g, b)
    }
}
