package me.awabi2048.chanponutilities.freecam

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FreecamCommandSourceContractTest {
    @Test
    fun `shortcut preference stays wired and body commands are absent`() {
        val command = Files.readString(Path.of("src/main/kotlin/me/awabi2048/chanponutilities/freecam/FreecamCommand.kt"))
        val listener = Files.readString(Path.of("src/main/kotlin/me/awabi2048/chanponutilities/freecam/FreecamListener.kt"))
        val store = Files.readString(Path.of("src/main/kotlin/me/awabi2048/chanponutilities/freecam/FreecamPreferenceStore.kt"))
        val sessionService = Files.readString(Path.of("src/main/kotlin/me/awabi2048/chanponutilities/freecam/FreecamSessionService.kt"))
        val gsitCapture = Files.readString(Path.of("src/main/kotlin/me/awabi2048/chanponutilities/freecam/GSitPoseCaptureService.kt"))

        assertTrue(command.contains("toggle_shortcut"))
        assertFalse(command.contains("\"pose\""))
        assertFalse(command.contains("\"body\""))
        assertTrue(listener.contains("isShortcutEnabled"))
        assertTrue(store.contains("shortcut_enabled"))
        assertTrue(gsitCapture.contains("requiresSeatMount = true"))
        assertTrue(sessionService.contains("seatMount.addPassenger(mannequin)"))
        assertTrue(sessionService.contains("seatMount?.remove()"))
        assertFalse(command.contains("ショートカット"), "翻訳済みコマンド応答をKotlinへ直書きしないこと")
    }
}
