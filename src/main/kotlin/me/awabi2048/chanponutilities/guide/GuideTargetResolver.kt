package me.awabi2048.chanponutilities.guide

/** 管理者指定はオンライン名と @p のみを受け付けます。 */
internal object GuideTargetResolver {
    fun <T> resolve(value: String, nearest: () -> T?, byName: (String) -> T?): T? = when {
        value == "@p" -> nearest()
        value.startsWith("@") -> null
        else -> byName(value)
    }
}
