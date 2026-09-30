package me.awabi2048.chanponutilities.guide

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

class GuideCourseMathTest {

    private fun point(x: Double, y: Double, z: Double) =
        GuidePoint(x, y, z, 0f, 0f, emptyList())

    @Test
    fun `distance to segment measures perpendicular distance`() {
        // 線分 (0,0,0)-(10,0,0) への点 (5,3,0) の距離は 3
        val distance = GuideCourseMath.distanceToSegment(
            5.0, 3.0, 0.0,
            0.0, 0.0, 0.0,
            10.0, 0.0, 0.0,
        )
        assertEquals(3.0, distance, 1e-9)
    }

    @Test
    fun `distance to segment clamps beyond both endpoints`() {
        // 端点の外側では端点への距離になる
        val before = GuideCourseMath.distanceToSegment(
            -3.0, 4.0, 0.0,
            0.0, 0.0, 0.0,
            10.0, 0.0, 0.0,
        )
        assertEquals(5.0, before, 1e-9)
        val after = GuideCourseMath.distanceToSegment(
            13.0, 4.0, 0.0,
            0.0, 0.0, 0.0,
            10.0, 0.0, 0.0,
        )
        assertEquals(5.0, after, 1e-9)
    }

    @Test
    fun `distance to degenerate segment is point distance`() {
        val distance = GuideCourseMath.distanceToSegment(
            3.0, 4.0, 0.0,
            0.0, 0.0, 0.0,
            0.0, 0.0, 0.0,
        )
        assertEquals(5.0, distance, 1e-9)
    }

    @Test
    fun `on course check respects 3d capsule radius including vertical axis`() {
        val from = point(0.0, 64.0, 0.0)
        val to = point(0.0, 64.0, 100.0)
        // 半径8m: 水平3m内はコース内
        assertTrue(GuideCourseMath.isOnCourse(3.0, 64.0, 50.0, from, to, 8.0))
        // 高さ方向にも適用される（真上9mは逸脱）
        assertFalse(GuideCourseMath.isOnCourse(0.0, 73.5, 50.0, from, to, 8.0))
        // 端点を超えた延長線上は逸脱
        assertFalse(GuideCourseMath.isOnCourse(0.0, 64.0, 120.0, from, to, 8.0))
        // 境界ちょうどはコース内
        assertTrue(GuideCourseMath.isOnCourse(8.0, 64.0, 50.0, from, to, 8.0))
    }

    @Test
    fun `step toward advances by step distance and clamps at target`() {
        val (x, y, z) = GuideCourseMath.stepToward(0.0, 0.0, 0.0, 10.0, 0.0, 0.0, 4.0)
        assertEquals(4.0, x, 1e-9)
        assertEquals(0.0, y, 1e-9)
        assertEquals(0.0, z, 1e-9)

        val arrived = GuideCourseMath.stepToward(9.0, 0.0, 0.0, 10.0, 0.0, 0.0, 4.0)
        assertEquals(10.0, arrived.first, 1e-9)
    }

    @Test
    fun `look angles follow bukkit yaw convention`() {
        // +Z(南)を向くと yaw=0、-X(西)を向くと yaw=90
        val (yawSouth, _) = GuideCourseMath.lookAngles(0.0, 0.0, 0.0, 0.0, 0.0, 10.0)
        assertTrue(abs(yawSouth) < 0.01f, "south yaw should be ~0, got $yawSouth")
        val (yawWest, _) = GuideCourseMath.lookAngles(0.0, 0.0, 0.0, -10.0, 0.0, 0.0)
        assertTrue(abs(yawWest - 90f) < 0.01f, "west yaw should be ~90, got $yawWest")
        // 上方向を向くと pitch は負
        val (_, pitchUp) = GuideCourseMath.lookAngles(0.0, 0.0, 0.0, 0.0, 10.0, 0.0)
        assertTrue(pitchUp < -80f, "up pitch should be negative, got $pitchUp")
    }

    @Test
    fun `occupancy fails when a solid block overlaps the box`() {
        // (0,0,0) のブロックだけ衝突を持つ世界
        val solid = setOf(Triple(0, 0, 0))
        val hasCollision = { bx: Int, by: Int, bz: Int, _: Double, _: Double, _: Double, _: Double, _: Double, _: Double ->
            Triple(bx, by, bz) in solid
        }
        // アレイの箱 (中心0.5, 足元0.0, 中心0.5, 半幅0.175, 高さ0.6) は (0,0,0) と重なる
        assertFalse(
            GuideCourseMath.canOccupy(0.5, 0.0, 0.5, 0.175, 0.6, hasCollision),
        )
        // 十分離れた位置は占有可能
        assertTrue(
            GuideCourseMath.canOccupy(5.5, 0.0, 5.5, 0.175, 0.6, hasCollision),
        )
    }

    @Test
    fun `boundary anchor sits on the vertical cylinder facing the player`() {
        // 区間 (0,0,0)-(0,0,100) 、半径8。プレイヤーは (20,65,50) で +X 側へ逸脱中。
        // 最近点は (0,0,50)、面上はプレイヤー方向（+X）へ半径8の (8,y,50) です。
        val anchors = GuideCourseMath.boundaryAnchorCandidates(
            20.0, 65.0, 50.0,
            0.0, 64.0, 0.0,
            0.0, 64.0, 100.0,
            8.0,
        )
        assertTrue(anchors.isNotEmpty())
        val (x, y, z) = anchors.first()
        assertEquals(8.0, x, 1e-9)
        assertEquals(50.0, z, 1e-9)
        assertEquals(65.0 + 1.2, y, 1e-9)
    }

    @Test
    fun `boundary anchor falls back toward next waypoint on vertical deviation`() {
        // 区間軸の真上にいる場合、水平方向が退化するため次の中継点（+Z）側へ出します。
        val anchors = GuideCourseMath.boundaryAnchorCandidates(
            0.0, 80.0, 50.0,
            0.0, 64.0, 0.0,
            0.0, 64.0, 100.0,
            8.0,
        )
        assertTrue(anchors.isNotEmpty())
        val (x, _, z) = anchors.first()
        assertEquals(0.0, x, 1e-9)
        assertEquals(58.0, z, 1e-9)
    }

    @Test
    fun `occupancy ignores mere face contact`() {
        // (1,0,0) が衝突ブロック。アレイ箱の右面 (x=0.675) がブロック面 x=1.0 に
        // 触れるだけの位置は占有可能として扱います。
        val solid = setOf(Triple(1, 0, 0))
        val hasCollision = { bx: Int, by: Int, bz: Int, _: Double, _: Double, _: Double, _: Double, _: Double, _: Double ->
            Triple(bx, by, bz) in solid
        }
        // 中心 x=0.5 → maxX=0.675 < 1.0 で重ならない
        assertTrue(GuideCourseMath.canOccupy(0.5, 0.0, 0.5, 0.175, 0.6, hasCollision))
    }
}
