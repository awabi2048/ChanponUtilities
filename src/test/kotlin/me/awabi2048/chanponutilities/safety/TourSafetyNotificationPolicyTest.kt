package me.awabi2048.chanponutilities.safety

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TourSafetyNotificationPolicyTest {
    @Test
    fun `notice is shown initially and after cooldown`() {
        assertTrue(shouldShowTourSafetyNotice(null, 1_000L))
        assertFalse(shouldShowTourSafetyNotice(1_000L, 60_999L))
        assertTrue(shouldShowTourSafetyNotice(1_000L, 61_000L))
    }

    @Test
    fun `clock rollback permits notice`() {
        assertTrue(shouldShowTourSafetyNotice(10_000L, 9_999L))
    }
}
