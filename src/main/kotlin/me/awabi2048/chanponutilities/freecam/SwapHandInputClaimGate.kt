package me.awabi2048.chanponutilities.freecam

import com.awabi2048.ccsystem.api.input.PlayerInteractionChannel
import com.awabi2048.ccsystem.api.input.PlayerInteractionClaimService
import java.util.UUID

/**
 * Fキー入力をCC-System側の機能が占有しているかをFreecamへ伝える境界です。
 *
 * FreecamがCC-Systemのclaim表現や所有者名へ依存すると、入力基盤の変更時に
 * ショートカット判定まで巻き込まれるため、ここでは「譲るべきか」だけを公開します。
 */
fun interface SwapHandInputClaimGate {
    fun isClaimed(playerId: UUID): Boolean
}

/** CC-Systemの入力チャネルclaimをFreecam向けの単純な判定へ変換します。 */
class CCSystemSwapHandInputClaimGate(
    private val claims: PlayerInteractionClaimService,
) : SwapHandInputClaimGate {
    override fun isClaimed(playerId: UUID): Boolean =
        claims.isClaimed(playerId, PlayerInteractionChannel.SWAP_HAND)
}
