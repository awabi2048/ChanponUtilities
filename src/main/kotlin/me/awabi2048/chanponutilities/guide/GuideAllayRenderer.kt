package me.awabi2048.chanponutilities.guide

import com.comphenix.protocol.PacketType
import com.comphenix.protocol.ProtocolLibrary
import com.comphenix.protocol.events.ListenerPriority
import com.comphenix.protocol.events.PacketAdapter
import com.comphenix.protocol.events.PacketContainer
import com.comphenix.protocol.events.PacketEvent
import com.comphenix.protocol.events.PacketListener
import com.comphenix.protocol.wrappers.WrappedAttribute
import com.comphenix.protocol.wrappers.WrappedChatComponent
import com.comphenix.protocol.wrappers.WrappedDataValue
import com.comphenix.protocol.wrappers.WrappedDataWatcher
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.bukkit.util.Vector
import java.util.Optional
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.logging.Level
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * ProtocolLib による client-only の仮想アレイ描画です。
 *
 * Bukkit Entity を spawn せず、対象プレイヤーへ Spawn / Metadata / Teleport /
 * HeadRotation / Destroy packet だけを送ります。サーバー world 上に実体を
 * 持たないため、他プレイヤーからは見えず、`@e` 走査・他プラグイン・
 * ワールドの物理とも一切干渉しません。
 *
 * クリック検知は client 側が実在すると信じて送る UseEntity packet を
 * 横取りして行います（攻撃・インタラクトの区別はせず、いずれも
 * 「アレイをクリックした」操作として扱います）。
 */
