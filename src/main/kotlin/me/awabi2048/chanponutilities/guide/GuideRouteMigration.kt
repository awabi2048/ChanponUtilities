package me.awabi2048.chanponutilities.guide

import org.bukkit.configuration.ConfigurationSection

/** 旧 midpoints と文字列メッセージを、新しい points と text 形式へ変換します。 */
internal object GuideRouteMigration {
    fun migrate(routes: ConfigurationSection?): Boolean {
        if (routes == null) return false
        var changed = false
        for (id in routes.getKeys(false)) {
            val route = routes.getConfigurationSection(id) ?: continue
            val rawList = route.getList("points")
            require(rawList == null || rawList.all { it is Map<*, *> }) { "routes.$id.points は地点の一覧で指定してください" }
            val points = route.getMapList("points").map { raw ->
                val copy = raw.entries.associate { it.key.toString() to it.value }.toMutableMap()
                when (val message = copy["message"]) {
                    is String -> { copy["message"] = listOf(mapOf("text" to message)); changed = true }
                    is List<*> -> if (message.any { it is String }) {
                        require(message.all { it is String }) { "routes.$id.points.message の旧形式が不正です" }
                        copy["message"] = message.map { mapOf("text" to it) }
                        changed = true
                    }
                }
                copy
            }
            val midpoints = route.getConfigurationSection("midpoints")
            require(!route.contains("midpoints") || midpoints != null) { "routes.$id.midpoints が不正です" }
            if (midpoints != null) {
                val byIndex = midpoints.getKeys(false).associate { key ->
                    val index = key.toIntOrNull()
                    require(index != null && index in 1 until points.size) { "routes.$id.midpoints.$key が不正です" }
                    val node = midpoints.getConfigurationSection(key)
                    require(node != null) { "routes.$id.midpoints.$key が不正です" }
                    val x = node.get("x") as? Number
                    val y = node.get("y") as? Number
                    val z = node.get("z") as? Number
                    require(x != null && y != null && z != null && x.toDouble().isFinite() && y.toDouble().isFinite() && z.toDouble().isFinite()) {
                        "routes.$id.midpoints.$key の座標が不正です"
                    }
                    index to mapOf("x" to x, "y" to y, "z" to z, "yaw" to 0.0, "pitch" to 0.0)
                }
                val combined = mutableListOf<Map<String, Any?>>()
                points.forEachIndexed { i, point -> combined.add(point); byIndex[i + 1]?.let(combined::add) }
                route.set("points", combined)
                route.set("midpoints", null)
                changed = true
            } else {
                route.set("points", points)
            }
        }
        return changed
    }
}
