package me.awabi2048.chanponutilities.freecam

import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.entity.Pose

data class CapturedFreecamPose(
    val anchor: Location,
    val pose: Pose,
    val requiresSeatMount: Boolean = false,
    val releaseOriginalState: () -> Unit = {}
)

sealed interface FreecamPoseCaptureResult {
    data class Success(val captured: CapturedFreecamPose) : FreecamPoseCaptureResult
    data object UnsupportedVehicle : FreecamPoseCaptureResult
}

fun interface FreecamPoseCaptureService {
    fun capture(player: Player): FreecamPoseCaptureResult
}

class StandingPoseCaptureService : FreecamPoseCaptureService {
    override fun capture(player: Player): FreecamPoseCaptureResult {
        if (player.vehicle != null) return FreecamPoseCaptureResult.UnsupportedVehicle
        return FreecamPoseCaptureResult.Success(CapturedFreecamPose(player.location.clone(), Pose.STANDING))
    }
}
