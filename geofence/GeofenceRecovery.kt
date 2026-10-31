package geofence

import com.google.android.gms.location.GeofenceStatusCodes

/**
 * ジオフェンスのエラー Intent を受けたときの回復手順。
 * Play services はネットワーク位置情報(NLP)が無効になると登録を消し、
 * GEOFENCE_NOT_AVAILABLE のエラーを送る。消えた登録は自動では戻らない。
 * そこで同期済みの印を消し(次の onStart で必ず再登録)、Worker を予約して復帰まで待つ。
 */
class GeofenceRecovery(
    private val invalidateSyncState: () -> Unit,
    private val scheduleResync: () -> Unit
) {
    /** 回復の手順を始めたら true。登録が消えるエラーでなければ何もせず false */
    fun onError(errorCode: Int): Boolean {
        if (!registrationLost(errorCode)) return false
        invalidateSyncState()
        scheduleResync()
        return true
    }

    companion object {
        fun registrationLost(errorCode: Int): Boolean =
            errorCode == GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE
    }
}
