package me.awabi2048.chanponutilities.guide

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.logging.Level

/**
 * `guide_routes.yml` の読み込み・検証・書き戻しを担当します。
 *
 * 読み込みは全ルートを検証してから原子的に採用します。1件でも不正なら
 * 全体を拒否し、直前の有効なルート集合を保持します。
 * 実行中のガイドセッションは開始時のルート snapshot を保持し続けるため、
 * reload や setpoint の反映は新規セッションからになります。
 */
class GuideRouteStore(
    private val plugin: JavaPlugin,
) {
    private val filePath: Path = plugin.dataFolder.toPath().resolve(FILE_NAME)

    @Volatile
    private var routes: Map<String, GuideRoute> = emptyMap()

    fun all(): List<GuideRoute> = routes.values.toList()

    fun find(id: String): GuideRoute? = routes[id]

    sealed interface LoadResult {
        data class Loaded(val routeCount: Int) : LoadResult
        data class Invalid(val errors: List<String>) : LoadResult
    }

    /**
     * ファイルを読み込み、検証を通った場合のみ現在のルート集合を置き換えます。
     * ファイルが存在しなければプラグイン同梱の初期ファイルを展開します。
     */
    fun load(): LoadResult {
        ensureFileExists()
        val yaml = YamlConfiguration()
        runCatching { yaml.load(filePath.toFile()) }
            .onFailure { return LoadResult.Invalid(listOf("guide_routes.yml の読み込みに失敗しました: ${it.message}")) }
        return adopt(yaml)
    }

    fun reload(): LoadResult = load()

    private fun adopt(yaml: YamlConfiguration): LoadResult {
        return when (val parsed = GuideRouteCodec.parse(yaml.getConfigurationSection("routes"))) {
            is GuideRouteCodec.ParseResult.Success -> {
                routes = parsed.routes
                LoadResult.Loaded(parsed.routes.size)
            }
            is GuideRouteCodec.ParseResult.Failure -> LoadResult.Invalid(parsed.errors)
        }
    }

    sealed interface SetPointResult {
        /** 既存ルートの中継点を書き換え、または末尾へ追加しました。 */
        data class Saved(val route: GuideRoute, val index: Int, val appended: Boolean) : SetPointResult

        /** ルートが存在しなかったため、中継点1つだけの新規ルートを作成しました。 */
        data class RouteCreated(val route: GuideRoute) : SetPointResult

        /** index が範囲外（既存点数+1 より大きい、または 1 未満）です。 */
        data class InvalidIndex(val index: Int) : SetPointResult

        /** ファイルの読み書き・再検証に失敗しました。 */
        data class Failed(val reason: String) : SetPointResult
    }

    /**
     * 実行者の現在位置・向きを中継点として保存します。
     *
     * `index`（1始まり）が既存範囲内なら座標・向きのみ上書きして `message` を保持し、
     * 末尾+1 なら追加、それより大きいまたは 0 以下なら不正として拒否します。
     * ルートが存在しなければ、その1点だけを持つ新規ルートを作成します。
     */
    @Synchronized
    fun setPoint(
        routeId: String,
        index: Int,
        worldName: String,
        x: Double,
        y: Double,
        z: Double,
        yaw: Float,
        pitch: Float,
    ): SetPointResult {
        if (index < 1) return SetPointResult.InvalidIndex(index)

        val yaml = YamlConfiguration()
        if (Files.exists(filePath)) {
            runCatching { yaml.load(filePath.toFile()) }
                .onFailure {
                    plugin.logger.log(Level.SEVERE, "guide_routes.yml の読み込みに失敗しました", it)
                    return SetPointResult.Failed("load: ${it.message}")
                }
        }
        val routesSection = yaml.getConfigurationSection("routes")
            ?: yaml.createSection("routes")

        val point = GuidePoint(x, y, z, yaw, pitch)
        val section = routesSection.getConfigurationSection(routeId)
        if (section == null) {
            // 新規ルート: index の値に依らず先頭の中継点として作成します。
            val created = routesSection.createSection(routeId)
            created.set("world", worldName)
            created.set("points", listOf(pointToMap(point)))
            saveYaml(yaml)
            adopt(yaml)
            return SetPointResult.RouteCreated(find(routeId) ?: GuideRoute(routeId, worldName, null, listOf(point)))
        }

        val size = section.getMapList("points").size
        if (index > size + 1) return SetPointResult.InvalidIndex(index)
        GuideRouteCodec.writePoint(section, index - 1, point)
        saveYaml(yaml)
        val adopted = adopt(yaml) as? LoadResult.Loaded
            ?: return SetPointResult.Failed("re-validation after save failed")
        val route = find(routeId) ?: return SetPointResult.Failed("route missing after save")
        return SetPointResult.Saved(route, index, appended = index > size)
    }

    private fun pointToMap(point: GuidePoint): Map<String, Any> = linkedMapOf(
        "x" to point.x,
        "y" to point.y,
        "z" to point.z,
        "yaw" to point.yaw.toDouble(),
        "pitch" to point.pitch.toDouble(),
    )

    private fun ensureFileExists() {
        if (Files.exists(filePath)) return
        runCatching {
            Files.createDirectories(filePath.parent)
            plugin.getResource(FILE_NAME)?.use { input -> Files.copy(input, filePath) }
        }.onFailure { plugin.logger.log(Level.WARNING, "guide_routes.yml の初期展開に失敗しました", it) }
    }

    private fun saveYaml(yaml: YamlConfiguration) {
        val temporary = filePath.resolveSibling("${filePath.fileName}.tmp")
        yaml.save(temporary.toFile())
        val verified = YamlConfiguration()
        verified.load(temporary.toFile())
        runCatching {
            Files.move(temporary, filePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        }.getOrElse {
            Files.move(temporary, filePath, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private companion object {
        const val FILE_NAME = "guide_routes.yml"
    }
}
