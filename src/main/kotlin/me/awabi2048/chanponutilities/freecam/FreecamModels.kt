package me.awabi2048.chanponutilities.freecam

import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.entity.Pose
import org.bukkit.inventory.ItemStack
import java.util.UUID

data class EquipmentSnapshot(
    val helmet: ItemStack?,
    val chestplate: ItemStack?,
    val leggings: ItemStack?,
    val boots: ItemStack?,
    val mainHand: ItemStack,
    val offHand: ItemStack
)

data class BodySnapshot(
    val playerId: UUID,
    val anchor: Location,
    val pose: Pose,
    val originalGameMode: GameMode,
    val allowFlight: Boolean,
    val flying: Boolean,
    val equipment: EquipmentSnapshot
)

data class FreecamSession(
    val snapshot: BodySnapshot,
    val mannequinId: UUID,
    val chunkX: Int,
    val chunkZ: Int
)

enum class FreecamStartMode {
    CURRENT_POSITION,
    PREVIOUS_CAMERA_POSITION
}

enum class FreecamExitReason {
    MANUAL, BODY_DAMAGED, BODY_REMOVED, PLAYER_QUIT, PLAYER_KICK,
    GAME_MODE_CHANGED, PLUGIN_DISABLE, RECOVERY, INTERNAL_ERROR
}
