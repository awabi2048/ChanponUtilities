package me.awabi2048.chanponutilities.guide

import com.comphenix.protocol.PacketType
import com.comphenix.protocol.ProtocolLibrary
import com.comphenix.protocol.events.ListenerPriority
import com.comphenix.protocol.events.PacketAdapter
import com.comphenix.protocol.events.PacketContainer
import com.comphenix.protocol.events.PacketEvent
import com.comphenix.protocol.events.PacketListener
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
import kotlin.math.floor

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

        sendMetadata(viewer, id, displayName)
        return id
    }

    private fun sendMetadata(viewer: Player, id: Int, displayName: net.kyori.adventure.text.Component) {
        val nameJson = GsonComponentSerializer.gson().serialize(displayName)
        val nameHandle = WrappedChatComponent.fromJson(nameJson).handle
        val values = listOf(
            // index 0: shared flags。bit 0x40 が glowing です。
            WrappedDataValue(ID_SHARED_FLAGS, byteSerializer(), FLAG_GLOWING),
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
     * 仮想アレイを絶対座標へ移動し、体と頭の向きを更新します。
     *
     * client は teleport を数 tick かけて補間するため、毎 tick 小刻みに
     * 送れば直線飛行が滑らかに見えます。
     */
    fun moveTo(viewer: Player, id: Int, x: Double, y: Double, z: Double, yaw: Float, pitch: Float) {
        val teleport = manager.createPacket(PacketType.Play.Server.ENTITY_TELEPORT)
        teleport.integers.write(0, id)
        teleport.doubles.write(0, x)
        teleport.doubles.write(1, y)
        teleport.doubles.write(2, z)
        teleport.bytes.write(0, toPackedByte(yaw))
        teleport.bytes.write(1, toPackedByte(pitch))
        teleport.booleans.write(0, false)
        send(viewer, teleport)

        val headRotation = manager.createPacket(PacketType.Play.Server.ENTITY_HEAD_ROTATION)
        headRotation.integers.write(0, id)
        headRotation.bytes.write(0, toPackedByte(yaw))
        send(viewer, headRotation)
    }

    fun destroy(viewer: Player, id: Int) {
        managedIds -= id
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

    private companion object {
        /** CC-System の仮想描画採番域（1_600_000_000 起点）と重ならない高位域です。 */
        const val VIRTUAL_ID_BASE: Int = 1_700_000_000

        // Entity 基底の metadata index です。
        const val ID_SHARED_FLAGS: Int = 0
        const val ID_CUSTOM_NAME: Int = 2
        const val ID_NAME_VISIBLE: Int = 3

        const val FLAG_GLOWING: Byte = 0x40

        fun toPackedByte(degrees: Float): Byte = floor(degrees * 256.0f / 360.0f).toInt().toByte()
    }
}
