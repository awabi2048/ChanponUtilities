package me.awabi2048.chanponutilities.guide

import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class GuideMidpointTest {
    private fun routes(index: String): GuideRouteCodec.ParseResult {
        val yaml = YamlConfiguration()
        yaml.loadFromString("""
            routes:
              sample:
                world: world
                points:
                  - {x: 0, y: 64, z: 0, yaw: 0, pitch: 0}
                  - {x: 10, y: 64, z: 0, yaw: 0, pitch: 0}
                midpoints:
                  '$index': {x: 5, y: 65, z: 3}
        """.trimIndent())
        return try {
            GuideRouteMigration.migrate(yaml.getConfigurationSection("routes"))
            GuideRouteCodec.parse(yaml.getConfigurationSection("routes"))
        } catch (error: IllegalArgumentException) {
            GuideRouteCodec.ParseResult.Failure(listOf(error.message ?: "invalid"))
        }
    }
    @Test fun `old midpoint becomes an intermediate point`() {
        val result = routes("1") as GuideRouteCodec.ParseResult.Success
        val route = result.routes.getValue("sample")
        assertEquals(3, route.points.size)
        assertEquals(5.0, route.points[1].x)
        assertFalse(route.points[1].isStop)
    }
    @Test fun `midpoint after last waypoint is invalid`() {
        assertTrue(routes("2") is GuideRouteCodec.ParseResult.Failure)
    }
}
