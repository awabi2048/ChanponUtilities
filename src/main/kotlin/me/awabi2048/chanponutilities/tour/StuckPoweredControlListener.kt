package me.awabi2048.chanponutilities.tour

import me.awabi2048.chanponutilities.ChanponUtilitiesPlugin
import org.bukkit.GameMode
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.block.data.AnaloguePowerable
import org.bukkit.block.data.Powerable
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockRedstoneEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.inventory.EquipmentSlot
import java.util.concurrent.ConcurrentHashMap

/** 更新なしで設置され通電状態のままになった装置へ、実際の操作時に通常のtickを届けます。 */
internal class StuckPoweredControlListener(
    private val nextTick: (() -> Unit) -> Unit,
    private val later: (Long, () -> Unit) -> Unit,
    private val dispatch: (PlayerInteractEvent) -> Unit,
) : Listener {
    constructor(plugin: ChanponUtilitiesPlugin) : this(
        { action -> plugin.server.scheduler.runTask(plugin, Runnable { action() }) },
        { delay, action -> plugin.server.scheduler.runTaskLater(plugin, Runnable { action() }, delay) },
        { event -> plugin.server.pluginManager.callEvent(event) },
    )
    private val pendingPlates = ConcurrentHashMap.newKeySet<Pair<BlockPosition, java.util.UUID>>()
    private val normalButtonActivations = ConcurrentHashMap<BlockPosition, Long>()
    private val nextActivation = java.util.concurrent.atomic.AtomicLong()

    @EventHandler(priority = EventPriority.MONITOR)
    fun onRedstone(event: BlockRedstoneEvent) {
        if (!event.block.type.name.endsWith("_BUTTON")) return
        val position = BlockPosition.of(event.block)
        if (event.oldCurrent == 0 && event.newCurrent > 0) {
            val token = nextActivation.incrementAndGet()
            normalButtonActivations[position] = token
            // 通常の石・木ボタンの通電中はバニラの予定tickを優先します。
            later(40L) { normalButtonActivations.remove(position, token) }
        } else if (event.newCurrent == 0) {
            normalButtonActivations.remove(position)
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onButton(event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_BLOCK || event.hand != EquipmentSlot.HAND ||
            event.useInteractedBlock() == Event.Result.DENY) return
        val block = event.clickedBlock ?: return
        if (!block.type.name.endsWith("_BUTTON") || !isPoweredControl(block)) return
        if (normalButtonActivations.containsKey(BlockPosition.of(block))) return
        // 既に押された状態だとバニラは押下処理を省略します。先に通常tickを実行することで、
        // この操作を通常の押下として扱い、バニラ自身に解除予定を登録させます。
        block.tick()
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onMove(event: PlayerMoveEvent) {
        val player = event.player
        if (player.gameMode == GameMode.SPECTATOR || player.isDead) return
        val from = event.from
        val to = event.to
        if (from.world == to.world && from.blockX == to.blockX && from.blockY == to.blockY && from.blockZ == to.blockZ) return
        val block = to.block
        if (!block.type.name.endsWith("_PRESSURE_PLATE") || !isPoweredControl(block)) return
        val position = BlockPosition.of(block)
        val attempt = position to player.uniqueId
        if (!pendingPlates.add(attempt)) return
        nextTick action@{
            pendingPlates.remove(attempt)
            if (!player.isOnline || player.isDead || player.gameMode == GameMode.SPECTATOR ||
                BlockPosition.of(player.location.block) != position || !block.type.name.endsWith("_PRESSURE_PLATE") ||
                !isPoweredControl(block)) return@action
            // 踏み込み時に通常のPHYSICAL権限判定を通し、保護領域の操作拒否を迂回しません。
            val check = PlayerInteractEvent(player, Action.PHYSICAL, null, block, BlockFace.SELF, EquipmentSlot.HAND)
            dispatch(check)
            if (check.useInteractedBlock() != Event.Result.DENY) block.tick()
        }
    }
}

internal fun isPoweredControl(block: Block): Boolean = when (val data = block.blockData) {
    is Powerable -> data.isPowered
    is AnaloguePowerable -> data.power > 0
    else -> false
}
