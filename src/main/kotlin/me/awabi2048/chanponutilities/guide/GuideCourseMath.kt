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
     * コース逸脱時にアレイが待機する候補位置の列です。
     *
     * 基準位置は、現在区間上のプレイヤー最近点を軸とする「地面に垂直な円筒」
     * （半径 = コース半径）の側面を、プレイヤー方向の水平角度で切り取った面上です。
     * 高さはプレイヤーと同程度に取り、方向指示が見える位置に浮かせます。
     *
     * 占有不能な場合に備えて、角度・高さ・半径のバリエーションを優先度順に
     * 続けて返します。軸上（水平成分が退化する垂直逸脱）では次の中継点側へ
     * 逃がした候補を返します。
     */
    fun boundaryAnchorCandidates(
        playerX: Double, playerY: Double, playerZ: Double,
        ax: Double, ay: Double, az: Double,
        bx: Double, by: Double, bz: Double,
        radius: Double,
    ): List<Triple<Double, Double, Double>> {
        // 区間上の最近点（3Dでクランプ済み）を円筒の軸とします。
        val abx = bx - ax
        val aby = by - ay
        val abz = bz - az
        val apx = playerX - ax
        val apy = playerY - ay
        val apz = playerZ - az
        val lengthSq = abx * abx + aby * aby + abz * abz
        val t = if (lengthSq <= 0.0) 0.0 else ((apx * abx + apy * aby + apz * abz) / lengthSq).coerceIn(0.0, 1.0)
        val centerX = ax + abx * t
        val centerZ = az + abz * t

        var dirX = playerX - centerX
        var dirZ = playerZ - centerZ
        var horizontal = sqrt(dirX * dirX + dirZ * dirZ)
        if (horizontal < 1.0e-6) {
            // プレイヤーが軸の真上/真下にいる場合は次の中継点側の角度を使います。
            dirX = bx - centerX
            dirZ = bz - centerZ
            horizontal = sqrt(dirX * dirX + dirZ * dirZ)
            if (horizontal < 1.0e-6) return emptyList()
        }
        val baseAngle = atan2(dirZ, dirX)

        val anchors = mutableListOf<Triple<Double, Double, Double>>()
        val angleOffsets = listOf(0.0, Math.toRadians(25.0), Math.toRadians(-25.0), Math.toRadians(50.0), Math.toRadians(-50.0))
        val heightOffsets = listOf(1.2, 0.5, 1.8)
        val radiusScales = listOf(1.0, 0.85)
        for (angleOffset in angleOffsets) {
            val angle = baseAngle + angleOffset
            for (radiusScale in radiusScales) {
                val r = radius * radiusScale
                for (heightOffset in heightOffsets) {
                    anchors += Triple(
                        centerX + r * kotlin.math.cos(angle),
                        playerY + heightOffset,
                        centerZ + r * kotlin.math.sin(angle),
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

    /**
     * 移動経路です。補完曲線を折れ線標本として保持し、進行距離に応じた
     * 位置を返します。
     */
    class FlightPath private constructor(
        val points: List<Triple<Double, Double, Double>>,
        private val cumulative: DoubleArray,
    ) {
        val totalLength: Double get() = cumulative[cumulative.size - 1]

        var traveled: Double = 0.0
            private set

        val finished: Boolean get() = traveled >= totalLength - 1.0e-6

        fun advance(distance: Double): Triple<Double, Double, Double> {
            traveled = (traveled + distance).coerceAtMost(totalLength)
            return pointAt(traveled)
        }

        fun pointAt(distance: Double): Triple<Double, Double, Double> {
            val d = distance.coerceIn(0.0, totalLength)
            var lo = 0
            var hi = cumulative.size - 1
            while (lo + 1 < hi) {
                val mid = (lo + hi) / 2
                if (cumulative[mid] <= d) lo = mid else hi = mid
            }
            val segment = cumulative[hi] - cumulative[lo]
            val t = if (segment <= 0.0) 0.0 else (d - cumulative[lo]) / segment
            return Triple(
                points[lo].first + (points[hi].first - points[lo].first) * t,
                points[lo].second + (points[hi].second - points[lo].second) * t,
                points[lo].third + (points[hi].third - points[lo].third) * t,
            )
        }

        companion object {
            fun of(points: List<Triple<Double, Double, Double>>): FlightPath {
                val cumulative = DoubleArray(points.size)
                var sum = 0.0
                for (i in 1 until points.size) {
                    sum += sqrt(distanceSq(
                        points[i - 1].first, points[i - 1].second, points[i - 1].third,
                        points[i].first, points[i].second, points[i].third,
                    ))
                    cumulative[i] = sum
                }
                return FlightPath(points, cumulative)
            }
        }
    }

    /**
     * 飛行経路を計画します。
     *
     * 直線距離の `deviationRatio`（誤差率）だけ中点からずらした制御点を持つ
     * 二次ベジェ曲線を標本化して返します。候補は進行方向に垂直な水平面内の
     * 左右・斜め上・真上で、標本点が不透過ブロック内に入らない最初のものを
     * 採用します。すべての曲線候補が不透過ブロックに触れる場合は直線へ
     * フォールバックします。短距離では曲線の効果が見えないため直線を返します。
     *
     * `isOpaque` はブロック座標の不透過判定述語です。
     */
    fun planFlightPath(
        fx: Double, fy: Double, fz: Double,
        tx: Double, ty: Double, tz: Double,
        deviationRatio: Double,
        isOpaque: (blockX: Int, blockY: Int, blockZ: Int) -> Boolean,
    ): FlightPath {
        val from = Triple(fx, fy, fz)
        val to = Triple(tx, ty, tz)
        val dx = tx - fx
        val dy = ty - fy
        val dz = tz - fz
        val length = sqrt(dx * dx + dy * dy + dz * dz)
        if (length < CURVE_MIN_DISTANCE) {
            return FlightPath.of(listOf(from, to))
        }

        val deviation = length * deviationRatio
        val midX = fx + dx * 0.5
        val midY = fy + dy * 0.5
        val midZ = fz + dz * 0.5

        // 候補の制御点オフセットです。水平垂直方向を優先し、上方向は
        // 距離に対して控えめな高さに留めます。
        val offsets = mutableListOf<Triple<Double, Double, Double>>()
        val horizontal = sqrt(dx * dx + dz * dz)
        if (horizontal > 1.0e-6) {
            val px = -dz / horizontal * deviation
            val pz = dx / horizontal * deviation
            offsets += Triple(px, 0.0, pz)
            offsets += Triple(-px, 0.0, -pz)
            offsets += Triple(px * 0.5, deviation * 0.5, pz * 0.5)
            offsets += Triple(-px * 0.5, deviation * 0.5, -pz * 0.5)
        }
        offsets += Triple(0.0, deviation * 0.7, 0.0)
        // 全幅が置けない場合の縮小幅フォールバックです。
        val shrunk = offsets.map { (ox, oy, oz) -> Triple(ox * 0.5, oy * 0.5, oz * 0.5) }
        offsets += shrunk

        val samples = maxOf(MIN_CURVE_SAMPLES, kotlin.math.ceil(length / 1.5).toInt())
        for ((ox, oy, oz) in offsets) {
            val points = (0..samples).map { step ->
                val t = step.toDouble() / samples
                Triple(
                    quadratic(fx, midX + ox, tx, t),
                    quadratic(fy, midY + oy, ty, t),
                    quadratic(fz, midZ + oz, tz, t),
                )
            }
            val blocked = points.any { (px, py, pz) ->
                isOpaque(floor(px).toInt(), floor(py).toInt(), floor(pz).toInt())
            }
            if (!blocked) return FlightPath.of(points)
        }
        return FlightPath.of(listOf(from, to))
    }

    private fun quadratic(p0: Double, p1: Double, p2: Double, t: Double): Double {
        val u = 1.0 - t
        return u * u * p0 + 2.0 * u * t * p1 + t * t * p2
    }

    private const val CURVE_MIN_DISTANCE = 2.0
    private const val MIN_CURVE_SAMPLES = 8
}
