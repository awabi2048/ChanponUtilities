package me.awabi2048.chanponutilities.freecam

import com.awabi2048.ccsystem.api.localization.generated.ChanponUtilitiesKeys as Keys

import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import io.papermc.paper.datacomponent.item.ResolvableProfile
import me.awabi2048.chanponutilities.ChanponUtilitiesPlugin
import me.awabi2048.chanponutilities.util.ChanponI18n
import org.bukkit.GameMode
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.entity.Mannequin
import org.bukkit.entity.Player
import org.bukkit.entity.Pose
import org.bukkit.entity.ArmorStand
import org.bukkit.persistence.PersistentDataType
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class FreecamSessionService(
    private val plugin: ChanponUtilitiesPlugin,
    private val poseCaptureService: FreecamPoseCaptureService
) {
    private val sessions = ConcurrentHashMap<UUID, FreecamSession>()
    private val monitorTasks = ConcurrentHashMap<UUID, ScheduledTask>()
    private val internalTeleports = ConcurrentHashMap.newKeySet<UUID>()
    private val recovery = FreecamRecoveryStore(plugin)
    private val preferences = FreecamPreferenceStore(plugin)
    private val bodyMarker = NamespacedKey(plugin, "freecam_body")
    private val bodyOwner = NamespacedKey(plugin, "freecam_owner")

    fun isActive(playerId: UUID): Boolean = sessions.containsKey(playerId)
    fun isShortcutEnabled(playerId: UUID): Boolean = preferences.isShortcutEnabled(playerId)
    fun toggleShortcut(playerId: UUID): Boolean = preferences.toggleShortcut(playerId)
    fun resetDistance(playerId: UUID): Int = preferences.resetDistance(playerId)
    fun setResetDistance(playerId: UUID, distance: Int) = preferences.setResetDistance(playerId, distance)
    fun resetResetDistance(playerId: UUID): Int = preferences.resetResetDistance(playerId)
    fun isInternalTeleport(playerId: UUID): Boolean = internalTeleports.contains(playerId)
    fun ownerOf(mannequin: Mannequin): UUID? = mannequin.persistentDataContainer
        .get(bodyOwner, PersistentDataType.STRING)?.let { runCatching { UUID.fromString(it) }.getOrNull() }

    fun start(player: Player, startMode: FreecamStartMode = FreecamStartMode.CURRENT_POSITION): Boolean {
        if (!plugin.config.getBoolean("features.freecam.enabled", true) || isActive(player.uniqueId)) return false
        if (!player.hasPermission("chanponutilities.freecam") || player.isDead || player.gameMode == GameMode.SPECTATOR) return false
        if (FreecamStartCheckEvent(player).also(Bukkit.getPluginManager()::callEvent).isCancelled) return false

        val captured = when (val result = poseCaptureService.capture(player)) {
            is FreecamPoseCaptureResult.Success -> result.captured
            FreecamPoseCaptureResult.UnsupportedVehicle -> return false
        }
        val equipment = player.inventory
        val snapshot = BodySnapshot(
            player.uniqueId, captured.anchor, captured.pose, captured.requiresSeatMount,
            player.gameMode, player.allowFlight, player.isFlying,
            EquipmentSnapshot(equipment.helmet.clone(), equipment.chestplate.clone(), equipment.leggings.clone(), equipment.boots.clone(), equipment.itemInMainHand.clone(), equipment.itemInOffHand.clone())
        )
        val body = runCatching { spawnBody(player, snapshot) }.getOrElse {
            plugin.logger.warning("FreeCam body creation failed for ${player.name}: ${it.message}")
            return false
        }
        runCatching { captured.releaseOriginalState() }.onFailure {
            body.mannequin.remove()
            body.seatMount?.remove()
            plugin.logger.warning("FreeCam failed to release the original GSit state for ${player.name}: ${it.message}")
            return false
        }
        val chunk = snapshot.anchor.chunk
        snapshot.anchor.world.addPluginChunkTicket(chunk.x, chunk.z, plugin)
        recovery.save(snapshot)
        sessions[player.uniqueId] = FreecamSession(
            snapshot, body.mannequin.uniqueId, body.seatMount?.uniqueId, chunk.x, chunk.z
        )
        return try {
            player.gameMode = GameMode.SPECTATOR
            var previousCameraTooFar = false
            if (startMode == FreecamStartMode.PREVIOUS_CAMERA_POSITION) {
                recovery.loadPreviousCamera(player.uniqueId)?.let { location ->
                    val withinResetDistance = location.world.uid == snapshot.anchor.world.uid &&
                        location.distanceSquared(snapshot.anchor) <=
                        resetDistance(player.uniqueId).toDouble().let { it * it }
                    if (withinResetDistance) {
                        internalTeleports.add(player.uniqueId)
                        try {
                            player.teleport(location)
                        } finally {
                            internalTeleports.remove(player.uniqueId)
                        }
                    } else {
                        previousCameraTooFar = true
                    }
                }
            }
            if (previousCameraTooFar) {
                ChanponI18n.list(player, Keys.CHANPON_UTILITIES_FREECAM_PREVIOUS_CAMERA_TOO_FAR).forEach(player::sendMessage)
            }
            if (preferences.shouldShowStartMessage(player.uniqueId)) {
                ChanponI18n.list(player, Keys.CHANPON_UTILITIES_FREECAM_STARTED).forEach(player::sendMessage)
            }
            scheduleMonitor(player)
            true
        } catch (failure: Exception) {
            stop(player.uniqueId, FreecamExitReason.INTERNAL_ERROR, false)
            false
        }
    }

    fun stop(
        playerId: UUID,
        reason: FreecamExitReason,
        notify: Boolean = true,
        finalCameraPosition: org.bukkit.Location? = null
    ): Boolean {
        val session = sessions.remove(playerId) ?: run {
            monitorTasks.remove(playerId)?.cancel()
            return false
        }
        monitorTasks.remove(playerId)?.cancel()
        val player = plugin.server.getPlayer(playerId)
        val mannequin = plugin.server.getEntity(session.mannequinId) as? Mannequin
        val seatMount = session.seatMountId?.let(plugin.server::getEntity)
        val previousCamera = finalCameraPosition?.clone() ?: player?.takeIf { it.isOnline }?.location?.clone()
        try {
            if (player != null && player.isOnline) {
                player.teleport(session.snapshot.anchor)
                player.gameMode = session.snapshot.originalGameMode
                player.allowFlight = session.snapshot.allowFlight
                player.isFlying = session.snapshot.flying && session.snapshot.allowFlight
                player.fallDistance = 0f
                if (notify && reason == FreecamExitReason.BODY_DAMAGED) {
                    player.sendMessage(ChanponI18n.text(player, Keys.CHANPON_UTILITIES_FREECAM_BODY_DAMAGED))
                }
            }
        } finally {
            mannequin?.remove()
            seatMount?.remove()
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
        player.sendMessage(ChanponI18n.text(player, Keys.CHANPON_UTILITIES_FREECAM_RECOVERED))
        return true
    }

    fun shutdown() {
        sessions.keys.toList().forEach { stop(it, FreecamExitReason.PLUGIN_DISABLE, false) }
        monitorTasks.values.forEach { it.cancel() }
        monitorTasks.clear()
        internalTeleports.clear()
    }

    private fun spawnBody(player: Player, snapshot: BodySnapshot): SpawnedFreecamBody {
        val seatMount = if (snapshot.requiresSeatMount) spawnSeatMount(snapshot) else null
        var spawnedMannequin: Mannequin? = null
        return try {
            val mannequin = snapshot.anchor.world.spawn(snapshot.anchor, Mannequin::class.java)
            spawnedMannequin = mannequin
            mannequin.profile = ResolvableProfile.resolvableProfile(player.playerProfile)
            mannequin.mainHand = player.mainHand
            mannequin.isImmovable = true
            mannequin.setGravity(false)
            mannequin.isPersistent = false
            mannequin.isSilent = true
            mannequin.isCollidable = false
            if (seatMount == null) {
                mannequin.setPose(snapshot.pose.takeIf { it in Mannequin.validPoses() } ?: Pose.STANDING, true)
            } else {
                check(seatMount.addPassenger(mannequin)) { "Mannequin could not mount the FreeCam seat entity" }
            }
            mannequin.equipment.setHelmet(snapshot.equipment.helmet)
            mannequin.equipment.setChestplate(snapshot.equipment.chestplate)
            mannequin.equipment.setLeggings(snapshot.equipment.leggings)
            mannequin.equipment.setBoots(snapshot.equipment.boots)
            mannequin.equipment.setItemInMainHand(snapshot.equipment.mainHand)
            mannequin.equipment.setItemInOffHand(snapshot.equipment.offHand)
            mannequin.persistentDataContainer.set(bodyMarker, PersistentDataType.BYTE, 1)
            mannequin.persistentDataContainer.set(bodyOwner, PersistentDataType.STRING, player.uniqueId.toString())
            SpawnedFreecamBody(mannequin, seatMount)
        } catch (failure: Exception) {
            spawnedMannequin?.remove()
            seatMount?.remove()
            throw failure
        }
    }

    private fun spawnSeatMount(snapshot: BodySnapshot): ArmorStand =
        snapshot.anchor.world.spawn(snapshot.anchor, ArmorStand::class.java) { mount ->
            mount.isVisible = false
            mount.isMarker = true
            mount.isSmall = true
            mount.setGravity(false)
            mount.isInvulnerable = true
            mount.isSilent = true
            mount.isPersistent = false
            mount.isCollidable = false
            mount.persistentDataContainer.set(bodyMarker, PersistentDataType.BYTE, 1)
            mount.persistentDataContainer.set(bodyOwner, PersistentDataType.STRING, snapshot.playerId.toString())
        }

    private fun scheduleMonitor(player: Player) {
        monitorTasks.remove(player.uniqueId)?.cancel()
        val task = player.scheduler.runAtFixedRate(plugin, { _ ->
            val session = sessions[player.uniqueId] ?: return@runAtFixedRate
            if (player.gameMode != GameMode.SPECTATOR) stop(player.uniqueId, FreecamExitReason.GAME_MODE_CHANGED)
            else if (plugin.server.getEntity(session.mannequinId)?.isValid != true ||
                (session.seatMountId != null && plugin.server.getEntity(session.seatMountId)?.isValid != true)
            ) stop(player.uniqueId, FreecamExitReason.BODY_REMOVED)
        }, { stop(player.uniqueId, FreecamExitReason.INTERNAL_ERROR, false) }, 20L, 20L) ?: run {
            stop(player.uniqueId, FreecamExitReason.INTERNAL_ERROR, false)
            return
        }
        monitorTasks[player.uniqueId] = task
    }
}

private data class SpawnedFreecamBody(val mannequin: Mannequin, val seatMount: ArmorStand?)
