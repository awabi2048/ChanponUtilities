package me.awabi2048.chanponutilities.guide

import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * ガイドアレイのコース判定・移動に関する純粋な幾何ロジックです。
 *
 * Bukkit に依存しないため単体テスト可能であり、ブロック衝突の判定だけは
 * 呼び出し側が渡す述語に委譲しています。
 */
object GuideCourseMath {

    /**
     * 点 p から線分 ab への最短距離です。
     * 「現在区間を中心とする円筒（3D カプセル）」の内外判定に使います。
     */
    fun distanceToSegment(
        px: Double, py: Double, pz: Double,
        ax: Double, ay: Double, az: Double,
        bx: Double, by: Double, bz: Double,
    ): Double {
        val abx = bx - ax
        val aby = by - ay
        val abz = bz - az
        val apx = px - ax
        val apy = py - ay
        val apz = pz - az
        val lengthSq = abx * abx + aby * aby + abz * abz
        val t = if (lengthSq <= 0.0) 0.0 else ((apx * abx + apy * aby + apz * abz) / lengthSq).coerceIn(0.0, 1.0)
        val cx = ax + abx * t - px
        val cy = ay + aby * t - py
        val cz = az + abz * t - pz
        return sqrt(cx * cx + cy * cy + cz * cz)
    }

    /** 現在区間のカプセル領域内にプレイヤーがいるかを返します。 */
    fun isOnCourse(
        px: Double, py: Double, pz: Double,
        from: GuidePoint, to: GuidePoint, radius: Double,
    ): Boolean = distanceToSegment(
        px, py, pz,
        from.x, from.y, from.z,
        to.x, to.y, to.z,
    ) <= radius

    /**
     * アレイの当たり判定（中心座標 x,z・足元 y）がブロック衝突なく置けるかを返します。
     *
     * `hasCollision` はブロック座標を受け、その位置の衝突形状がエンティティの
     * AABB と重なるかを返す述語です（Bukkit 側で Block の collisionShape を評価します）。
     */
    fun canOccupy(
        centerX: Double, feetY: Double, centerZ: Double,
        halfWidth: Double, height: Double,
        hasCollision: (blockX: Int, blockY: Int, blockZ: Int, minX: Double, minY: Double, minZ: Double, maxX: Double, maxY: Double, maxZ: Double) -> Boolean,
    ): Boolean {
        val minX = centerX - halfWidth
        val minY = feetY
        val minZ = centerZ - halfWidth
        val maxX = centerX + halfWidth
        val maxY = feetY + height
        val maxZ = centerZ + halfWidth
        // 衝突判定の対象となるブロック範囲を走査します。境界ちょうどの面接触は
        // 占有を妨げないよう、微小量だけ内側へ縮めた AABB で評価します。
        val eps = 1.0e-7
        val bMinX = floor(minX + eps).toInt()
        val bMinY = floor(minY + eps).toInt()
        val bMinZ = floor(minZ + eps).toInt()
        val bMaxX = floor(maxX - eps).toInt()
        val bMaxY = floor(maxY - eps).toInt()
        val bMaxZ = floor(maxZ - eps).toInt()
        for (bx in bMinX..bMaxX) {
            for (by in bMinY..bMaxY) {
                for (bz in bMinZ..bMaxZ) {
                    if (hasCollision(bx, by, bz, minX, minY, minZ, maxX, maxY, maxZ)) return false
                }
            }
        }
        return true
    }

    /**
     * 追従モードでアレイが滞在を試みる候補位置（プレイヤー座標基準）の列です。
     * 先頭ほど優先度が高く、占有不能な場合は順に次を試します。
     *
     * 頭上を優先し、続いて水平方向のリングを試します。
     */
    fun followAnchorCandidates(playerX: Double, playerY: Double, playerZ: Double): List<Triple<Double, Double, Double>> {
        val anchors = mutableListOf<Triple<Double, Double, Double>>()
        anchors += Triple(playerX, playerY + 2.2, playerZ)
        val ringRadii = listOf(1.6, 2.4)
        val heights = listOf(playerY + 1.6, playerY + 2.0, playerY + 1.0)
        for (radius in ringRadii) {
            for (y in heights) {
                for (step in 0 until 8) {
                    val angle = step * Math.PI / 4.0
                    anchors += Triple(
                        playerX + radius * kotlin.math.cos(angle),
                        y,
                        playerZ + radius * kotlin.math.sin(angle),
                    )
                }
            }
        }
        return anchors
    }

    /**
     * from→to を見るための yaw/pitch（度）です。Bukkit の yaw 規約
     * （0=南 +Z、90=西 -X）に合わせます。
     */
    fun lookAngles(
        fromX: Double, fromY: Double, fromZ: Double,
        toX: Double, toY: Double, toZ: Double,
    ): Pair<Float, Float> {
        val dx = toX - fromX
        val dy = toY - fromY
        val dz = toZ - fromZ
        val horizontal = sqrt(dx * dx + dz * dz)
        val yaw = Math.toDegrees(atan2(-dx, dz)).toFloat()
        val pitch = Math.toDegrees(-atan2(dy, horizontal)).toFloat()
        return yaw to pitch
    }

    /**
     * 現在位置から目標へ `speed * dt` だけ進んだ新位置を返します。
     * 到達可能なら目標座標そのものを返します。
     */
    fun stepToward(
        x: Double, y: Double, z: Double,
        tx: Double, ty: Double, tz: Double,
        distance: Double,
    ): Triple<Double, Double, Double> {
        val dx = tx - x
        val dy = ty - y
        val dz = tz - z
        val remaining = sqrt(dx * dx + dy * dy + dz * dz)
        if (remaining <= distance || remaining <= 1.0e-9) return Triple(tx, ty, tz)
        val scale = distance / remaining
        return Triple(x + dx * scale, y + dy * scale, z + dz * scale)
    }

    fun distanceSq(
        ax: Double, ay: Double, az: Double,
        bx: Double, by: Double, bz: Double,
    ): Double {
        val dx = ax - bx
        val dy = ay - by
        val dz = az - bz
        return dx * dx + dy * dy + dz * dz
    }
}