internal class GuideAllayRenderer(
    private val plugin: Plugin,
    private val onInteract: (player: Player, entityId: Int) -> Unit,
) {
    private val manager by lazy { ProtocolLibrary.getProtocolManager() }
    private val idAllocator = AtomicInteger(VIRTUAL_ID_BASE)
    private val managedIds: MutableSet<Int> = ConcurrentHashMap.newKeySet()

    // client が知る直近の絶対位置です。移動は相対 packet（PosRot）で送るため、
    // 送信済み位置との差分から相対量を求めます。
    private val sentPositions = ConcurrentHashMap<Int, Vector>()

    private val interactListener: PacketListener = object : PacketAdapter(
        plugin,
        ListenerPriority.NORMAL,
        PacketType.Play.Client.USE_ENTITY,
    ) {
        override fun onPacketReceiving(event: PacketEvent) {
            val entityId = runCatching { event.packet.integers.read(0) }.getOrNull() ?: return
            if (entityId !in managedIds) return
            // 仮想エンティティのパケットをサーバー本体へ流すと「存在しない
            // エンティティへのインタラクト」警告になるため、必ずキャンセルします。
            event.isCancelled = true
            val player = event.player ?: return
            Bukkit.getScheduler().runTask(plugin, Runnable {
                if (player.isOnline) onInteract(player, entityId)
            })
        }
    }

    fun register() {
        manager.addPacketListener(interactListener)
    }

    fun unregister() {
        runCatching { manager.removePacketListener(interactListener) }
        managedIds.clear()
        sentPositions.clear()
    }

    /**
     * 仮想アレイを viewer へ出現させます。
     * y はアレイの足元（エンティティ座標）です。
     */
    fun spawn(
        viewer: Player,
        x: Double, y: Double, z: Double,
        yaw: Float, pitch: Float,
        displayName: net.kyori.adventure.text.Component,
    ): Int {
        val id = idAllocator.getAndIncrement()
        managedIds += id

        val spawn = manager.createPacket(PacketType.Play.Server.SPAWN_ENTITY)
        spawn.integers.write(0, id)
        spawn.integers.write(1, 0)
        spawn.getUUIDs().write(0, UUID.randomUUID())
        spawn.entityTypeModifier.write(0, EntityType.ALLAY)
        spawn.doubles.write(0, x)
        spawn.doubles.write(1, y)
        spawn.doubles.write(2, z)
        // Living entity 用の spawn packet は (xRot, yRot, yHeadRot) の順です。
        spawn.bytes.write(0, toPackedByte(pitch))
        spawn.bytes.write(1, toPackedByte(yaw))
        spawn.bytes.write(2, toPackedByte(yaw))
        spawn.vectors.write(0, Vector(0, 0, 0))
        send(viewer, spawn)
        sentPositions[id] = Vector(x, y, z)

        sendMetadata(viewer, id, displayName)
        sendScale(viewer, id)
        return id
    }

    /**
     * scale 属性で仮想アレイの見た目を拡大します。26.2 系ではエンティティの
     * 拡縮は metadata ではなく scale 属性として同期するため、
     * UPDATE_ATTRIBUTES packet で送ります。
     */
    private fun sendScale(viewer: Player, id: Int) {
        runCatching {
            val packet = manager.createPacket(PacketType.Play.Server.UPDATE_ATTRIBUTES)
            packet.integers.write(0, id)
            val scale = WrappedAttribute.newBuilder()
                .attributeKey("scale")
                .baseValue(ALLAY_SCALE)
                .packet(packet)
                .build()
            packet.attributeCollectionModifier.write(0, listOf(scale))
            send(viewer, packet)
        }.onFailure { failure ->
            plugin.logger.log(Level.WARNING, "ガイドアレイの scale 属性送信に失敗しました: viewer=${viewer.name}", failure)
        }
    }

    private fun sendMetadata(viewer: Player, id: Int, displayName: net.kyori.adventure.text.Component) {
        val nameJson = GsonComponentSerializer.gson().serialize(displayName)
        val nameHandle = WrappedChatComponent.fromJson(nameJson).handle
        val values = listOf(
            // index 0: shared flags。bit 0x40 が glowing です。平常時は消灯し、
            // コース逸脱の案内中だけ点けます。
            WrappedDataValue(ID_SHARED_FLAGS, byteSerializer(), 0x00.toByte()),
            // index 2: カスタム名（Optional<ChatComponent>）。
            WrappedDataValue(ID_CUSTOM_NAME, optionalChatSerializer(), Optional.of(nameHandle)),
            // index 3: カスタム名の常時表示。
            WrappedDataValue(ID_NAME_VISIBLE, booleanSerializer(), true),
        )
        val packet = manager.createPacket(PacketType.Play.Server.ENTITY_METADATA)
        packet.integers.write(0, id)
        packet.dataValueCollectionModifier.write(0, values)
        send(viewer, packet)
    }

    /**
     * アレイの発光を切り替えます。コース逸脱の案内が続く間だけ点灯させ、
     * 平常時・コース復帰後は消灯する演出です。
     */
    fun setGlowing(viewer: Player, id: Int, glowing: Boolean) {
        val packet = manager.createPacket(PacketType.Play.Server.ENTITY_METADATA)
        packet.integers.write(0, id)
        packet.dataValueCollectionModifier.write(
            0,
            listOf(WrappedDataValue(ID_SHARED_FLAGS, byteSerializer(), if (glowing) FLAG_GLOWING else 0x00.toByte())),
        )
        send(viewer, packet)
    }

    /**
     * アレイ頭上の案内文を表示する仮想テキストディスプレイを出現させます。
     * 常時 viewer へ向く CENTER billboard で、y は表示原点の座標です。
     */
    fun spawnTextDisplay(
        viewer: Player,
        x: Double, y: Double, z: Double,
        text: net.kyori.adventure.text.Component,
    ): Int {
        val id = idAllocator.getAndIncrement()
        managedIds += id

        val spawn = manager.createPacket(PacketType.Play.Server.SPAWN_ENTITY)
        spawn.integers.write(0, id)
        spawn.integers.write(1, 0)
        spawn.getUUIDs().write(0, UUID.randomUUID())
        spawn.entityTypeModifier.write(0, EntityType.TEXT_DISPLAY)
        spawn.doubles.write(0, x)
        spawn.doubles.write(1, y)
        spawn.doubles.write(2, z)
        spawn.bytes.write(0, 0.toByte())
        spawn.bytes.write(1, 0.toByte())
        spawn.bytes.write(2, 0.toByte())
        spawn.vectors.write(0, Vector(0, 0, 0))
        send(viewer, spawn)
        sentPositions[id] = Vector(x, y, z)

        setTextDisplayText(viewer, id, text)
        return id
    }

    /** テキストディスプレイの本文を更新します。 */
    fun setTextDisplayText(
        viewer: Player,
        id: Int,
        text: net.kyori.adventure.text.Component,
    ) {
        val textJson = GsonComponentSerializer.gson().serialize(text)
        val textHandle = WrappedChatComponent.fromJson(textJson).handle
        val values = listOf(
            // index 15: billboard 制約。3 は常時 viewer 正面の CENTER です。
            WrappedDataValue(ID_BILLBOARD, byteSerializer(), BILLBOARD_CENTER),
            // index 23: 表示テキスト（Component）。
            WrappedDataValue(ID_TEXT, chatSerializer(), textHandle),
            // index 24: 行幅です。
            WrappedDataValue(ID_LINE_WIDTH, intSerializer(), TEXT_LINE_WIDTH),
            // index 25: 背景色（半透明の黒）です。
            WrappedDataValue(ID_BACKGROUND, intSerializer(), TEXT_BACKGROUND_ARGB),
            // index 26: テキスト不透明度。-1 が既定値です。
            WrappedDataValue(ID_TEXT_OPACITY, byteSerializer(), (-1).toByte()),
            // index 27: スタイル flags。0x01=影付き、0x04=既定背景です。
            WrappedDataValue(ID_STYLE_FLAGS, byteSerializer(), STYLE_SHADOW_OR_DEFAULT_BG),
        )
        val packet = manager.createPacket(PacketType.Play.Server.ENTITY_METADATA)
        packet.integers.write(0, id)
        packet.dataValueCollectionModifier.write(0, values)
        send(viewer, packet)
    }

    /**
     * 仮想アレイを絶対座標へ移動し、体と頭の向きを更新します。
     *
     * 26.2 系では teleport packet が PositionMoveRotation レコード構成に変わり
     * 素の座標を直接書けないため、毎 tick の小刻みな移動には相対移動 packet
     * （REL_ENTITY_MOVE_LOOK）を使います。client は受信位置へ数 tick かけて
     * 補間するため、毎 tick 送れば直線飛行が滑らかに見えます。
     */
    fun moveTo(viewer: Player, id: Int, x: Double, y: Double, z: Double, yaw: Float, pitch: Float) {
        val previous = sentPositions[id] ?: Vector(x, y, z)
        val dx = x - previous.x
        val dy = y - previous.y
        val dz = z - previous.z

        // 相対移動量は 1/4096 ブロック単位の short で ±8 ブロックまでしか
        // 一度に表せません。普段の飛行では 1 hop ですが、稀な大移動に備えて
        // 超過時は分割して順に送ります。
        val span = maxOf(abs(dx), abs(dy), abs(dz))
        val hops = maxOf(1, ceil(span / RELATIVE_MOVE_LIMIT).toInt())
        var sent = previous
        for (step in 1..hops) {
            val t = step.toDouble() / hops
            val tx = previous.x + dx * t
            val ty = previous.y + dy * t
            val tz = previous.z + dz * t

            val move = manager.createPacket(PacketType.Play.Server.REL_ENTITY_MOVE_LOOK)
            move.integers.write(0, id)
            move.shorts.write(0, toMoveAxis(tx - sent.x))
            move.shorts.write(1, toMoveAxis(ty - sent.y))
            move.shorts.write(2, toMoveAxis(tz - sent.z))
            move.bytes.write(0, toPackedByte(yaw))
            move.bytes.write(1, toPackedByte(pitch))
            // フィールド順は (onGround, hasRot, hasPos) です。位置・回転を
            // 適用させるため hasRot / hasPos を立てます。
            move.booleans.write(0, false)
            move.booleans.write(1, true)
            move.booleans.write(2, true)
            send(viewer, move)
            sent = Vector(tx, ty, tz)
        }
        sentPositions[id] = Vector(x, y, z)

        val headRotation = manager.createPacket(PacketType.Play.Server.ENTITY_HEAD_ROTATION)
        headRotation.integers.write(0, id)
        headRotation.bytes.write(0, toPackedByte(yaw))
        send(viewer, headRotation)
    }

    fun destroy(viewer: Player, id: Int) {
        managedIds -= id
        sentPositions.remove(id)
        if (!viewer.isOnline) return
        val packet = manager.createPacket(PacketType.Play.Server.ENTITY_DESTROY)
        packet.intLists.write(0, listOf(id))
        send(viewer, packet)
    }

    fun isManaged(entityId: Int): Boolean = entityId in managedIds

    private fun send(viewer: Player, packet: PacketContainer) {
        runCatching { manager.sendServerPacket(viewer, packet) }.onFailure { failure ->
            plugin.logger.log(Level.WARNING, "ガイドアレイの packet 送信に失敗しました: viewer=${viewer.name}", failure)
        }
    }

    private fun byteSerializer(): WrappedDataWatcher.Serializer =
        WrappedDataWatcher.Registry.get(Byte::class.javaObjectType, false)

    private fun booleanSerializer(): WrappedDataWatcher.Serializer =
        WrappedDataWatcher.Registry.get(Boolean::class.javaObjectType, false)

    private fun optionalChatSerializer(): WrappedDataWatcher.Serializer =
        WrappedDataWatcher.Registry.getChatComponentSerializer(true)

    private fun chatSerializer(): WrappedDataWatcher.Serializer =
        WrappedDataWatcher.Registry.getChatComponentSerializer()

    private fun intSerializer(): WrappedDataWatcher.Serializer =
        WrappedDataWatcher.Registry.get(Int::class.javaObjectType, false)

    private companion object {
        /** CC-System の仮想描画採番域（1_600_000_000 起点）と重ならない高位域です。 */
        const val VIRTUAL_ID_BASE: Int = 1_700_000_000

        // Entity 基底の metadata index です。
        const val ID_SHARED_FLAGS: Int = 0
        const val ID_CUSTOM_NAME: Int = 2
        const val ID_NAME_VISIBLE: Int = 3

        // Display/TextDisplay の metadata index です（26.2 の defineSynchedData 順）。
        const val ID_BILLBOARD: Int = 15
        const val ID_TEXT: Int = 23
        const val ID_LINE_WIDTH: Int = 24
        const val ID_BACKGROUND: Int = 25
        const val ID_TEXT_OPACITY: Int = 26
        const val ID_STYLE_FLAGS: Int = 27

        const val FLAG_GLOWING: Byte = 0x40
        const val BILLBOARD_CENTER: Byte = 3
        const val TEXT_LINE_WIDTH: Int = 200
        const val TEXT_BACKGROUND_ARGB: Int = 0x40000000
        const val STYLE_SHADOW_OR_DEFAULT_BG: Byte = 0x05

        /** ガイドアレイの見た目スケールです。 */
        const val ALLAY_SCALE: Double = 1.5

        /** PosRot が一度に表せる相対移動幅（±8 ブロックの手前で安全側に切ります）。 */
        const val RELATIVE_MOVE_LIMIT: Double = 7.5

        fun toPackedByte(degrees: Float): Byte = floor(degrees * 256.0f / 360.0f).toInt().toByte()

        fun toMoveAxis(deltaBlocks: Double): Short =
            (deltaBlocks * 4096.0).roundToInt().toShort()
    }
}
