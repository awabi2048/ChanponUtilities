package me.awabi2048.chanponutilities.listener

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LecternSneakReadPolicyTest {
    @Test
    fun `default mode requires sneaking with an empty hand`() {
        assertTrue(shouldUseLecternSneakRead(isSneaking = true, handEmpty = true, requireEmptyHand = true, reverse = false))
        assertFalse(shouldUseLecternSneakRead(isSneaking = true, handEmpty = false, requireEmptyHand = true, reverse = false))
        assertFalse(shouldUseLecternSneakRead(isSneaking = false, handEmpty = true, requireEmptyHand = true, reverse = false))
    }

    @Test
    fun `reverse mode uses normal right click instead`() {
        assertTrue(shouldUseLecternSneakRead(isSneaking = false, handEmpty = true, requireEmptyHand = true, reverse = true))
        assertFalse(shouldUseLecternSneakRead(isSneaking = true, handEmpty = true, requireEmptyHand = true, reverse = true))
    }

    @Test
    fun `empty hand requirement can be disabled`() {
        assertTrue(shouldUseLecternSneakRead(isSneaking = true, handEmpty = false, requireEmptyHand = false, reverse = false))
    }
}
