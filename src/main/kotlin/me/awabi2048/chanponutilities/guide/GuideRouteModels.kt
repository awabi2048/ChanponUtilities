package me.awabi2048.chanponutilities.guide

import org.bukkit.configuration.ConfigurationSection

/**
 * ガイドアレイの中継点です。
 *
 * 座標はアレイの待機位置（足元）であり、yaw/pitch は出現演出時の向きです。
 * message はその中継点を完了したときにプレイヤーへ送信する生テキスト行です
 * （`&` カラーコード可、空なら完了時にメッセージを送りません）。
 */
data class GuidePoint(
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Float,
    val pitch: Float,
    val message: List<String> = emptyList(),
    val delayedMessages: List<GuideMessageLine> = emptyList(),
) {
    val isStop: Boolean get() = message.isNotEmpty() || delayedMessages.isNotEmpty()
}

/** ワールド内の向かい合う頂点で表す自動開始領域です。境界を含みます。 */
data class GuideAutoStartArea(
    val worldName: String,
    val x1: Int, val y1: Int, val z1: Int,
    val x2: Int, val y2: Int, val z2: Int,
) {
    fun contains(world: String, x: Double, y: Double, z: Double): Boolean =
        world == worldName && x >= minOf(x1, x2) && x <= maxOf(x1, x2) &&
            y >= minOf(y1, y2) && y <= maxOf(y1, y2) &&
            z >= minOf(z1, z2) && z <= maxOf(z1, z2)
}

/**
 * 中継点の列と表示名を持つガイドルートです。
 *
 * 各中継点は範囲内での滞在により進行します。
 */
data class GuideRoute(
    val id: String,
    val worldName: String,
    val displayName: String?,
    val points: List<GuidePoint>,
    val autoStart: GuideAutoStartArea? = null,
) {
    /** コマンド表示・完了メッセージに使う名前です。 */
    val label: String get() = displayName ?: id
}

/**
 * `guide_routes.yml` と `GuideRoute` の相互変換です。
 *
 * ルート定義は「全件検証後に原子的に採用する」ため、ここではパース結果と
 * エラーを返すだけでファイルへの反映は行いません。
 */
object GuideRouteCodec {

    sealed interface ParseResult {
        data class Success(val routes: Map<String, GuideRoute>) : ParseResult
        data class Failure(val errors: List<String>) : ParseResult
    }

    fun parse(routesSection: ConfigurationSection?): ParseResult {
        if (routesSection == null) return ParseResult.Success(emptyMap())
        val errors = mutableListOf<String>()
        val routes = LinkedHashMap<String, GuideRoute>()
        for (id in routesSection.getKeys(false)) {
            val section = routesSection.getConfigurationSection(id)
            if (section == null) {
                errors += "routes.$id must be a map"
                continue
            }
            when (val parsed = parseRoute(id, section)) {
                is ParseResult.Success -> routes[id] = parsed.routes.values.single()
                is ParseResult.Failure -> errors += parsed.errors
            }
        }
        return if (errors.isEmpty()) ParseResult.Success(routes) else ParseResult.Failure(errors)
    }

