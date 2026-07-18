package me.awabi2048.chanponutilities

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LanguageSourceContractTest {
    @Test
    fun `language keys are resolved from CC System`() {
        val source = Files.readString(Path.of("src/main/kotlin/me/awabi2048/chanponutilities/util/ChanponI18n.kt"))
        assertTrue(source.contains("getI18nString(player, \"chanpon_utilities.\$key\""))
        assertTrue(source.contains("getI18nStringList(player, \"chanpon_utilities.\$key\""))
    }

    @Test
    fun `plugin does not register a private language source`() {
        val source = Files.readString(Path.of("src/main/kotlin/me/awabi2048/chanponutilities/ChanponUtilitiesPlugin.kt"))
        assertFalse(source.contains("registerI18nSource"))
    }
}
