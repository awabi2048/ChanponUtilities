package me.awabi2048.chanponutilities.guide

import org.bukkit.configuration.ConfigurationSection

/**
 * ガイドアレイの中継点です。
 *
 * 座標はアレイの待機位置（足元）であり、yaw/pitch は待機中にアレイが向く方角です。
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
)

/**
 * 中継点の列と表示名を持つガイドルートです。
 *
 * 先頭の中継点はクリックで完了し、以降は範囲内での滞在により進行します。
 */
data class GuideRoute(
    val id: String,
    val worldName: String,
    val displayName: String?,
    val points: List<GuidePoint>,
    val midpoints: Map<Int, GuidePoint> = emptyMap(),
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
                is String -> listOf(rawMessage)
                is List<*> -> rawMessage.map { line ->
                    line as? String ?: run { errors += "$path.message must contain only strings"; "" }
                }
                else -> run { errors += "$path.message must be a string or list of strings"; emptyList() }
            }
            GuidePoint(
                x.toDouble(), y.toDouble(), z.toDouble(),
                yaw.toFloat(), pitch.toFloat().coerceIn(-90f, 90f),
                message,
            )
        }
        // キー 3.5 は3番と4番の中継点の間の通過点を表します。
        val midpoints = linkedMapOf<Int, GuidePoint>()
        section.getConfigurationSection("midpoints")?.let { mid ->
            for (key in mid.getKeys(false)) {
                val index = key.toIntOrNull()
                val node = mid.getConfigurationSection(key)
                val x = node?.get("x") as? Number
                val y = node?.get("y") as? Number
                val z = node?.get("z") as? Number
                if (index == null || index >= points.size || x == null || y == null || z == null ||
                    !x.toDouble().isFinite() || !y.toDouble().isFinite() || !z.toDouble().isFinite()) {
                    errors += "routes.$id.midpoints.$key は隣接する中継点間の有限座標で指定してください"
                } else {
                    midpoints[index] = GuidePoint(x.toDouble(), y.toDouble(), z.toDouble(), 0f, 0f)
                }
            }
        }
        if (errors.isNotEmpty()) return ParseResult.Failure(errors)
        return ParseResult.Success(mapOf(id to GuideRoute(id, world!!, displayName, points, midpoints)))
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
