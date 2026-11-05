package me.awabi2048.chanponutilities.guide

/** 設定順を保持したまま、今回新たに入った最初の領域を選びます。 */
internal object GuideAutoStart {
    data class Position(val world: String, val x: Double, val y: Double, val z: Double)

    fun select(routes: List<GuideRoute>, from: Position?, to: Position): GuideRoute? = routes.firstOrNull { route ->
        val area = route.autoStart ?: return@firstOrNull false
        area.contains(to.world, to.x, to.y, to.z) &&
            (from == null || !area.contains(from.world, from.x, from.y, from.z))
    }
}
