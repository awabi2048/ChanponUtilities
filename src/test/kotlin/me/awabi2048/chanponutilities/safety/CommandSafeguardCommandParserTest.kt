package me.awabi2048.chanponutilities.safety

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CommandSafeguardCommandParserTest {
    @Test
    fun `kill selector with and without brackets belongs to the same command family`() {
        assertEquals(
            listOf("@e"),
            CommandSafeguardCommandParser.parse("/kill @e")?.selectors,
        )
        assertEquals(
            listOf("@e[]"),
            CommandSafeguardCommandParser.parse("/minecraft:kill @e[]")?.selectors,
        )
    }

    @Test
    fun `selector conditions containing spaces remain one argument`() {
        val parsed = CommandSafeguardCommandParser.parse(
            "/kill @e[nbt={CustomName:\"wide target\"}]",
        )

        assertEquals(listOf("@e[nbt={CustomName:\"wide target\"}]"), parsed?.selectors)
    }

    @Test
    fun `effect and item use their command-specific target positions`() {
        assertEquals(
            listOf("@a[gamemode=survival]"),
            CommandSafeguardCommandParser.parse("/effect give @a[gamemode=survival] speed 10")?.selectors,
        )
        assertEquals(
            listOf("@e[type=item]"),
            CommandSafeguardCommandParser.parse("/item replace entity @e[type=item] weapon.mainhand with minecraft:stone")?.selectors,
        )
    }

    @Test
    fun `selector-like text in unrelated arguments is not treated as a target`() {
        assertNull(CommandSafeguardCommandParser.parse("/scoreboard objectives add @e dummy"))
        assertNull(CommandSafeguardCommandParser.parse("/minecraft:custom_command @e"))
    }

    @Test
    fun `execute conservatively includes selectors from nested execution`() {
        assertEquals(
            listOf("@a", "@s", "@e"),
            CommandSafeguardCommandParser.parse("/execute as @a at @s run kill @e")?.selectors,
        )
    }
}