    private fun parseRoute(id: String, section: ConfigurationSection): ParseResult {
        val errors = mutableListOf<String>()
        val world = section.getString("world")
        if (world.isNullOrBlank()) errors += "routes.$id.world must be a non-blank string"
        val displayName = section.getString("name")?.takeIf { it.isNotBlank() }

        val rawPoints = section.getMapList("points")
        if (rawPoints.isEmpty()) errors += "routes.$id.points must contain at least one waypoint"

        val points = rawPoints.mapIndexed { index, raw ->
            val path = "routes.$id.points[$index]"
            val x = raw["x"] as? Number ?: run { errors += "$path.x must be a number"; 0.0 }
            val y = raw["y"] as? Number ?: run { errors += "$path.y must be a number"; 0.0 }
            val z = raw["z"] as? Number ?: run { errors += "$path.z must be a number"; 0.0 }
            val yaw = raw["yaw"] as? Number ?: run { errors += "$path.yaw must be a number"; 0.0 }
            val pitch = raw["pitch"] as? Number ?: run { errors += "$path.pitch must be a number"; 0.0 }
            val message = when (val rawMessage = raw["message"]) {
                null -> emptyList()
                is List<*> -> rawMessage.map { line ->
                    (line as? Map<*, *>)?.let(GuideMessageLine::parse)
                        ?: run { errors += "$path.message は text と任意の非負整数 delay で指定してください"; GuideMessageLine("", null) }
                }
                else -> run { errors += "$path.message must be a list"; emptyList() }
            }
            GuidePoint(
                x.toDouble(), y.toDouble(), z.toDouble(),
                yaw.toFloat(), pitch.toFloat().coerceIn(-90f, 90f),
                message.filter { it.delay == null }.map { it.text },
                message.filter { it.delay != null },
            )
        }
        // 正の遅延は移動中だけ進むため、移動距離がない区間では配信が永久に終わりません。
        for (i in points.indices) {
            if (points[i].delayedMessages.none { (it.delay ?: 0L) > 0L }) continue
            val end = GuideStopPlan.nextStopIndex(i, points.map { it.isStop }) ?: continue
            val hasMovement = (i until end).any { n ->
                val a = points[n]; val b = points[n + 1]
                GuideCourseMath.distanceSq(a.x, a.y, a.z, b.x, b.y, b.z) > 1.0e-12
            }
            if (!hasMovement) errors += "routes.$id.points[$i] の遅延配信には移動距離が必要です"
        }
        // 自動開始は省略可能ですが、指定した場合は全頂点とワールドを厳密に検証します。
        var autoStart: GuideAutoStartArea? = null
        if (section.contains("auto_start")) {
            val area = section.getConfigurationSection("auto_start")
            val areaWorld = area?.getString("world")
            fun coordinate(corner: String, axis: String): Int? {
                val value = area?.get("$corner.$axis")
                val number = value as? Number
                if (number == null || number.toDouble() % 1.0 != 0.0 ||
                    number.toDouble() < Int.MIN_VALUE || number.toDouble() > Int.MAX_VALUE) {
                    errors += "routes.$id.auto_start.$corner.$axis は整数で指定してください"
                    return null
                }
                return number.toInt()
            }
            val x1 = coordinate("from", "x"); val y1 = coordinate("from", "y"); val z1 = coordinate("from", "z")
            val x2 = coordinate("to", "x"); val y2 = coordinate("to", "y"); val z2 = coordinate("to", "z")
            if (areaWorld.isNullOrBlank() || areaWorld != world) {
                errors += "routes.$id.auto_start.world はルートと同じワールド名を指定してください"
            } else if (x1 != null && y1 != null && z1 != null && x2 != null && y2 != null && z2 != null) {
                autoStart = GuideAutoStartArea(areaWorld, x1, y1, z1, x2, y2, z2)
            }
        }
        if (errors.isNotEmpty()) return ParseResult.Failure(errors)
        return ParseResult.Success(mapOf(id to GuideRoute(id, world!!, displayName, points, autoStart)))
    }

    /** 中継点を YAML セクションへ書き戻します。既存の message は保持されます。 */
    fun writePoint(routeSection: ConfigurationSection, index: Int, point: GuidePoint) {
        val rawPoints = routeSection.getMapList("points").toMutableList()
        val entry = linkedMapOf<String, Any>(
            "x" to point.x,
            "y" to point.y,
            "z" to point.z,
            "yaw" to point.yaw.toDouble(),
            "pitch" to point.pitch.toDouble(),
        )
        val existingMessage = rawPoints.getOrNull(index)?.get("message")
        if (existingMessage != null) entry["message"] = existingMessage
        if (index < rawPoints.size) {
            rawPoints[index] = entry
        } else {
            rawPoints.add(entry)
        }
        routeSection.set("points", rawPoints)
    }
}
