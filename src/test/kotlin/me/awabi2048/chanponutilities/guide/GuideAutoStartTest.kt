package me.awabi2048.chanponutilities.guide

import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class GuideAutoStartTest {
    private val first = GuideRoute("first", "world", null, listOf(GuidePoint(0.0, 64.0, 0.0, 0f, 0f)),
        autoStart = GuideAutoStartArea("world", 10, 70, 10, -10, 60, -10))
    private val second = first.copy(id = "second")
    private fun pos(x: Double, y: Double = 65.0, world: String = "world") = GuideAutoStart.Position(world, x, y, 0.0)

    @Test fun `ログイン時と領域へ入った時は設定順を優先する`() {
        assertEquals(first, GuideAutoStart.select(listOf(first, second), null, pos(0.0)))
        assertEquals(first, GuideAutoStart.select(listOf(first, second), pos(-11.0), pos(-10.0)))
    }

    @Test fun `領域内の移動や領域からの退出では再開始しない`() {
        assertNull(GuideAutoStart.select(listOf(first), pos(0.0), pos(1.0)))
        assertNull(GuideAutoStart.select(listOf(first), pos(0.0), pos(11.0)))
        assertNull(GuideAutoStart.select(listOf(first), null, pos(0.0, world = "other")))
        assertNull(GuideAutoStart.select(listOf(first), null, pos(0.0, y = 70.01)))
    }

    @Test fun `ルート設定から整数の頂点を読み込む`() {
        val yaml = YamlConfiguration()
        yaml.loadFromString("""
            routes:
              tour:
                world: world
                auto_start:
                  world: world
                  from: {x: -4, y: 60, z: 10}
                  to: {x: 10, y: 70, z: -4}
                points:
                  - {x: 0, y: 64, z: 0, yaw: 0, pitch: 0}
        """.trimIndent())
        val result = GuideRouteCodec.parse(yaml.getConfigurationSection("routes")) as GuideRouteCodec.ParseResult.Success
        assertTrue(result.routes.getValue("tour").autoStart!!.contains("world", 0.0, 65.0, 0.0))
    }

    @Test fun `小数座標や異なるワールドは拒否する`() {
        val yaml = YamlConfiguration()
        yaml.loadFromString("""
            routes:
              tour:
                world: world
                auto_start:
                  world: other
                  from: {x: 0.5, y: 60, z: 0}
                  to: {x: 10, y: 70, z: 10}
                points:
                  - {x: 0, y: 64, z: 0, yaw: 0, pitch: 0}
        """.trimIndent())
        val result = GuideRouteCodec.parse(yaml.getConfigurationSection("routes"))
        assertTrue(result is GuideRouteCodec.ParseResult.Failure)
        assertEquals(2, (result as GuideRouteCodec.ParseResult.Failure).errors.size)
    }
}
