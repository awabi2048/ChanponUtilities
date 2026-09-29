package me.awabi2048.chanponutilities.guide

import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GuideRouteCodecTest {

    private fun yamlOf(text: String): YamlConfiguration {
        val yaml = YamlConfiguration()
        yaml.loadFromString(text)
        return yaml
    }

    private fun parse(text: String): GuideRouteCodec.ParseResult =
        GuideRouteCodec.parse(yamlOf(text).getConfigurationSection("routes"))

    @Test
    fun `parses a route with waypoints and messages`() {
        val result = parse(
            """
            routes:
              tour:
                name: "拠点ツアー"
                world: world
                points:
                  - x: 0.5
                    y: 65.0
                    z: 0.5
                    yaw: 180.0
                    pitch: 0.0
                    message:
                      - "&b開始"
                  - x: 10.0
                    y: 64.0
                    z: -5.0
                    yaw: 90.0
                    pitch: 10.0
            """.trimIndent(),
        )
        val routes = (result as GuideRouteCodec.ParseResult.Success).routes
        val route = routes.getValue("tour")
        assertEquals("拠点ツアー", route.displayName)
        assertEquals("world", route.worldName)
        assertEquals(2, route.points.size)
        assertEquals(listOf("&b開始"), route.points[0].message)
        assertEquals(180.0f, route.points[0].yaw)
        assertTrue(route.points[1].message.isEmpty())
    }

    @Test
    fun `single string message is accepted`() {
        val result = parse(
            """
            routes:
              tour:
                world: world
                points:
                  - x: 0.0
                    y: 64.0
                    z: 0.0
                    yaw: 0.0
                    pitch: 0.0
                    message: "一行メッセージ"
            """.trimIndent(),
        )
        val route = (result as GuideRouteCodec.ParseResult.Success).routes.getValue("tour")
        assertEquals(listOf("一行メッセージ"), route.points[0].message)
        assertNull(route.displayName)
        assertEquals("tour", route.label)
    }

    @Test
    fun `missing world or empty points are rejected`() {
        val noWorld = parse(
            """
            routes:
              tour:
                points:
                  - {x: 0.0, y: 64.0, z: 0.0, yaw: 0.0, pitch: 0.0}
            """.trimIndent(),
        )
        assertTrue(noWorld is GuideRouteCodec.ParseResult.Failure)

        val noPoints = parse(
            """
            routes:
              tour:
                world: world
            """.trimIndent(),
        )
        assertTrue(noPoints is GuideRouteCodec.ParseResult.Failure)
    }

    @Test
    fun `non numeric coordinate is rejected`() {
        val result = parse(
            """
            routes:
              tour:
                world: world
                points:
                  - {x: "abc", y: 64.0, z: 0.0, yaw: 0.0, pitch: 0.0}
            """.trimIndent(),
        )
        assertTrue(result is GuideRouteCodec.ParseResult.Failure)
    }

    @Test
    fun `missing routes section yields empty map`() {
        val result = GuideRouteCodec.parse(yamlOf("other: {}").getConfigurationSection("routes"))
        assertEquals(emptyMap<String, GuideRoute>(), (result as GuideRouteCodec.ParseResult.Success).routes)
    }

    @Test
    fun `write point preserves existing message`() {
        val yaml = yamlOf(
            """
            routes:
              tour:
                world: world
                points:
                  - x: 0.0
                    y: 64.0
                    z: 0.0
                    yaw: 0.0
                    pitch: 0.0
                    message:
                      - "keep me"
                  - {x: 1.0, y: 64.0, z: 1.0, yaw: 0.0, pitch: 0.0}
            """.trimIndent(),
        )
        val section = yaml.getConfigurationSection("routes.tour")!!
        GuideRouteCodec.writePoint(section, 0, GuidePoint(5.0, 70.0, 5.0, 45f, -10f))
        GuideRouteCodec.writePoint(section, 2, GuidePoint(9.0, 64.0, 9.0, 0f, 0f))

        val points = section.getMapList("points")
        assertEquals(3, points.size)
        assertEquals(5.0, (points[0]["x"] as Number).toDouble())
        assertEquals(listOf("keep me"), points[0]["message"])
        assertNull(points[2]["message"])
    }
}
