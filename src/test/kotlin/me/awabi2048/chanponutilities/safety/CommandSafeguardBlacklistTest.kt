package me.awabi2048.chanponutilities.safety

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CommandSafeguardBlacklistTest {
    @Test
    fun configuredCommandsAreMatchedAsCompleteTrimmedStrings() {
        val blacklist = setOf("/kill @e[]", "/kill @e")

        assertTrue(" /kill @e[] ".trim() in blacklist)
        assertTrue("/kill @e" in blacklist)
    }

    @Test
    fun unconfiguredCommandVariantsAreNotDynamicallyResolved() {
        val blacklist = setOf("/kill @e[]", "/kill @e")

        assertFalse("/kill @e[type=item]" in blacklist)
        assertFalse("/minecraft:kill @e" in blacklist)
        assertFalse("/execute as @e run kill @s" in blacklist)
    }
}
