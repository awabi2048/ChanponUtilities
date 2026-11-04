package me.awabi2048.chanponutilities.guide

import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class GuideAutoStartPollerTest {
    private val route = GuideRoute("tour", "world", null, listOf(GuidePoint(0.0, 64.0, 0.0, 0f, 0f)),
        autoStart = GuideAutoStartArea("world", 0, 60, 0, 10, 70, 10))
    private fun position(x: Double) = GuideAutoStart.Position("world", x, 65.0, 5.0)

    @Test fun `外側から内側への定期判定で一度だけ開始する`() {
        val poller = GuideAutoStartPoller(); val id = UUID.randomUUID()
        poller.record(id, position(-1.0))
        assertEquals(route, poller.poll(id, position(1.0), listOf(route), false))
        assertNull(poller.poll(id, position(2.0), listOf(route), false))
        assertNull(poller.poll(id, position(11.0), listOf(route), false))
        assertEquals(route, poller.poll(id, position(10.0), listOf(route), false))
    }

    @Test fun `ガイド中にも位置を記録し終了後の再開始を防ぐ`() {
        val poller = GuideAutoStartPoller(); val id = UUID.randomUUID()
        poller.record(id, position(-1.0))
        assertNull(poller.poll(id, position(1.0), listOf(route), true))
        assertNull(poller.poll(id, position(2.0), listOf(route), false))
        poller.poll(id, position(-1.0), listOf(route), false)
        assertEquals(route, poller.poll(id, position(1.0), listOf(route), false))
    }

    @Test fun `領域を判定間隔中に通過した場合は開始しない`() {
        val poller = GuideAutoStartPoller(); val id = UUID.randomUUID()
        poller.record(id, position(-1.0))
        assertNull(poller.poll(id, position(11.0), listOf(route), false))
    }

    @Test fun `終了時に前回位置を破棄する`() {
        val poller = GuideAutoStartPoller(); val id = UUID.randomUUID()
        poller.record(id, position(-1.0))
        poller.forget(id)
        assertNull(poller.poll(id, position(1.0), listOf(route), false))
    }
}
