package me.awabi2048.chanponutilities.safety

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.MenuActionResult
import com.awabi2048.ccsystem.api.gui.MenuDialogButton
import com.awabi2048.ccsystem.api.gui.MenuDialogHandler
import com.awabi2048.ccsystem.api.gui.MenuDialogRequest
import com.awabi2048.ccsystem.api.gui.MenuFormButton
import com.awabi2048.ccsystem.api.gui.MenuFormHandler
import com.awabi2048.ccsystem.api.gui.MenuFormResponse
import com.awabi2048.ccsystem.api.gui.MenuFormService
import com.awabi2048.ccsystem.api.gui.MenuSimpleFormRequest
import com.awabi2048.ccsystem.api.gui.MenuUpdate
import com.awabi2048.ccsystem.api.localization.LocalizationKey
import com.awabi2048.ccsystem.api.localization.generated.ChanponUtilitiesKeys as Keys
import me.awabi2048.chanponutilities.ChanponUtilitiesPlugin
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitTask
import java.util.UUID

/**
 * コマンドセーフガードの状態機械、UI表示、再実行、確認中の音声を一括管理します。
 *
 * PlayerCommandPreprocessEventはプレイヤーの権限判定より前に到達するため、ここでは
 * permissionを一切確認しません。ただし確認後のコマンドは元のプレイヤーを送信者として
 * 実行するため、セーフガードがコマンド自体の権限を付与することはありません。
 */
