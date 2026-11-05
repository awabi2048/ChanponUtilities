package me.awabi2048.chanponutilities.guide

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GuideLifecycleAnimationTest {
    @Test fun `出現は1ブロック上昇し一回転して所定位置と大きさに到達する`() {
        val start = GuideLifecycleAnimation.appearing(64.0, 30f, 0)
        val middle = GuideLifecycleAnimation.appearing(64.0, 30f, 10)
        val end = GuideLifecycleAnimation.appearing(64.0, 30f, 20)
        assertEquals(63.0, start.y, 1e-9)
        assertEquals(0.0, start.scale, 1e-9)
        assertEquals(63.5, middle.y, 1e-9)
        assertEquals(210f, middle.yaw)
        assertEquals(1.0, middle.scale, 1e-9)
        assertEquals(64.0, end.y, 1e-9)
        assertEquals(390f, end.yaw)
        assertEquals(2.0, end.scale, 1e-9)
    }

    @Test fun `消滅は途中の大きさからも連続して1ブロック上昇する`() {
        val start = GuideLifecycleAnimation.disappearing(64.2, -15f, 1.2, 0)
        val middle = GuideLifecycleAnimation.disappearing(64.2, -15f, 1.2, 10)
        val end = GuideLifecycleAnimation.disappearing(64.2, -15f, 1.2, 20)
        assertEquals(1.2, start.scale, 1e-9)
        assertEquals(64.7, middle.y, 1e-9)
        assertEquals(0.6, middle.scale, 1e-9)
        assertEquals(65.2, end.y, 1e-9)
        assertEquals(345f, end.yaw)
        assertEquals(0.0, end.scale, 1e-9)
    }
}
