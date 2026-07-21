package me.awabi2048.chanponutilities.freecam

internal fun shouldShowFreecamStartMessage(
    lastShownAtMillis: Long?,
    nowMillis: Long,
    cooldownMillis: Long
): Boolean {
    require(cooldownMillis >= 0L) { "cooldownMillis must not be negative" }
    if (lastShownAtMillis == null || nowMillis < lastShownAtMillis) return true
    return nowMillis - lastShownAtMillis >= cooldownMillis
}
