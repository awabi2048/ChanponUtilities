package me.awabi2048.chanponutilities.guide

import com.awabi2048.ccsystem.api.localization.generated.ChanponUtilitiesKeys as Keys
import me.awabi2048.chanponutilities.util.ChanponI18n
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.util.BoundingBox
import java.util.UUID
import kotlin.math.sin

/**
 * ガイドアレイのセッション管理です。
 *
 * 1プレイヤーにつき1つの仮想アレイを描画し、中継点の待機・クリックによる
 * 完了・コース逸脱時の追従案内を状態機械として処理します。
 * すべてメインスレッド上で動作します。
 *
 * 中継点の1点目はガイド開始時に完了済みとして扱い、その message を開始
 * メッセージとして表示します。実際に案内されるのは2点目以降です。
 */
class GuideSessionService(
    private val plugin: JavaPlugin,
    private val settings: GuideSettings,
) {
    private val renderer = GuideAllayRenderer(plugin, ::handleInteract)
    private val sessions = HashMap<UUID, GuideSession>()
    private var tickTaskId = -1

    /** ガイドモジュールの設定値です。config reload はライフサイクル再起動を伴うため生成時の snapshot で構いません。 */
    data class GuideSettings(
        val clickDistance: Double,
        val courseRadius: Double,
        val offCourseGraceSeconds: Double,
        val followMessageIntervalSeconds: Double,
        val flightSpeed: Double,
        val followFlightSpeed: Double,
    )

    private enum class Phase {
        /** 中継点で待機しており、クリックを受け付ける状態です。 */
        WAITING,

        /** 完了直後に次の中継点へ飛行中です。 */
        FLYING,

        /** プレイヤーがコースを逸脱し、アレイが付近へ追従して案内中です。 */
        FOLLOWING,

        /** プレイヤーがコースへ復帰し、アレイが中継点へ帰還中です。 */
        RETURNING,
    }

    private class GuideSession(
        val player: Player,
        val route: GuideRoute,
        val entityId: Int,
        var nextIndex: Int,
        var x: Double,
        var y: Double,
        var z: Double,
        var phase: Phase,
        var offCourseTicks: Int = 0,
        var lastPromptAtMillis: Long = 0L,
        var lastClickPromptAtMillis: Long = 0L,
        var animTicks: Int = 0,
        var anchorX: Double = 0.0,
        var anchorY: Double = 0.0,
        var anchorZ: Double = 0.0,
        var anchorValid: Boolean = false,
        var anchorPlayerX: Double = Double.NaN,
        var anchorPlayerY: Double = Double.NaN,
        var anchorPlayerZ: Double = Double.NaN,
    )

    fun start() {
        renderer.register()
        tickTaskId = plugin.server.scheduler.scheduleSyncRepeatingTask(plugin, ::tickAll, 1L, 1L)
    }

    fun shutdown() {
        if (tickTaskId != -1) {
            plugin.server.scheduler.cancelTask(tickTaskId)
            tickTaskId = -1
        }
        sessions.values.forEach { renderer.destroy(it.player, it.entityId) }
        sessions.clear()
        renderer.unregister()
    }

    /** 進行中セッションの表示用スナップショットです。 */
    data class SessionStatus(val route: GuideRoute, val nextIndex: Int)

    fun statusOf(player: Player): SessionStatus? =
        sessions[player.uniqueId]?.let { SessionStatus(it.route, it.nextIndex) }

    /**
     * ガイドを開始します。すでにガイド中なら現在のセッションを破棄して
     * 新しいルートで開始します。
     * 中継点が1点だけのルートは開始メッセージ表示後、その場で完了します。
     */
    fun startGuide(player: Player, route: GuideRoute): Boolean {
        val hadSession = sessions.containsKey(player.uniqueId)
        if (hadSession) stopSession(player)

        // 1点目は開始時に完了扱いです。開始メッセージとして message を表示します。
        val first = route.points.first()
        first.message.forEach { player.sendMessage(ChanponI18n.colorize(it)) }

        if (route.points.size < 2) {
            // 案内対象の中継点がないため、開始メッセージの表示のみで完了とします。
            player.sendMessage(
                ChanponI18n.text(player, Keys.CHANPON_UTILITIES_GUIDE_COMPLETED, "route" to route.label)
            )
            return hadSession
        }

        val name = LegacyComponentSerializer.legacySection()
            .deserialize(ChanponI18n.text(player, Keys.CHANPON_UTILITIES_GUIDE_ALLAY_NAME))
        val entityId = renderer.spawn(
            player,
            first.x, first.y, first.z,
            first.yaw, first.pitch,
            name,
        )
        val second = route.points[1]
        val session = GuideSession(
            player = player,
            route = route,
            entityId = entityId,
            nextIndex = 1,
            x = first.x, y = first.y, z = first.z,
            phase = Phase.FLYING,
        )
        sessions[player.uniqueId] = session
        // 開始時点で次の中継点への飛行姿勢へ向けます。
        val (yaw, pitch) = GuideCourseMath.lookAngles(first.x, first.y, first.z, second.x, second.y, second.z)
        renderer.moveTo(player, entityId, first.x, first.y, first.z, yaw, pitch)
        return hadSession
    }

    /** ガイドを明示終了します。セッションがなければ false です。 */
    fun stopGuide(player: Player): Boolean {
        if (!sessions.containsKey(player.uniqueId)) return false
        stopSession(player)
        return true
    }

    /** ログアウト時の静かな破棄です。client 側の仮想実体も接続断で消えます。 */
    fun onQuit(player: Player) {
        sessions.remove(player.uniqueId)?.let { renderer.destroy(player, it.entityId) }
    }

    /**
     * ワールド移動時の破棄です。ルートの対象ワールド外へ出た場合のみ
     * セッションを終了し、退出が発生したかを返します。
     */
    fun onWorldLeft(player: Player): Boolean {
        val session = sessions[player.uniqueId] ?: return false
        if (player.world.name == session.route.worldName) return false
        sessions.remove(player.uniqueId)
        renderer.destroy(player, session.entityId)
        return true
    }

    private fun stopSession(player: Player) {
        sessions.remove(player.uniqueId)?.let { renderer.destroy(player, it.entityId) }
    }

    /**
     * UseEntity packet の受信（メインスレッド）です。
     * アレイのクリックは中継点の待機中・一定距離内のみ受理し、追従中は
     * 誘導メッセージの再送に留めます。
     */
    private fun handleInteract(player: Player, entityId: Int) {
        val session = sessions[player.uniqueId] ?: return
        if (session.entityId != entityId) return
        if (player.world.name != session.route.worldName) return

        when (session.phase) {
            Phase.WAITING -> {
                val maxDist = settings.clickDistance
                if (GuideCourseMath.distanceSq(
                        player.location.x, player.location.y, player.location.z,
                        session.x, session.y, session.z,
                    ) > maxDist * maxDist
                ) return
                completeWaypoint(session)
            }
            Phase.FOLLOWING -> {
                val now = System.currentTimeMillis()
                if (now - session.lastClickPromptAtMillis >= 1_000L) {
                    session.lastClickPromptAtMillis = now
                    player.sendMessage(ChanponI18n.text(player, Keys.CHANPON_UTILITIES_GUIDE_FOLLOW_PROMPT))
                }
            }
            // 飛行中・帰還中のアレイはクリックを受け付けません。
            Phase.FLYING, Phase.RETURNING -> Unit
        }
    }

    private fun completeWaypoint(session: GuideSession) {
        val player = session.player
        val point = session.route.points[session.nextIndex]
        point.message.forEach { player.sendMessage(ChanponI18n.colorize(it)) }
        player.sendMessage(
            ChanponI18n.text(
                player, Keys.CHANPON_UTILITIES_GUIDE_WAYPOINT_COMPLETED,
                "index" to session.nextIndex + 1,
                "total" to session.route.points.size,
            )
        )
        session.nextIndex++
        session.offCourseTicks = 0
        if (session.nextIndex >= session.route.points.size) {
            sessions.remove(player.uniqueId)
            renderer.destroy(player, session.entityId)
            player.sendMessage(
                ChanponI18n.text(player, Keys.CHANPON_UTILITIES_GUIDE_COMPLETED, "route" to session.route.label)
            )
        } else {
            session.phase = Phase.FLYING
        }
    }

    private fun tickAll() {
        if (sessions.isEmpty()) return
        val iterator = sessions.values.iterator()
        while (iterator.hasNext()) {
            val session = iterator.next()
            if (!session.player.isOnline) {
                iterator.remove()
                continue
            }
            tick(session)
        }
    }

    private fun tick(session: GuideSession) {
        val player = session.player
        if (player.world.name != session.route.worldName) return

        session.animTicks++
        when (session.phase) {
            Phase.WAITING -> tickWaiting(session)
            Phase.FLYING, Phase.RETURNING -> tickFlight(session)
            Phase.FOLLOWING -> tickFollowing(session)
        }
        checkOffCourse(session)
    }

    /**
     * 論理位置に加える上下の単振動オフセットです。アレイの常時浮遊アニメーション
     * として全フェーズで描画位置へ適用します。
     */
    private fun bobOffset(session: GuideSession): Double =
        sin(session.animTicks * BOB_RADIANS_PER_TICK) * BOB_AMPLITUDE

    /**
     * 待機中は単振動による浮遊だけを行い、向きは中継点に設定された yaw を維持します。
     */
    private fun tickWaiting(session: GuideSession) {
        val point = session.route.points[session.nextIndex]
        renderer.moveTo(session.player, session.entityId, point.x, point.y + bobOffset(session), point.z, point.yaw, point.pitch)
    }

    private fun tickFlight(session: GuideSession) {
        val target = session.route.points[session.nextIndex]
        val speed = if (session.phase == Phase.FLYING) settings.flightSpeed else settings.followFlightSpeed
        val step = GuideCourseMath.stepToward(
            session.x, session.y, session.z,
            target.x, target.y, target.z,
            speed / TICKS_PER_SECOND,
        )
        session.x = step.first
        session.y = step.second
        session.z = step.third
        val (yaw, pitch) = GuideCourseMath.lookAngles(session.x, session.y, session.z, target.x, target.y, target.z)
        renderer.moveTo(session.player, session.entityId, session.x, session.y + bobOffset(session), session.z, yaw, pitch)

        if (GuideCourseMath.distanceSq(session.x, session.y, session.z, target.x, target.y, target.z) <= ARRIVAL_EPSILON_SQ) {
            session.x = target.x
            session.y = target.y
            session.z = target.z
            session.phase = Phase.WAITING
            renderer.moveTo(session.player, session.entityId, session.x, session.y + bobOffset(session), session.z, target.yaw, target.pitch)
        }
    }

    /**
     * 追従モードです。アレイはコース境界の円筒面上（プレイヤー側）にある
     * 占有可能な位置へ飛行して滞在し、体と頭を次の中継点方向へ向けて案内します。
     * 占有可能な位置が見つからない間は現在位置を維持します。
     */
    private fun tickFollowing(session: GuideSession) {
        val player = session.player
        refreshAnchorIfNeeded(session)
        if (session.anchorValid) {
            val step = GuideCourseMath.stepToward(
                session.x, session.y, session.z,
                session.anchorX, session.anchorY, session.anchorZ,
                settings.followFlightSpeed / TICKS_PER_SECOND,
            )
            session.x = step.first
            session.y = step.second
            session.z = step.third
        }
        val target = session.route.points[session.nextIndex]
        val (yaw, pitch) = GuideCourseMath.lookAngles(
            session.x, session.y + ALLAY_HEIGHT / 2.0, session.z,
            target.x, target.y, target.z,
        )
        renderer.moveTo(session.player, session.entityId, session.x, session.y + bobOffset(session), session.z, yaw, pitch)
    }

    /**
     * プレイヤーが動いたときだけ追従アンカーを再計算し、ブロック占有判定で
     * 置ける位置を選びます。候補はコース境界円筒の側面上（プレイヤー側）です。
     */
    private fun refreshAnchorIfNeeded(session: GuideSession) {
        val loc = session.player.location
        if (!loc.x.isNaN() &&
            GuideCourseMath.distanceSq(loc.x, loc.y, loc.z, session.anchorPlayerX, session.anchorPlayerY, session.anchorPlayerZ) <= ANCHOR_REFRESH_MOVE_SQ &&
            session.anchorValid
        ) return
        session.anchorPlayerX = loc.x
        session.anchorPlayerY = loc.y
        session.anchorPlayerZ = loc.z
        session.anchorValid = false
        val prev = session.route.points[session.nextIndex - 1]
        val next = session.route.points[session.nextIndex]
        val candidates = GuideCourseMath.boundaryAnchorCandidates(
            loc.x, loc.y, loc.z,
            prev.x, prev.y, prev.z,
            next.x, next.y, next.z,
            settings.courseRadius,
        )
        for ((x, y, z) in candidates) {
            if (canOccupyAllay(session.player.world, x, y, z)) {
                session.anchorX = x
                session.anchorY = y
                session.anchorZ = z
                session.anchorValid = true
                return
            }
        }
    }

    /**
     * コース逸脱判定です。現在区間（直近完了点→次の中継点）を軸とする
     * 3Dカプセルの外に猶予時間より長く留まると追従モードへ入り、
     * 戻るとアレイは中継点へ帰還します。
     */
    private fun checkOffCourse(session: GuideSession) {
        if (session.nextIndex >= session.route.points.size) return
        val player = session.player
        val prev = session.route.points[session.nextIndex - 1]
        val next = session.route.points[session.nextIndex]
        val loc = player.location
        val onCourse = GuideCourseMath.isOnCourse(
            loc.x, loc.y, loc.z, prev, next, settings.courseRadius,
        )
        if (onCourse) {
            if (session.phase == Phase.FOLLOWING) {
                session.phase = Phase.RETURNING
                player.sendMessage(ChanponI18n.text(player, Keys.CHANPON_UTILITIES_GUIDE_RETURNED))
            }
            session.offCourseTicks = 0
            return
        }
        session.offCourseTicks++
        val graceTicks = (settings.offCourseGraceSeconds * TICKS_PER_SECOND).toInt()
        if (session.phase == Phase.WAITING && session.offCourseTicks >= graceTicks) {
            session.phase = Phase.FOLLOWING
            session.anchorValid = false
            sendFollowPrompt(session)
        } else if (session.phase == Phase.FOLLOWING) {
            val intervalMillis = (settings.followMessageIntervalSeconds * 1_000).toLong()
            val now = System.currentTimeMillis()
            if (now - session.lastPromptAtMillis >= intervalMillis) {
                sendFollowPrompt(session)
            }
        }
    }

    private fun sendFollowPrompt(session: GuideSession) {
        session.lastPromptAtMillis = System.currentTimeMillis()
        session.player.sendMessage(ChanponI18n.text(session.player, Keys.CHANPON_UTILITIES_GUIDE_FOLLOW_PROMPT))
    }

    /**
     * アレイの当たり判定がその位置に置けるかを、ブロック衝突形状との
     * 交差で判定します。
     */
    private fun canOccupyAllay(world: World, x: Double, y: Double, z: Double): Boolean =
        GuideCourseMath.canOccupy(x, y, z, ALLAY_HALF_WIDTH, ALLAY_HEIGHT) { bx, by, bz, minX, minY, minZ, maxX, maxY, maxZ ->
            val block = world.getBlockAt(bx, by, bz)
            block.collisionShape.boundingBoxes.any { box ->
                box.overlaps(
                    BoundingBox(
                        minX - bx, minY - by, minZ - bz,
                        maxX - bx, maxY - by, maxZ - bz,
                    )
                )
            }
        }

    private companion object {
        const val TICKS_PER_SECOND = 20.0
        /** 中継点への到達判定の許容誤差です。 */
        const val ARRIVAL_EPSILON_SQ = 0.01

        // 常時の単振動アニメーションです。1tickごとの位相進行で周期は約3秒です。
        const val BOB_RADIANS_PER_TICK = Math.PI / 30.0
        const val BOB_AMPLITUDE = 0.15
        /** Allay の当たり判定サイズです（NMS の ALLAY 寸法 0.35 x 0.6）。 */
        const val ALLAY_HALF_WIDTH = 0.175
        const val ALLAY_HEIGHT = 0.6
        /** 追従アンカーの再計算を行うプレイヤー移動量の閾値（二乗）です。 */
        const val ANCHOR_REFRESH_MOVE_SQ = 0.25 * 0.25
    }
}
