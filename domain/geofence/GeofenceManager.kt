package domain.geofence

import domain.model.Store

interface GeofenceManager {
    /**
     * 通知が有効(notifyEnabled == true)な店舗一覧を端末のジオフェンスに登録する。
     */
    suspend fun registerGeofences(stores: List<Store>)

    /**
     * 登録済みのすべての店舗ジオフェンスを解除する。
     */
    suspend fun unregisterAllGeofences()
}
