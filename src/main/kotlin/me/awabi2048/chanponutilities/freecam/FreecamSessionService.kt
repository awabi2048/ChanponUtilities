package me.awabi2048.chanponutilities.freecam

import io.papermc.paper.datacomponent.item.ResolvableProfile
import me.awabi2048.chanponutilities.ChanponUtilitiesPlugin
import me.awabi2048.chanponutilities.util.ChanponI18n
import org.bukkit.GameMode
import org.bukkit.NamespacedKey
import org.bukkit.entity.Mannequin
import org.bukkit.entity.Player
import org.bukkit.entity.Pose
import org.bukkit.persistence.PersistentDataType
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class FreecamSessionService(private val plugin: ChanponUtilitiesPlugin) {
    private val sessions = ConcurrentHashMap<UUID, FreecamSession>()
    private val internalTeleports = ConcurrentHashMap.newKeySet<UUID>()
    private val recovery = FreecamRecoveryStore(plugin)
    private val bodyMarker = NamespacedKey(plugin, "freecam_body")
    private val bodyOwner = NamespacedKey(plugin, "freecam_owner")

    fun isActive(playerId: UUID): Boolean = sessions.containsKey(playerId)
    fun isInternalTeleport(playerId: UUID): Boolean = internalTeleports.contains(playerId)
    fun ownerOf(mannequin: Mannequin): UUID? = mannequin.persistentDataContainer
        .get(bodyOwner, PersistentDataType.STRING)?.let { runCatching { UUID.fromString(it) }.getOrNull() }

    fun start(player: Player, startMode: FreecamStartMode = FreecamStartMode.CURRENT_POSITION): Boolean {
        if (!plugin.config.getBoolean("features.freecam.enabled", true) || isActive(player.uniqueId)) return false
        if (!player.hasPermission("chanponutilities.freecam") || player.isDead || player.gameMode == GameMode.SPECTATOR || player.vehicle != null) return false

        val pose = if (plugin.server.pluginManager.isPluginEnabled("GSit")) player.pose else Pose.STANDING
        val equipment = player.inventory
        val snapshot = BodySnapshot(
            player.uniqueId, player.location.clone(), pose, player.gameMode, player.allowFlight, player.isFlying,
            EquipmentSnapshot(equipment.helmet.clone(), equipment.chestplate.clone(), equipment.leggings.clone(), equipment.boots.clone(), equipment.itemInMainHand.clone(), equipment.itemInOffHand.clone())
        )
        val mannequin = runCatching { spawnBody(player, snapshot) }.getOrElse {
            plugin.logger.warning("FreeCam body creation failed for ${player.name}: ${it.message}")
            return false
        }
        val chunk = snapshot.anchor.chunk
        snapshot.anchor.world.addPluginChunkTicket(chunk.x, chunk.z, plugin)
        recovery.save(snapshot)
        sessions[player.uniqueId] = FreecamSession(snapshot, mannequin.uniqueId, chunk.x, chunk.z)
        return try {
            player.gameMode = GameMode.SPECTATOR
            if (startMode == FreecamStartMode.PREVIOUS_CAMERA_POSITION) {
                recovery.loadPreviousCamera(player.uniqueId)?.let { location ->
                    internalTeleports.add(player.uniqueId)
                    try {
                        player.teleport(location)
                    } finally {
                        internalTeleports.remove(player.uniqueId)
                    }
                }
            }
            player.sendMessage(ChanponI18n.text(player, "freecam.started"))
            scheduleMonitor(player)
            true
        } catch (failure: Exception) {
            stop(player.uniqueId, FreecamExitReason.INTERNAL_ERROR, false)
            false
        }
    }

    fun stop(playerId: UUID, reason: FreecamExitReason, notify: Boolean = true): Boolean {
        val session = sessions.remove(playerId) ?: return false
        val player = plugin.server.getPlayer(playerId)
        val mannequin = plugin.server.getEntity(session.mannequinId) as? Mannequin
        val previousCamera = player?.takeIf { it.isOnline }?.location?.clone()
        try {
            if (player != null && player.isOnline) {
                player.teleport(session.snapshot.anchor)
                player.gameMode = session.snapshot.originalGameMode
                player.allowFlight = session.snapshot.allowFlight
                player.isFlying = session.snapshot.flying && session.snapshot.allowFlight
                player.fallDistance = 0f
                if (notify) player.sendMessage(ChanponI18n.text(player, if (reason == FreecamExitReason.BODY_DAMAGED) "freecam.body.damaged" else "freecam.stopped"))
            }
        } finally {
            mannequin?.remove()
            session.snapshot.anchor.world.removePluginChunkTicket(session.chunkX, session.chunkZ, plugin)
            recovery.remove(playerId)
            if (reason == FreecamExitReason.MANUAL && previousCamera != null) {
                recovery.savePreviousCamera(playerId, previousCamera)
            }
        }
        return true
    }

    fun recover(player: Player): Boolean {
        val entry = recovery.load(player.uniqueId) ?: return false
        player.teleport(entry.location)
        player.gameMode = entry.gameMode
        player.allowFlight = entry.allowFlight
        player.isFlying = entry.flying && entry.allowFlight
        player.fallDistance = 0f
        recovery.remove(player.uniqueId)
        player.sendMessage(ChanponI18n.text(player, "freecam.recovered"))
        return true
    }

    fun shutdown() {
        sessions.keys.toList().forEach { stop(it, FreecamExitReason.PLUGIN_DISABLE, false) }
        internalTeleports.clear()
    }

    private fun spawnBody(player: Player, snapshot: BodySnapshot): Mannequin {
        val mannequin = snapshot.anchor.world.spawn(snapshot.anchor, Mannequin::class.java)
        mannequin.profile = ResolvableProfile.resolvableProfile(player.playerProfile)
        mannequin.mainHand = player.mainHand
        mannequin.isImmovable = true
        mannequin.setGravity(false)
        mannequin.isPersistent = false
        mannequin.isSilent = true
        mannequin.isCollidable = false
        mannequin.setPose(snapshot.pose.takeIf { it in Mannequin.validPoses() } ?: Pose.STANDING, true)
        mannequin.equipment.setHelmet(snapshot.equipment.helmet)
        mannequin.equipment.setChestplate(snapshot.equipment.chestplate)
        mannequin.equipment.setLeggings(snapshot.equipment.leggings)
        mannequin.equipment.setBoots(snapshot.equipment.boots)
        mannequin.equipment.setItemInMainHand(snapshot.equipment.mainHand)
        mannequin.equipment.setItemInOffHand(snapshot.equipment.offHand)
        mannequin.persistentDataContainer.set(bodyMarker, PersistentDataType.BYTE, 1)
        mannequin.persistentDataContainer.set(bodyOwner, PersistentDataType.STRING, player.uniqueId.toString())
        return mannequin
    }

    private fun scheduleMonitor(player: Player) {
        player.scheduler.runAtFixedRate(plugin, { _ ->
            val session = sessions[player.uniqueId] ?: return@runAtFixedRate
            if (player.gameMode != GameMode.SPECTATOR) stop(player.uniqueId, FreecamExitReason.GAME_MODE_CHANGED)
            else if (plugin.server.getEntity(session.mannequinId)?.isValid != true) stop(player.uniqueId, FreecamExitReason.BODY_REMOVED)
        }, { stop(player.uniqueId, FreecamExitReason.INTERNAL_ERROR, false) }, 20L, 20L)
    }
}
