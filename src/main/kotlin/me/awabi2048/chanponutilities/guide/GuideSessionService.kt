package me.awabi2048.chanponutilities.guide

import com.awabi2048.ccsystem.api.localization.generated.ChanponUtilitiesKeys as Keys
import me.awabi2048.chanponutilities.util.ChanponI18n
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.util.BoundingBox
import java.util.UUID
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * ガイドアレイのセッション管理です。
 *
 * 1プレイヤーにつき1つの仮想アレイを描画し、中継点の待機・クリックによる
 * 完了・コース逸脱時の追従案内を状態機械として処理します。
 * すべてメインスレッド上で動作します。
 *
 * ガイド開始時は1点目でアレイが待機し、プレイヤーのクリックで1点目が
 * 完了して案内が始まります。逸脱中はシステムチャット（長周期）と
 * アレイ頭上のテキスト表示（短周期）の二系統で案内します。
 */
class GuideSessionService(
    private val plugin: JavaPlugin,
    private val settings: GuideSettings,
) {
    private val renderer = GuideAllayRenderer(plugin, ::handleInteract)
    private val sessions = HashMap<UUID, GuideSession>()
    private val departures = mutableListOf<Departure>()

    private data class Departure(
        val player: Player, val entityId: Int, val worldName: String,
        val pose: GuideAllayRenderer.Pose, var tick: Int = 0,
    )
    private var tickTaskId = -1

    /** ガイドモジュールの設定値です。config reload はライフサイクル再起動を伴うため生成時の snapshot で構いません。 */
    data class GuideSettings(
        val clickDistance: Double,
        val waypointRadius: Double,
        val waypointVerticalRadius: Double,
        val waypointDwellSeconds: Double,
        val messageWaitSecondsPerLine: Double,
        val courseRadius: Double,
        val offCourseGraceSeconds: Double,
        val offCourseChatIntervalSeconds: Double,
        val followDisplayIntervalSeconds: Double,
        val flightSpeed: Double,
        val followFlightSpeed: Double,
        val debug: Boolean = false,
    )

    private enum class Phase {
        /** 出現演出中はクリックや経路判定を受け付けません。 */
        APPEARING,
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
        var lastClickPromptAtMillis: Long = 0L,
        var bobPhase: Double = 0.0,
        var flight: GuideCourseMath.FlightPath? = null,
        var coursePath: GuideCourseMath.FlightPath? = null,
        var anchorValid: Boolean = false,
        var anchorPlayerX: Double = Double.NaN,
        var anchorPlayerY: Double = Double.NaN,
        var anchorPlayerZ: Double = Double.NaN,
        var followTicks: Int = 0,
        var displayEntityId: Int = -1,
        var displayIndex: Int = 0,
        var currentDisplayText: String? = null,
        var glowing: Boolean = false,
        var movingThisTick: Boolean = false,
        var particleTicks: Int = 0,
        var speedPerTick: Double = 0.0,
        var movementNoisePhase: Double = 0.0,
        var pathRetryTicks: Int = 0,
        var waypointProgress: GuideWaypointProgress? = null,
        var pausedForDistance: Boolean = false,
        var ended: Boolean = false,
        var appearanceTick: Int = 0,
    )

    fun isRunning(): Boolean = tickTaskId != -1

    fun start() {
        renderer.register()
        tickTaskId = plugin.server.scheduler.scheduleSyncRepeatingTask(plugin, ::tickAll, 1L, 1L)
    }

    fun shutdown() {
        if (tickTaskId != -1) {
            plugin.server.scheduler.cancelTask(tickTaskId)
            tickTaskId = -1
        }
        sessions.values.forEach { if (!it.ended) destroyEntities(it) }
        sessions.clear()
        departures.forEach { renderer.destroy(it.player, it.entityId) }
        departures.clear()
        renderer.unregister()
    }

    /** 進行中セッションの表示用スナップショットです。 */
    data class SessionStatus(val route: GuideRoute, val nextIndex: Int)

    /** 頻繁な自動開始判定では表示用スナップショットを作らず状態だけを確認します。 */
    fun isGuiding(player: Player): Boolean = sessions[player.uniqueId]?.ended == false

    fun statusOf(player: Player): SessionStatus? =
        sessions[player.uniqueId]?.takeUnless { it.ended }?.let { SessionStatus(it.route, it.nextIndex) }

    /**
     * ガイドを開始します。すでにガイド中なら現在のセッションを破棄して
     * 新しいルートで開始します。
     *
     * アレイは1点目へ出現して待機し、プレイヤーがクリックすると1点目が
     * 完了して2点目への案内が始まります。1点のみのルートはそのクリックで
     * 完了となります。
     */
    fun startGuide(player: Player, route: GuideRoute): Boolean {
        val hadSession = sessions.containsKey(player.uniqueId)
        if (hadSession) stopSession(player)

        val first = route.points.first()
        val name = LegacyComponentSerializer.legacySection()
            .deserialize(ChanponI18n.text(player, Keys.CHANPON_UTILITIES_GUIDE_ALLAY_NAME))
        val entityId = renderer.spawn(
            player,
            first.x, first.y - 1.0, first.z,
            first.yaw, first.pitch,
            name,
        )
        val session = GuideSession(
            player = player,
            route = route,
            entityId = entityId,
            nextIndex = 0,
            x = first.x, y = first.y, z = first.z,
            phase = Phase.APPEARING,
        )
        player.playSound(player.location, Sound.ENTITY_ALLAY_ITEM_GIVEN, 1.0f, 1.25f)
        sessions[player.uniqueId] = session
        player.sendMessage(ChanponI18n.text(player, Keys.CHANPON_UTILITIES_GUIDE_AWAIT_FIRST_CLICK))
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
        sessions.remove(player.uniqueId)?.let { if (!it.ended) destroyEntities(it) }
        discardDepartures(player)
    }

    /**
     * ワールド移動時の破棄です。ルートの対象ワールド外へ出た場合のみ
     * セッションを終了し、退出が発生したかを返します。
     */
    fun onWorldLeft(player: Player): Boolean {
        // ワールド切替後には旧ワールドでの消滅演出を継続できません。
        discardDepartures(player)
        val session = sessions[player.uniqueId] ?: return false
        if (player.world.name == session.route.worldName) return false
        sessions.remove(player.uniqueId)
        if (!session.ended) destroyEntities(session)
        return true
    }

    private fun stopSession(player: Player) {
        sessions.remove(player.uniqueId)?.let { if (!it.ended) beginDeparture(it) }
    }

    /** 通常終了・中断・置換時は最後の表示姿勢を保持して10 tick消滅演出を行います。 */
    private fun beginDeparture(session: GuideSession) {
        session.player.playSound(session.player.location, Sound.ENTITY_ALLAY_AMBIENT_WITHOUT_ITEM, 1.0f, 1.25f)
        if (session.displayEntityId != -1) {
            renderer.destroy(session.player, session.displayEntityId)
            session.displayEntityId = -1
        }
        val pose = renderer.pose(session.entityId)
        if (pose == null || !session.player.isOnline || session.player.world.name != session.route.worldName) {
            renderer.destroy(session.player, session.entityId)
        } else {
            departures.add(Departure(session.player, session.entityId, session.route.worldName, pose))
        }
    }

    private fun discardDepartures(player: Player) {
        val iterator = departures.iterator()
        while (iterator.hasNext()) {
            val item = iterator.next()
            if (item.player.uniqueId == player.uniqueId) {
                renderer.destroy(item.player, item.entityId)
                iterator.remove()
            }
        }
    }

    private fun destroyEntities(session: GuideSession) {
        renderer.destroy(session.player, session.entityId)
        if (session.displayEntityId != -1) {
            renderer.destroy(session.player, session.displayEntityId)
            session.displayEntityId = -1
        }
    }

    /**
     * UseEntity packet の受信（メインスレッド）です。
     * アレイのクリックは中継点の待機中・一定距離内のみ受理し、追従中は
     * 誘導メッセージの再送に留めます。
     */
    private fun handleInteract(player: Player, entityId: Int) {
        val session = sessions[player.uniqueId] ?: return
        if (session.ended || session.entityId != entityId) return
        if (player.world.name != session.route.worldName) return

        when (session.phase) {
            Phase.WAITING -> Unit
            Phase.FOLLOWING -> Unit
            // 飛行中・帰還中のアレイはクリックを受け付けません。
            Phase.APPEARING, Phase.FLYING, Phase.RETURNING -> Unit
        }
    }

    private fun sendWaypointMessage(session: GuideSession) {
        val player = session.player
        session.route.points[session.nextIndex].message.forEach { player.sendMessage(ChanponI18n.colorize(it)) }
        // 中継完了時の演出は案内対象者にのみ表示します。
        player.spawnParticle(Particle.TOTEM_OF_UNDYING, session.x, session.y, session.z, 20, 2.0, 2.0, 2.0, 0.2)
        player.playSound(player.location, Sound.ENTITY_ALLAY_AMBIENT_WITH_ITEM, 0.8f, 0.98f)
        player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f)
    }

    private fun advanceWaypoint(session: GuideSession) {
        session.waypointProgress = null
        session.nextIndex++
        session.offCourseTicks = 0
        if (session.nextIndex >= session.route.points.size) {
            session.ended = true
            beginDeparture(session)

        } else {
            session.phase = Phase.FLYING
            session.flight = planFlight(session, session.route.points[session.nextIndex])
            session.coursePath = session.flight
            if (session.flight == null) plugin.logger.warning("ガイド経路を探索できません: route=${session.route.id}, point=${session.nextIndex + 1}")
        }
    }

    /** 中間点を通り、通過できることを確認した経路だけを返します。 */
    private fun planFlight(session: GuideSession, target: GuidePoint): GuideCourseMath.FlightPath? {
        val midpoint = session.route.midpoints[session.nextIndex]
        val start = Triple(session.x, session.y, session.z)
        val end = Triple(target.x, target.y, target.z)
        if (midpoint == null || session.phase != Phase.FLYING) return findPath(session, start, end)
        val via = Triple(midpoint.x, midpoint.y, midpoint.z)
        val first = findRawPath(session, start, via) ?: return null
        val second = findRawPath(session, via, end) ?: return null
        val knots = first + second.drop(1)
        return GuideSmoothPath.build(knots) { x, y, z -> canOccupyPath(session.player.world, x, y, z) }?.let(GuideCourseMath.FlightPath::of)
    }

    private fun planFlightTo(session: GuideSession, tx: Double, ty: Double, tz: Double): GuideCourseMath.FlightPath? =
        findPath(session, Triple(session.x, session.y, session.z), Triple(tx, ty, tz))

    private fun findRawPath(session: GuideSession, from: Triple<Double, Double, Double>, to: Triple<Double, Double, Double>): List<Triple<Double, Double, Double>>? =
        GuidePathfinder.find(from, to) { x, y, z -> canOccupyPath(session.player.world, x, y, z) }

    private fun findPath(session: GuideSession, from: Triple<Double, Double, Double>, to: Triple<Double, Double, Double>): GuideCourseMath.FlightPath? {
        val knots = findRawPath(session, from, to) ?: return null
        return GuideSmoothPath.build(knots) { x, y, z -> canOccupyPath(session.player.world, x, y, z) }?.let(GuideCourseMath.FlightPath::of)
    }
    /** 未読込チャンクを避け、標本の間隔を考慮して当たり判定を保守的に広げます。 */
    private fun canOccupyPath(world: World, x: Double, y: Double, z: Double): Boolean =
        GuideCourseMath.canOccupy(x, y, z, ALLAY_HALF_WIDTH + 0.1, ALLAY_HEIGHT + 0.1) { bx, by, bz, minX, minY, minZ, maxX, maxY, maxZ ->
            if (!world.isChunkLoaded(bx shr 4, bz shr 4)) return@canOccupy true
            world.getBlockAt(bx, by, bz).collisionShape.boundingBoxes.any { box ->
                box.overlaps(BoundingBox(minX - bx, minY - by, minZ - bz, maxX - bx, maxY - by, maxZ - bz))
            }
        }
    private fun tickAll() {
        tickDepartures()
        if (sessions.isEmpty()) return
        val iterator = sessions.values.iterator()
        while (iterator.hasNext()) {
            val session = iterator.next()
            if (session.ended || !session.player.isOnline) {
                iterator.remove()
                continue
            }
            tick(session)
            if (session.ended) iterator.remove()
        }
    }

    private fun tickDepartures() {
        val iterator = departures.iterator()
        while (iterator.hasNext()) {
            val item = iterator.next()
            if (!item.player.isOnline || item.player.world.name != item.worldName) {
                renderer.destroy(item.player, item.entityId)
                iterator.remove()
                continue
            }
            val frame = GuideLifecycleAnimation.disappearing(item.pose.y, item.pose.yaw, item.pose.scale, ++item.tick)
            renderer.sendScale(item.player, item.entityId, frame.scale)
            renderer.moveTo(item.player, item.entityId, item.pose.x, frame.y, item.pose.z, frame.yaw, item.pose.pitch)
            if (item.tick >= GuideLifecycleAnimation.DURATION_TICKS) {
                renderer.destroy(item.player, item.entityId)
                iterator.remove()
            }
        }
    }

    private fun tickAppearance(session: GuideSession) {
        val point = session.route.points.first()
        val tick = ++session.appearanceTick
        val frame = GuideLifecycleAnimation.appearing(point.y, point.yaw, tick)
        renderer.sendScale(session.player, session.entityId, frame.scale)
        renderer.moveTo(session.player, session.entityId, point.x, frame.y, point.z, frame.yaw, point.pitch)
        if (tick == 1) renderer.reveal(session.player, session.entityId)
        if (tick >= GuideLifecycleAnimation.DURATION_TICKS) session.phase = Phase.WAITING
    }

    private fun tick(session: GuideSession) {
        val player = session.player
        if (player.world.name != session.route.worldName) return

        session.movingThisTick = false
        if (session.phase == Phase.APPEARING) {
            tickAppearance(session)
            return
        }
        // 経路内で置いていかれた場合は進捗を保持して待ちます。
        val location = player.location
        val onCourse = session.coursePath?.let { GuideCourseMath.isNearPath(location.x, location.y, location.z, it.points, settings.courseRadius) } == true
        val tooFar = session.phase == Phase.FLYING && onCourse &&
            GuideCourseMath.distanceSq(location.x, location.y, location.z, session.x, session.y, session.z) >= 64.0
        if (tooFar) {
            session.pausedForDistance = true
            session.speedPerTick = 0.0
            session.followTicks++
            val viewer = player.eyeLocation
            val (yaw, pitch) = GuideCourseMath.lookAngles(session.x, session.y + ALLAY_HEIGHT / 2.0, session.z, viewer.x, viewer.y, viewer.z)
            val bob = bobOffset(session)
            renderer.moveTo(player, session.entityId, session.x, session.y + bob, session.z, yaw, pitch)
            updateFollowDisplay(session, bob)
            val interval = (settings.offCourseChatIntervalSeconds * TICKS_PER_SECOND).toInt().coerceAtLeast(1)
            if (session.followTicks % interval == 0) session.currentDisplayText?.let { sendPeriodicGuide(player, it) }
            checkOffCourse(session)
            showParticles(session)
            return
        }
        if (session.pausedForDistance) {
            session.pausedForDistance = false
            session.followTicks = 0
            session.currentDisplayText = null
            if (session.displayEntityId != -1) renderer.destroy(player, session.displayEntityId)
            session.displayEntityId = -1
        }
        when (session.phase) {
            Phase.APPEARING -> error("出現演出は先に処理されます")
            Phase.WAITING -> tickWaiting(session)
            Phase.FLYING, Phase.RETURNING -> tickFlight(session)
            Phase.FOLLOWING -> tickFollowing(session)
        }
        if (session.ended) return
        checkOffCourse(session)
        showParticles(session)
        if (settings.debug && session.particleTicks % 10 == 0) showDebug(session)
    }

    /** 実際の判定で使う折れ線と、その周囲の断面を可視化します。 */
    private fun showDebug(session: GuideSession) {
        val path = session.coursePath ?: return
        val count = kotlin.math.ceil(path.totalLength).toInt().coerceIn(1, 200)
        for (i in 0..count) {
            val d = path.totalLength * i / count
            val point = path.pointAt(d)
            session.player.spawnParticle(Particle.END_ROD, point.first, point.second, point.third, 1, 0.0, 0.0, 0.0, 0.0)
            if (i % 4 != 0) continue
            val before = path.pointAt((d - 0.1).coerceAtLeast(0.0))
            val after = path.pointAt((d + 0.1).coerceAtMost(path.totalLength))
            val direction = org.bukkit.util.Vector(after.first - before.first, after.second - before.second, after.third - before.third)
            if (direction.lengthSquared() < 1.0e-9) continue
            direction.normalize()
            val reference = if (kotlin.math.abs(direction.y) > 0.9) org.bukkit.util.Vector(1, 0, 0) else org.bukkit.util.Vector(0, 1, 0)
            val u = direction.clone().crossProduct(reference).normalize()
            val v = direction.clone().crossProduct(u).normalize()
            for (n in 0 until 12) {
                val angle = n * Math.PI / 6.0
                val offset = u.clone().multiply(cos(angle) * settings.courseRadius).add(v.clone().multiply(sin(angle) * settings.courseRadius))
                session.player.spawnParticle(Particle.DUST, point.first + offset.x, point.second + offset.y, point.third + offset.z,
                    1, 0.0, 0.0, 0.0, 0.0, Particle.DustOptions(org.bukkit.Color.AQUA, 0.8f))
            }
        }
    }

    /** 演出は案内対象のプレイヤーにだけ送信します。 */
    private fun showParticles(session: GuideSession) {
        val player = session.player
        val phase = session.particleTicks * Math.PI / 20.0
        for (i in 0..1) {
            val angle = phase + i * Math.PI
            // 各周回点に2種類を同数・同位置で表示します。
            val x = session.x + cos(angle) * 2.0
            val y = session.y - 0.1 + sin(phase * 0.5) * 0.1
            val z = session.z + sin(angle) * 2.0
            player.spawnParticle(Particle.ENCHANTED_HIT, x, y, z, 1, 0.0, 0.0, 0.0, 0.0)
            player.spawnParticle(Particle.DUST_COLOR_TRANSITION, x, y, z, 1, 0.0, 0.0, 0.0, 0.0, Particle.DustTransition(org.bukkit.Color.fromRGB(128, 255, 230), org.bukkit.Color.WHITE, 0.75f))
        }
        if (++session.particleTicks % 100 != 0) return
        val from = player.location.add(0.0, 1.0, 0.0)
        val allay = org.bukkit.Location(player.world, session.x, session.y + 0.3, session.z)
        val point = session.route.points[session.nextIndex]
        val target = org.bukkit.Location(player.world, point.x, point.y + 0.3, point.z)
        if (session.phase == Phase.FOLLOWING) {
            showParticleLine(player, from, target)
        } else {
            showParticleLine(player, from, allay)
            showParticleLine(player, allay, target)
        }
    }

    /** 誘導線上の位置にばらつきと目的地方向の速度を与えます。 */
    private fun showParticleLine(player: Player, from: org.bukkit.Location, to: org.bukkit.Location) {
        val dx = to.x - from.x
        val dy = to.y - from.y
        val dz = to.z - from.z
        val length = sqrt(dx * dx + dy * dy + dz * dz)
        if (length <= 1.0e-6) return
        val count = (length * 2.0).toInt().coerceIn(1, 128)
        val random = java.util.concurrent.ThreadLocalRandom.current()
        for (i in 0..count) {
            val t = i.toDouble() / count
            player.spawnParticle(Particle.END_ROD,
                from.x + dx * t + random.nextDouble(-0.1, 0.1),
                from.y + dy * t + random.nextDouble(-0.1, 0.1),
                from.z + dz * t + random.nextDouble(-0.1, 0.1),
                0, dx / length, dy / length, dz / length, 0.05)
        }
    }

    /**
     * 論理位置に加える上下の単振動オフセットです。アレイの常時浮遊アニメーション
     * として全フェーズで描画位置へ適用し、移動中は周期を半分に速めます。
     */
    private fun bobOffset(session: GuideSession): Double {
        session.bobPhase += if (session.movingThisTick) BOB_RADIANS_MOVING else BOB_RADIANS_IDLE
        return sin(session.bobPhase) * BOB_AMPLITUDE * (1.0 + 0.15 * sin(session.movementNoisePhase * 0.7))
    }

    /** 速度はゆっくり変動させ、到着前に減速して急発進と急停止を避けます。 */
    private fun flightStep(session: GuideSession, flight: GuideCourseMath.FlightPath, speed: Double): Double {
        session.movementNoisePhase += 0.035
        val desired = speed / TICKS_PER_SECOND * (1.0 + 0.15 * sin(session.movementNoisePhase))
        val remaining = (flight.totalLength - flight.traveled).coerceAtLeast(0.0)
        val braking = kotlin.math.sqrt(2.0 * 0.02 * remaining)
        val target = minOf(desired, braking)
        session.speedPerTick += (target - session.speedPerTick).coerceIn(-0.02, 0.02)
        return session.speedPerTick.coerceAtLeast(0.001)
    }

    /**
     * 待機中は単振動による浮遊だけを行い、向きは中継点に設定された yaw を維持します。
     */
    private fun tickWaiting(session: GuideSession) {
        val point = session.route.points[session.nextIndex]
        val viewer = session.player.location
        // 距離にかかわらず案内対象を向きます。
        val (yaw, pitch) = GuideCourseMath.lookAngles(
            point.x, point.y + ALLAY_HEIGHT / 2.0, point.z,
            viewer.x, session.player.eyeLocation.y, viewer.z,
        )
        renderer.moveTo(session.player, session.entityId, point.x, point.y + bobOffset(session), point.z, yaw, pitch)
        session.x = point.x
        session.y = point.y
        session.z = point.z
        val progress = session.waypointProgress ?: GuideWaypointProgress(
            kotlin.math.ceil(settings.waypointDwellSeconds * TICKS_PER_SECOND).toLong(),
            kotlin.math.ceil(settings.messageWaitSecondsPerLine * point.message.size * TICKS_PER_SECOND).toLong(),
        ).also { session.waypointProgress = it }
        val loc = session.player.location
        val inRange = GuideWaypointProgress.contains(loc.x, loc.y, loc.z, point.x, point.y, point.z,
            settings.waypointRadius, settings.waypointVerticalRadius)
        when (progress.tick(inRange)) {
            GuideWaypointProgress.Event.NONE -> Unit
            GuideWaypointProgress.Event.MESSAGE -> sendWaypointMessage(session)
            GuideWaypointProgress.Event.MESSAGE_AND_ADVANCE -> { sendWaypointMessage(session); advanceWaypoint(session) }
            GuideWaypointProgress.Event.ADVANCE -> advanceWaypoint(session)
        }
    }

    /**
     * 計画した曲線経路に沿って飛行し、移動方向へ体と頭を向けます。
     * FLYING は次の中継点、RETURNING はコース復帰後の中継点へ向かいます。
     */
    private fun tickFlight(session: GuideSession) {
        val target = session.route.points[session.nextIndex]
        val speed = if (session.phase == Phase.FLYING) settings.flightSpeed else settings.followFlightSpeed
        // 経路が見つからない場合は静止し、チャンクの読込や障害物の変化を待って再試行します。
        val flight = session.flight ?: run {
            if (++session.pathRetryTicks % 100 == 0) {
                session.flight = planFlight(session, target)
                if (session.phase == Phase.FLYING && session.flight != null) session.coursePath = session.flight
            }
            return
        }
        session.pathRetryTicks = 0
        val pos = flight.advance(flightStep(session, flight, speed))
        session.x = pos.first
        session.y = pos.second
        session.z = pos.third
        session.movingThisTick = !flight.finished

        val viewer = session.player.eyeLocation
        val (yaw, pitch) = GuideCourseMath.lookAngles(session.x, session.y + ALLAY_HEIGHT / 2.0, session.z, viewer.x, viewer.y, viewer.z)
        val bob = bobOffset(session)
        renderer.moveTo(session.player, session.entityId, session.x, session.y + bob, session.z, yaw, pitch)

        if (flight.finished) {
            session.x = target.x
            session.y = target.y
            session.z = target.z
            session.flight = null
            session.speedPerTick = 0.0
            session.phase = Phase.WAITING
            renderer.moveTo(session.player, session.entityId, session.x, session.y + bob, session.z, yaw, pitch)
        }
    }

    /**
     * 追従モードです。アレイはプレイヤーから約2ブロックの占有可能な位置へ
     * 飛行して滞在し、静止中は次の中継点方向へ体と頭を
     * 向けて案内します。占有可能な位置が見つからない間は現在位置を維持します。
     *
     * 案内は二系統のタイマーで行います。長周期のシステムチャットと、
     * 短周期で切り替わるアレイ頭上のテキスト表示（効果音付き）です。
     */
    private fun tickFollowing(session: GuideSession) {
        session.followTicks++
        // 経路への移動方向にかかわらず、常にプレイヤー付近への追従を優先します。
        refreshAnchorIfNeeded(session)

        val flight = session.flight
        if (flight != null) {
            val pos = flight.advance(flightStep(session, flight, settings.followFlightSpeed))
            session.x = pos.first
            session.y = pos.second
            session.z = pos.third
            session.movingThisTick = !flight.finished
        }

        // 移動中は移動方向を、静止中は次の中継点を指し示します。
        val viewer = session.player.eyeLocation
        val (yaw, pitch) = GuideCourseMath.lookAngles(session.x, session.y + ALLAY_HEIGHT / 2.0, session.z, viewer.x, viewer.y, viewer.z)
        val bob = bobOffset(session)
        renderer.moveTo(session.player, session.entityId, session.x, session.y + bob, session.z, yaw, pitch)
        updateFollowDisplay(session, bob)

        // 長周期のシステムチャットです。最初の発火後にアレイが発光を開始します。
        val chatInterval = (settings.offCourseChatIntervalSeconds * TICKS_PER_SECOND).toInt().coerceAtLeast(1)
        if (session.followTicks % chatInterval == 0) {
            // 再翻訳せず表示済みの文を使い、言語変更のタイミングでも表示と一致させます。
            session.currentDisplayText?.let { sendPeriodicGuide(session.player, it) }
            if (!session.glowing) {
                session.glowing = true
                renderer.setGlowing(session.player, session.entityId, true)
            }
        }
    }

    /**
     * 追従中のアレイ頭上テキストです。短い周期ごとに内容を順送りで切り替え、
     * 切替時に効果音を再生します。アレイのCustomNameより高い位置に、
     * 常時 viewer 正面を向くテキストディスプレイで表示します。
     */
    private fun updateFollowDisplay(session: GuideSession, allayBob: Double) {
        val displayY = session.y + allayBob + DISPLAY_OFFSET
        if (session.displayEntityId == -1) {
            val texts = followDisplayTexts(session.player)
            if (texts.isEmpty()) return
            session.displayIndex = 0
            session.currentDisplayText = texts[0]
            session.displayEntityId = renderer.spawnTextDisplay(
                session.player, session.x, displayY, session.z,
                LegacyComponentSerializer.legacySection().deserialize(texts[0]),
            )
            playFollowCue(session)
            return
        }
        renderer.moveTo(session.player, session.displayEntityId, session.x, displayY, session.z, 0f, 0f)

        val interval = (settings.followDisplayIntervalSeconds * TICKS_PER_SECOND).toInt().coerceAtLeast(1)
        if (session.followTicks % interval != 0) return
        val texts = followDisplayTexts(session.player)
        if (texts.isEmpty()) return
        session.displayIndex = (session.displayIndex + 1) % texts.size
        session.currentDisplayText = texts[session.displayIndex]
        renderer.setTextDisplayText(
            session.player, session.displayEntityId,
            LegacyComponentSerializer.legacySection().deserialize(texts[session.displayIndex]),
        )
        playFollowCue(session)
    }

    /** 表示中の台詞と中断操作を同じチャットメッセージにまとめます。 */
    private fun sendPeriodicGuide(player: Player, message: String) {
        val serializer = LegacyComponentSerializer.legacySection()
        val button = serializer.deserialize(ChanponI18n.text(player, Keys.CHANPON_UTILITIES_GUIDE_STOP_BUTTON))
            .clickEvent(ClickEvent.runCommand("/guide stop"))
            .hoverEvent(HoverEvent.showText(serializer.deserialize(ChanponI18n.text(player, Keys.CHANPON_UTILITIES_GUIDE_STOP_HOVER))))
        player.sendMessage(serializer.deserialize(message).append(net.kyori.adventure.text.Component.space()).append(button))
    }
    private fun playFollowCue(session: GuideSession) {
        session.player.playSound(
            session.player.location.set(session.x, session.y, session.z),
            Sound.ENTITY_ALLAY_AMBIENT_WITH_ITEM,
            0.8f, 0.98f,
        )
    }

    private fun followDisplayTexts(player: Player): List<String> =
        ChanponI18n.list(player, Keys.CHANPON_UTILITIES_GUIDE_FOLLOW_DISPLAY)

    /**
     * プレイヤーが動いたときだけ追従アンカーを再計算し、ブロック占有判定で
     * 置ける位置を選びます。高さは現在のプレイヤーの足元を基準にします。
     */
    private fun refreshAnchorIfNeeded(session: GuideSession) {
        val loc = session.player.location
        // 頻繁な位置更新で探索が毎 tick 走らないよう、再計画の頻度を制限します。
        if (session.anchorValid && session.followTicks % 10 != 1) return
        if (!session.anchorValid && session.followTicks % 100 != 1) return
        if (!loc.x.isNaN() &&
            GuideCourseMath.distanceSq(loc.x, loc.y, loc.z, session.anchorPlayerX, session.anchorPlayerY, session.anchorPlayerZ) <= ANCHOR_REFRESH_MOVE_SQ &&
            session.anchorValid && (session.flight != null || session.followTicks % 100 != 0)
        ) return
        session.anchorPlayerX = loc.x
        session.anchorPlayerY = loc.y
        session.anchorPlayerZ = loc.z
        session.anchorValid = false
        val next = session.route.points[session.nextIndex]
        // プレイヤーの現在位置から候補を求め、上下移動にも追従します。
        val candidates = GuideFollowAnchor.candidates(loc.x, loc.y, loc.z, next.x, next.z)
        for ((x, y, z) in candidates) {
            if (canOccupyAllay(session.player.world, x, y, z)) {
                val flight = planFlightTo(session, x, y, z) ?: continue
                session.anchorValid = true
                session.flight = flight
                return
            }
        }
        // 占有可能な候補が全滅した場合は、古いアンカーへ向かわずその場に留まります。
        session.flight = null
    }

    /**
     * コース逸脱判定です。現在区間（直近完了点→次の中継点）を軸とする
     * 3Dカプセルの外に猶予時間より長く留まると追従モードへ入り、
     * 戻るとアレイは中継点へ帰還します。
     *
     * 1点目の完了前は「現在区間」が未定義のため、逸脱判定を行いません。
     */
    private fun checkOffCourse(session: GuideSession) {
        if (session.nextIndex == 0 || session.nextIndex >= session.route.points.size || session.waypointProgress?.messageSent == true) return
        val player = session.player
        val prev = session.route.points[session.nextIndex - 1]
        val next = session.route.points[session.nextIndex]
        val loc = player.location
        val points = session.coursePath?.points ?: listOf(
            Triple(prev.x, prev.y, prev.z), Triple(next.x, next.y, next.z))
        val onCourse = GuideCourseMath.isNearPath(loc.x, loc.y, loc.z, points, settings.courseRadius)
        if (onCourse) {
            session.offCourseTicks = 0
            if (session.phase == Phase.FOLLOWING) {
                exitFollowing(session)
            }
            return
        }
        session.offCourseTicks++
        val graceTicks = (settings.offCourseGraceSeconds * TICKS_PER_SECOND).toInt()
        if (session.phase == Phase.WAITING && session.offCourseTicks >= graceTicks) {
            session.phase = Phase.FOLLOWING
            session.pathRetryTicks = 0
            // 追従開始時には滞在が中断されるため、帰還後は計測し直します。
            session.waypointProgress = null
            session.anchorValid = false
            session.anchorPlayerY = Double.NaN
            session.followTicks = 0
            session.displayIndex = 0
            session.currentDisplayText = null
            session.flight = null
        }
    }

    /**
     * 追従モードを抜け、コース上の中継点へ帰還する状態へ遷移します。
     * 案内表示・発光を解除し、帰還経路を計画します。
     */
    private fun exitFollowing(session: GuideSession) {
        session.phase = Phase.RETURNING
        session.currentDisplayText = null
if (session.displayEntityId != -1) {
            renderer.destroy(session.player, session.displayEntityId)
            session.displayEntityId = -1
        }
        if (session.glowing) {
            session.glowing = false
            renderer.setGlowing(session.player, session.entityId, false)
        }
        session.flight = planFlight(session, session.route.points[session.nextIndex])
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

        // 常時の単振動アニメーションです。待機・静止時の周期は約3秒、
        // 移動中は半分の約1.5秒に速めて飛行感を出します。
        const val BOB_RADIANS_IDLE = Math.PI / 30.0
        const val BOB_RADIANS_MOVING = Math.PI / 15.0
        const val BOB_AMPLITUDE = 0.15
        /** 見た目のスケールに合わせて占有判定も拡大します。 */
        const val ALLAY_HALF_WIDTH = GuideAllayDimensions.HALF_WIDTH
        const val ALLAY_HEIGHT = GuideAllayDimensions.HEIGHT
        /** 追従アンカーの再計算を行うプレイヤー移動量の閾値（二乗）です。 */
        const val ANCHOR_REFRESH_MOVE_SQ = 0.25 * 0.25
        /** 移動方向へ向きを変える最小移動量（二乗）です。 */
        const val FACING_MIN_MOVE_SQ = 0.002 * 0.002
        /** 追従表示のテキストディスプレイを置く、足元からの高さです。 */
        const val DISPLAY_OFFSET = 1.55
    }
}
