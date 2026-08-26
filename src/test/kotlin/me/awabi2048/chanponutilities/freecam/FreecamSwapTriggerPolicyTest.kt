package me.awabi2048.chanponutilities.freecam

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FreecamSwapTriggerPolicyTest {
    @Test
    fun `claimed input hands over to CC-System before any other check`() {
        assertEquals(
            FreecamSwapDisposition.HAND_OVER,
            resolveFreecamSwapDisposition(
                inputClaimed = true,
                sneaking = true,
                shortcutAvailable = true,
                mainHandEmpty = true,
                offHandEmpty = true,
            ),
        )
    }

    @Test
    fun `sneaking resets double press tracking`() {
        assertEquals(
            FreecamSwapDisposition.SNEAKING_RESET,
            resolveFreecamSwapDisposition(
                inputClaimed = false,
                sneaking = true,
                shortcutAvailable = true,
                mainHandEmpty = true,
                offHandEmpty = true,
            ),
        )
    }

    @Test
    fun `unavailable shortcut is ignored without cancel or tracking`() {
        assertEquals(
            FreecamSwapDisposition.IGNORE,
            resolveFreecamSwapDisposition(
                inputClaimed = false,
                sneaking = false,
                shortcutAvailable = false,
                mainHandEmpty = true,
                offHandEmpty = true,
            ),
        )
    }

    @Test
    fun `item in either hand keeps vanilla swap hand behavior`() {
        val base = { mainHandEmpty: Boolean, offHandEmpty: Boolean ->
            resolveFreecamSwapDisposition(
                inputClaimed = false,
                sneaking = false,
                shortcutAvailable = true,
                mainHandEmpty = mainHandEmpty,
                offHandEmpty = offHandEmpty,
            )
        }
        assertEquals(FreecamSwapDisposition.NOT_EMPTY_HANDED, base(false, false))
        assertEquals(FreecamSwapDisposition.NOT_EMPTY_HANDED, base(false, true))
        assertEquals(FreecamSwapDisposition.NOT_EMPTY_HANDED, base(true, false))
    }

    @Test
    fun `empty both hands tracks the press for double press detection`() {
        assertEquals(
            FreecamSwapDisposition.TRACK_PRESS,
            resolveFreecamSwapDisposition(
                inputClaimed = false,
                sneaking = false,
                shortcutAvailable = true,
                mainHandEmpty = true,
                offHandEmpty = true,
            ),
        )
    }
}
