package me.awabi2048.chanponutilities.guide

/** delay がない行は地点で、ある行は出発後の指定 tick に送信します。 */
internal data class GuideMessageLine(val text: String, val delay: Long?) {
    companion object {
        fun parse(raw: Map<*, *>): GuideMessageLine? {
            val text = raw["text"] as? String ?: return null
            if (!raw.containsKey("delay")) return GuideMessageLine(text, null)
            val number = raw["delay"] as? Number ?: return null
            val value = number.toDouble()
            if (!value.isFinite() || value < 0.0 || value % 1.0 != 0.0 || value > Int.MAX_VALUE) return null
            return GuideMessageLine(text, value.toLong())
        }
    }
}
