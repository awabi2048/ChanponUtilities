package me.awabi2048.chanponutilities.guide

import com.awabi2048.ccsystem.api.localization.generated.ChanponUtilitiesKeys as Keys
import me.awabi2048.chanponutilities.util.ChanponI18n
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

/**
 * `/guide` コマンドです。
 *
 * プレイヤー向け: `start [route]`（ルートが1件だけなら省略可）、`stop`、`status`
 * 管理者向け: `admin setpoint <route> <index>`、`admin reload`
 */
class GuideCommand(
    private val routeStore: GuideRouteStore,
    private val sessionService: GuideSessionService,
) : CommandExecutor, TabCompleter {

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>,
    ): Boolean {
        val player = sender as? Player
        when (args.firstOrNull()?.lowercase()) {
            "start" -> {
                if (player == null) {
                    sender.sendMessage(ChanponI18n.text(null, Keys.CHANPON_UTILITIES_GUIDE_PLAYER_ONLY))
                    return true
                }
                if (!player.hasPermission(PERMISSION)) {
                    player.sendMessage(ChanponI18n.text(player, Keys.CHANPON_UTILITIES_MANAGEMENT_NO_PERMISSION))
                    return true
                }
                startGuide(player, args.getOrNull(1))
            }
            "stop" -> {
                if (player == null) {
                    sender.sendMessage(ChanponI18n.text(null, Keys.CHANPON_UTILITIES_GUIDE_PLAYER_ONLY))
                    return true
                }
                if (!player.hasPermission(PERMISSION)) {
                    player.sendMessage(ChanponI18n.text(player, Keys.CHANPON_UTILITIES_MANAGEMENT_NO_PERMISSION))
                    return true
                }
                if (sessionService.stopGuide(player)) {
                    player.sendMessage(ChanponI18n.text(player, Keys.CHANPON_UTILITIES_GUIDE_STOPPED))
                } else {
                    player.sendMessage(ChanponI18n.text(player, Keys.CHANPON_UTILITIES_GUIDE_NOT_ACTIVE))
                }
            }
            "status", null -> {
                if (player == null) {
                    sender.sendMessage(ChanponI18n.text(null, Keys.CHANPON_UTILITIES_GUIDE_PLAYER_ONLY))
                    return true
                }
                val status = sessionService.statusOf(player)
                if (status == null) {
                    player.sendMessage(ChanponI18n.text(player, Keys.CHANPON_UTILITIES_GUIDE_STATUS_INACTIVE))
                } else {
                    player.sendMessage(
                        ChanponI18n.text(
                            player, Keys.CHANPON_UTILITIES_GUIDE_STATUS_ACTIVE,
                            "route" to status.route.label,
                            "index" to status.nextIndex + 1,
                            "total" to status.route.points.size,
                        )
                    )
                }
            }
            "admin" -> admin(sender, args.drop(1))
            else -> sender.sendMessage(ChanponI18n.text(player, Keys.CHANPON_UTILITIES_GUIDE_USAGE))
        }
        return true
    }

    private fun startGuide(player: Player, routeId: String?) {
        if (routeId == null) {
            when (routeStore.all().size) {
                0 -> {
                    player.sendMessage(ChanponI18n.text(player, Keys.CHANPON_UTILITIES_GUIDE_NO_ROUTES))
                    return
                }
                1 -> Unit
                else -> {
                    player.sendMessage(ChanponI18n.text(player, Keys.CHANPON_UTILITIES_GUIDE_USAGE))
                    return
                }
            }
        }
        val route = if (routeId != null) routeStore.find(routeId) else routeStore.all().single()
        if (route == null) {
            player.sendMessage(
                ChanponI18n.text(player, Keys.CHANPON_UTILITIES_GUIDE_ROUTE_NOT_FOUND, "route" to routeId)
            )
            return
        }
        val replaced = sessionService.startGuide(player, route)
        val key = if (replaced) {
            Keys.CHANPON_UTILITIES_GUIDE_START_REPLACED
        } else {
            Keys.CHANPON_UTILITIES_GUIDE_STARTED
        }
        player.sendMessage(ChanponI18n.text(player, key, "route" to route.label))
    }

    private fun admin(sender: CommandSender, args: List<String>) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage(ChanponI18n.text(sender as? Player, Keys.CHANPON_UTILITIES_MANAGEMENT_NO_PERMISSION))
            return
        }
        when (args.firstOrNull()?.lowercase()) {
            "setpoint" -> {
                val player = sender as? Player
                if (player == null) {
                    sender.sendMessage(ChanponI18n.text(null, Keys.CHANPON_UTILITIES_GUIDE_PLAYER_ONLY))
                    return
                }
                val routeId = args.getOrNull(1)
                val indexText = args.getOrNull(2)
                val midpoint = Regex("([1-9][0-9]*)\\.5").matchEntire(indexText ?: "")?.groupValues?.get(1)?.toIntOrNull()
                if (routeId != null && midpoint != null) {
                    val loc = player.location
                    if (routeStore.setMidpoint(routeId, midpoint, loc.world.name, loc.x, loc.y, loc.z)) {
                        player.sendMessage(ChanponI18n.text(player, Keys.CHANPON_UTILITIES_GUIDE_ADMIN_SETPOINT_SAVED, "route" to routeId, "index" to "$midpoint.5"))
                    } else {
                        player.sendMessage(ChanponI18n.text(player, Keys.CHANPON_UTILITIES_GUIDE_ADMIN_INVALID_INDEX, "index" to "$midpoint.5"))
                    }
                    return
                }
                val index = indexText?.toIntOrNull()
                if (routeId == null || index == null) {
                    sender.sendMessage(ChanponI18n.text(player, Keys.CHANPON_UTILITIES_GUIDE_ADMIN_USAGE))
                    return
                }
                val location = player.location
                when (
                    val result = routeStore.setPoint(
                        routeId, index,
                        location.world.name,
                        location.x, location.y, location.z,
                        location.yaw, location.pitch,
                    )
                ) {
                    is GuideRouteStore.SetPointResult.RouteCreated -> {
                        player.sendMessage(
                            ChanponI18n.text(player, Keys.CHANPON_UTILITIES_GUIDE_ADMIN_ROUTE_CREATED, "route" to routeId)
                        )
                        player.sendMessage(
                            ChanponI18n.text(
                                player, Keys.CHANPON_UTILITIES_GUIDE_ADMIN_SETPOINT_SAVED,
                                "route" to routeId, "index" to 1,
                            )
                        )
                    }
                    is GuideRouteStore.SetPointResult.Saved -> {
                        player.sendMessage(
                            ChanponI18n.text(
                                player, Keys.CHANPON_UTILITIES_GUIDE_ADMIN_SETPOINT_SAVED,
                                "route" to routeId, "index" to result.index,
                            )
                        )
                    }
                    is GuideRouteStore.SetPointResult.InvalidIndex -> {
                        player.sendMessage(
                            ChanponI18n.text(
                                player, Keys.CHANPON_UTILITIES_GUIDE_ADMIN_INVALID_INDEX,
                                "index" to (args.getOrNull(2) ?: ""),
                            )
                        )
                    }
                    is GuideRouteStore.SetPointResult.Failed -> {
                        player.sendMessage(
                            ChanponI18n.text(
                                player, Keys.CHANPON_UTILITIES_GUIDE_ADMIN_INVALID_INDEX,
                                "index" to result.reason,
                            )
                        )
                    }
                }
            }
            "reload" -> {
                when (val result = routeStore.reload()) {
                    is GuideRouteStore.LoadResult.Loaded -> sender.sendMessage(
                        ChanponI18n.text(sender as? Player, Keys.CHANPON_UTILITIES_GUIDE_ADMIN_RELOADED, "count" to result.routeCount)
                    )
                    is GuideRouteStore.LoadResult.Invalid -> {
                        sender.sendMessage(ChanponI18n.text(sender as? Player, Keys.CHANPON_UTILITIES_GUIDE_ADMIN_RELOAD_FAILED))
                        result.errors.forEach { sender.sendMessage("§c- $it") }
                    }
                }
            }
            else -> sender.sendMessage(ChanponI18n.text(sender as? Player, Keys.CHANPON_UTILITIES_GUIDE_ADMIN_USAGE))
        }
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>,
    ): List<String> {
        return when (args.size) {
            1 -> buildList {
                addAll(listOf("start", "stop", "status"))
                if (sender.hasPermission(ADMIN_PERMISSION)) add("admin")
            }.filter { it.startsWith(args[0], ignoreCase = true) }
            2 -> when (args[0].lowercase()) {
                "start" -> routeStore.all().map(GuideRoute::id).filter { it.startsWith(args[1], ignoreCase = true) }
                "admin" -> if (sender.hasPermission(ADMIN_PERMISSION)) {
                    listOf("setpoint", "reload").filter { it.startsWith(args[1], ignoreCase = true) }
                } else emptyList()
                else -> emptyList()
            }
            3 -> if (args[0].equals("admin", true) && args[1].equals("setpoint", true) && sender.hasPermission(ADMIN_PERMISSION)) {
                routeStore.all().map(GuideRoute::id).filter { it.startsWith(args[2], ignoreCase = true) }
            } else emptyList()
            4 -> if (args[0].equals("admin", true) && args[1].equals("setpoint", true) && sender.hasPermission(ADMIN_PERMISSION)) {
                val size = routeStore.find(args[2])?.points?.size ?: 0
                (1..size + 1).map(Int::toString).filter { it.startsWith(args[3]) }
            } else emptyList()
            else -> emptyList()
        }
    }

    private companion object {
        const val PERMISSION = "chanponutilities.guide"
        const val ADMIN_PERMISSION = "chanponutilities.guide.admin"
    }
}