internal class CommandSafeguardService(
    private val plugin: ChanponUtilitiesPlugin,
) {
    private val pendingByPlayer = mutableMapOf<UUID, PendingConfirmation>()
    private val soundTasksByPlayer = mutableMapOf<UUID, BukkitTask>()
    private val approvedCommandsByPlayer = mutableMapOf<UUID, String>()
    private val blacklistedCommands = plugin.config
        .getStringList(BLACKLIST_CONFIG_PATH)
        .map(String::trim)
        .filter(String::isNotEmpty)
        .toSet()

    /**
     * コマンドを捕捉し、確認対象ならtrueを返します。
     * trueの場合、呼び出し側は元のPlayerCommandPreprocessEventをキャンセルします。
     */
    fun intercept(player: Player, message: String): Boolean {
        check(Bukkit.isPrimaryThread()) { "Command safeguard must run on the Bukkit main thread" }

        val normalizedMessage = message.trim()
        val approved = approvedCommandsByPlayer.remove(player.uniqueId)
        if (approved == normalizedMessage) {
            // 2回目の確認からの内部再実行だけは、同じセーフガードを再度開かないようにします。
            return false
        }

        // 確認中の連打は新しい状態を作らず、入力されたコマンドも実行しません。
        if (pendingByPlayer.containsKey(player.uniqueId)) return true

        // コマンドの意味解析やセレクター解決は行わず、管理者が設定した完全一致の
        // 文字列だけを保護します。別表記を保護したい場合は設定へ明示的に追加します。
        if (normalizedMessage !in blacklistedCommands) return false

        val confirmation = PendingConfirmation(
            command = normalizedMessage,
            stage = ConfirmationStage.FIRST,
        )
        pendingByPlayer[player.uniqueId] = confirmation
        startConfirmationSound(player)

        if (!showConfirmation(player, confirmation)) {
            cancel(player)
            player.sendMessage(component(player, Keys.CHANPON_UTILITIES_COMMAND_SAFEGUARD_UI_UNAVAILABLE))
        }
        return true
    }

    fun cancel(player: Player) {
        pendingByPlayer.remove(player.uniqueId)
        approvedCommandsByPlayer.remove(player.uniqueId)
        soundTasksByPlayer.remove(player.uniqueId)?.cancel()
    }

    fun shutdown() {
        soundTasksByPlayer.values.forEach(BukkitTask::cancel)
        pendingByPlayer.clear()
        soundTasksByPlayer.clear()
        approvedCommandsByPlayer.clear()
    }

    private fun showConfirmation(player: Player, confirmation: PendingConfirmation): Boolean {
        val formService = CCSystem.getAPI().getMenuFormService()
        val isBedrock = runCatching { formService.isAvailable(player) }.getOrDefault(false)
        return if (isBedrock) {
            showForm(player, confirmation, formService)
        } else {
            showDialog(player, confirmation)
        }
    }

    private fun showDialog(player: Player, confirmation: PendingConfirmation): Boolean {
        val executeButton = MenuDialogButton(
            component(player, Keys.CHANPON_UTILITIES_COMMAND_SAFEGUARD_CONFIRM),
            MenuDialogHandler { target, _ -> handleAction(target, confirmation.stage, SafeguardAction.EXECUTE) },
        )
        val cancelButton = MenuDialogButton(
            component(player, Keys.CHANPON_UTILITIES_COMMAND_SAFEGUARD_CANCEL),
            MenuDialogHandler { target, _ -> handleAction(target, confirmation.stage, SafeguardAction.CANCEL) },
        )

        // MenuDialogServiceはconfirmを先、cancelを後にConfirmationへ渡します。
        // 2回目だけ意味上のボタンを反転して渡し、画面上の配置を入れ替えます。
        val request = MenuDialogRequest(
            owner = OWNER,
            id = confirmation.stage.dialogId,
            title = component(player, Keys.CHANPON_UTILITIES_COMMAND_SAFEGUARD_TITLE),
            body = dialogBody(player, confirmation),
            confirm = if (confirmation.stage == ConfirmationStage.FIRST) executeButton else cancelButton,
            cancel = if (confirmation.stage == ConfirmationStage.FIRST) cancelButton else executeButton,
            // Paper DialogにはEsc終了通知がないため、音声と保留状態を取りこぼさないよう
            // 明示的なボタン操作だけを確認終了経路にします。
            canCloseWithEscape = false,
        )

        return runCatching {
            CCSystem.getAPI().getMenuDialogService().show(player, request)
            true
        }.onFailure { failure ->
            plugin.logger.log(
                java.util.logging.Level.WARNING,
                "コマンドセーフガードのDialog表示に失敗しました: player=${player.uniqueId}",
                failure,
            )
        }.getOrDefault(false)
    }

    private fun showForm(
        player: Player,
        confirmation: PendingConfirmation,
        formService: MenuFormService,
    ): Boolean {
        val executeButton = MenuFormButton(
            id = SafeguardAction.EXECUTE.id,
            label = text(player, Keys.CHANPON_UTILITIES_COMMAND_SAFEGUARD_CONFIRM),
        )
        val cancelButton = MenuFormButton(
            id = SafeguardAction.CANCEL.id,
            label = text(player, Keys.CHANPON_UTILITIES_COMMAND_SAFEGUARD_CANCEL),
        )
        val buttons = if (confirmation.stage == ConfirmationStage.FIRST) {
            listOf(executeButton, cancelButton)
        } else {
            // Formはリスト順がそのまま表示順になるため、2回目だけ反転します。
            listOf(cancelButton, executeButton)
        }
        val request = MenuSimpleFormRequest(
            owner = OWNER,
            id = confirmation.stage.formId,
            title = text(player, Keys.CHANPON_UTILITIES_COMMAND_SAFEGUARD_TITLE),
            content = formBody(player, confirmation),
            buttons = buttons,
            handler = MenuFormHandler { target, response ->
                val action = SafeguardAction.from(response)
                    ?: return@MenuFormHandler MenuActionResult.Rejected()
                handleAction(target, confirmation.stage, action)
            },
            onClosed = MenuFormHandler { target, _ ->
                // 古いFormの終了通知が新しい確認状態を消さないよう、世代を確認します。
                if (pendingByPlayer[target.uniqueId]?.stage == confirmation.stage) {
                    cancel(target)
                }
                MenuActionResult.Success(MenuUpdate.Close)
            },
        )

        return runCatching { formService.show(player, request) }
            .onFailure { failure ->
                plugin.logger.log(
                    java.util.logging.Level.WARNING,
                    "コマンドセーフガードのForm表示に失敗しました: player=${player.uniqueId}",
                    failure,
                )
            }
            .getOrDefault(false)
    }

    private fun handleAction(
        player: Player,
        stage: ConfirmationStage,
        action: SafeguardAction,
    ): MenuActionResult {
        val current = pendingByPlayer[player.uniqueId]
        if (current == null || current.stage != stage) {
            return MenuActionResult.Success(MenuUpdate.Close)
        }

        return when (action) {
            SafeguardAction.CANCEL -> {
                cancel(player)
                MenuActionResult.Success(MenuUpdate.Close)
            }

            SafeguardAction.EXECUTE -> if (stage == ConfirmationStage.FIRST) {
                val next = current.copy(
                    stage = ConfirmationStage.SECOND,
                )
                pendingByPlayer[player.uniqueId] = next
                if (!showConfirmation(player, next)) {
                    cancel(player)
                    player.sendMessage(component(player, Keys.CHANPON_UTILITIES_COMMAND_SAFEGUARD_UI_UNAVAILABLE))
                    MenuActionResult.Success(MenuUpdate.Close)
                } else {
                    MenuActionResult.Success(MenuUpdate.None)
                }
            } else {
                cancel(player)
                approveAndExecute(player, current.command)
                MenuActionResult.Success(MenuUpdate.Close)
            }
        }
    }

    private fun approveAndExecute(player: Player, command: String) {
        val commandWithoutSlash = command.removePrefix("/").trim()
        val normalized = command.trim()
        approvedCommandsByPlayer[player.uniqueId] = normalized
        // performCommandがイベントを発火しない実装や、実行途中で失敗した場合にも
        // 一回限りの承認が残り続けないよう、次tickで必ず破棄します。
        plugin.server.scheduler.runTask(
            plugin,
            Runnable {
                if (approvedCommandsByPlayer[player.uniqueId] == normalized) {
                    approvedCommandsByPlayer.remove(player.uniqueId)
                }
            },
        )
        player.performCommand(commandWithoutSlash)
    }

    private fun startConfirmationSound(player: Player) {
        soundTasksByPlayer.remove(player.uniqueId)?.cancel()
        playConfirmationSound(player)
        soundTasksByPlayer[player.uniqueId] = plugin.server.scheduler.runTaskTimer(
            plugin,
            Runnable {
                if (!player.isOnline || !pendingByPlayer.containsKey(player.uniqueId)) {
                    soundTasksByPlayer.remove(player.uniqueId)?.cancel()
                    return@Runnable
                }
                playConfirmationSound(player)
            },
            SOUND_INTERVAL_TICKS,
            SOUND_INTERVAL_TICKS,
        )
    }

    private fun playConfirmationSound(player: Player) {
        player.playSound(
            player.location,
            Sound.BLOCK_NOTE_BLOCK_PLING,
            SOUND_VOLUME,
            SOUND_PITCH,
        )
    }

    private fun dialogBody(player: Player, confirmation: PendingConfirmation): List<Component> = listOf(
        component(player, Keys.CHANPON_UTILITIES_COMMAND_SAFEGUARD_DESCRIPTION),
        component(player, Keys.CHANPON_UTILITIES_COMMAND_SAFEGUARD_COMMAND_LABEL),
        Component.text(sanitizeCommand(confirmation.command)),
    )

    private fun formBody(player: Player, confirmation: PendingConfirmation): String = listOf(
        text(player, Keys.CHANPON_UTILITIES_COMMAND_SAFEGUARD_DESCRIPTION),
        text(player, Keys.CHANPON_UTILITIES_COMMAND_SAFEGUARD_COMMAND_LABEL),
        sanitizeCommand(confirmation.command),
    ).joinToString("\n")

    private fun sanitizeCommand(command: String): String {
        val sanitized = command
        .filterNot(Char::isISOControl)
        .replace('§', ' ')
        .replace('&', '＆')
        return if (sanitized.length <= MAX_COMMAND_DISPLAY_LENGTH) {
            sanitized
        } else {
            sanitized.take(MAX_COMMAND_DISPLAY_LENGTH - 1) + "…"
        }
    }

    private fun text(
        player: Player,
        key: LocalizationKey<String>,
        placeholders: Map<String, Any> = emptyMap(),
    ): String = CCSystem.getAPI().getLocalized(player, key, placeholders).replace('&', '§')

    private fun component(
        player: Player,
        key: LocalizationKey<String>,
        placeholders: Map<String, Any> = emptyMap(),
    ): Component = CCSystem.getAPI().getI18nComponent(player, key, placeholders)

    private data class PendingConfirmation(
        val command: String,
        val stage: ConfirmationStage,
    )

    private enum class ConfirmationStage(
        val dialogId: String,
        val formId: String,
    ) {
        FIRST(
            "first",
            "first",
        ),
        SECOND(
            "second",
            "second",
        ),
    }

    private enum class SafeguardAction(val id: String) {
        EXECUTE("execute"),
        CANCEL("cancel"),
        ;

        companion object {
            fun from(response: MenuFormResponse): SafeguardAction? =
                entries.firstOrNull { it.id == response.textValue("button") }
        }
    }

    private companion object {
        const val OWNER = "chanpon-utilities"
        const val BLACKLIST_CONFIG_PATH = "command_safeguard.blacklist"
        const val MAX_COMMAND_DISPLAY_LENGTH = 256
        const val SOUND_INTERVAL_TICKS = 20L
        const val SOUND_VOLUME = 1.0f
        const val SOUND_PITCH = 0.5f
    }
}
