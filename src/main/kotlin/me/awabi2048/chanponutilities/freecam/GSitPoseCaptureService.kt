package me.awabi2048.chanponutilities.freecam

import dev.geco.gsit.api.GSitAPI
import dev.geco.gsit.model.PoseType
import dev.geco.gsit.model.StopReason
import org.bukkit.entity.Player
import org.bukkit.entity.Pose

class GSitPoseCaptureService : FreecamPoseCaptureService {
    override fun capture(player: Player): FreecamPoseCaptureResult {
        GSitAPI.getPoseByPlayer(player)?.let { gsitPose ->
            val anchor = gsitPose.seat.location.clone()
            anchor.yaw += yawOffset(gsitPose.poseType)
            return FreecamPoseCaptureResult.Success(
                CapturedFreecamPose(anchor, toBukkitPose(gsitPose.poseType)) {
                    GSitAPI.removePose(gsitPose, StopReason.GAMEMODE_CHANGE, false)
                }
            )
        }

        GSitAPI.getSeatByEntity(player)?.let { seat ->
            if (player.vehicle != null && player.vehicle != seat.seatEntity) {
                return FreecamPoseCaptureResult.UnsupportedVehicle
            }
            return FreecamPoseCaptureResult.Success(
                CapturedFreecamPose(seat.location.clone(), Pose.STANDING, requiresSeatMount = true) {
                    GSitAPI.removeSeat(seat, StopReason.GAMEMODE_CHANGE, false)
                }
            )
        }

        GSitAPI.getCrawlByPlayer(player)?.let { crawl ->
            return FreecamPoseCaptureResult.Success(
                CapturedFreecamPose(player.location.clone(), Pose.SWIMMING) {
                    GSitAPI.stopCrawl(crawl, StopReason.GAMEMODE_CHANGE)
                }
            )
        }

        if (player.vehicle != null) return FreecamPoseCaptureResult.UnsupportedVehicle
        return FreecamPoseCaptureResult.Success(CapturedFreecamPose(player.location.clone(), player.pose))
    }

    companion object {
        internal fun toBukkitPose(type: PoseType): Pose = when (type) {
            PoseType.LAY, PoseType.LAY_BACK -> Pose.SLEEPING
            PoseType.BELLYFLOP -> Pose.SWIMMING
            PoseType.SPIN -> Pose.STANDING
        }

        internal fun yawOffset(type: PoseType): Float = if (type == PoseType.LAY_BACK) 180f else 0f
    }
}
