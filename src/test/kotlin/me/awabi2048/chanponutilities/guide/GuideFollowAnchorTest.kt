package me.awabi2048.chanponutilities.guide

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.math.hypot

class GuideFollowAnchorTest {
    @Test fun `候補はプレイヤーから水平2ブロックで上下移動を追従する`() {
        val before = GuideFollowAnchor.candidates(10.0, 64.0, 20.0, 20.0, 20.0)
        val after = GuideFollowAnchor.candidates(10.0, 70.0, 20.0, 20.0, 20.0)
        assertEquals(before.size, after.size)
        before.zip(after).forEach { (a, b) ->
            assertEquals(2.0, hypot(a.first - 10.0, a.third - 20.0), 1e-9)
            assertEquals(65.0, a.second, 1e-9)
            assertEquals(71.0, b.second, 1e-9)
            assertEquals(a.first, b.first, 1e-9)
            assertEquals(a.third, b.third, 1e-9)
        }
    }
}
