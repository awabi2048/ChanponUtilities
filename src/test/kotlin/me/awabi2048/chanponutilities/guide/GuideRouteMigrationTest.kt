package me.awabi2048.chanponutilities.guide

import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class GuideRouteMigrationTest {
    @Test fun `inserts old midpoint between old points and converts message rows`() {
        val yaml = YamlConfiguration()
        yaml.loadFromString("""
            routes:
              tour:
                points:
                  - {x: 0, y: 64, z: 0, yaw: 0, pitch: 0, message: [hello, world]}
                  - {x: 10, y: 64, z: 0, yaw: 0, pitch: 0}
                midpoints:
                  '1': {x: 5, y: 65, z: 1}
        """.trimIndent())
        assertTrue(GuideRouteMigration.migrate(yaml.getConfigurationSection("routes")))
        val route = yaml.getConfigurationSection("routes.tour")!!
        val points = route.getMapList("points")
        assertEquals(3, points.size)
        assertEquals(5, points[1]["x"])
        assertEquals(listOf(mapOf("text" to "hello"), mapOf("text" to "world")), points[0]["message"])
        assertFalse(route.contains("midpoints"))
        assertFalse(GuideRouteMigration.migrate(yaml.getConfigurationSection("routes")))
    }
}
