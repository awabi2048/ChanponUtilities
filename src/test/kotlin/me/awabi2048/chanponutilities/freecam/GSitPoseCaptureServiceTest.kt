package me.awabi2048.chanponutilities.freecam

import dev.geco.gsit.model.PoseType
import org.bukkit.entity.Pose
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GSitPoseCaptureServiceTest {
    @Test
    fun `GSit poses map to mannequin poses`() {
        assertEquals(Pose.SLEEPING, GSitPoseCaptureService.toBukkitPose(PoseType.LAY))
        assertEquals(Pose.SLEEPING, GSitPoseCaptureService.toBukkitPose(PoseType.LAY_BACK))
        assertEquals(Pose.SWIMMING, GSitPoseCaptureService.toBukkitPose(PoseType.BELLYFLOP))
        assertEquals(Pose.STANDING, GSitPoseCaptureService.toBukkitPose(PoseType.SPIN))
        assertEquals(180f, GSitPoseCaptureService.yawOffset(PoseType.LAY_BACK))
        assertEquals(0f, GSitPoseCaptureService.yawOffset(PoseType.LAY))
    }
}
