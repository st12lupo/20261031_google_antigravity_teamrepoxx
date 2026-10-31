package com.example.receiptreminder.domain.repository

import com.example.receiptreminder.domain.model.Store

/**
 * ジオフェンス管理インターフェース
 */
interface GeofenceManager {
    /** 店舗ジオフェンスの登録 */
    suspend fun registerGeofence(store: Store)

    /** 店舗ジオフェンスの登録解除 */
    suspend fun unregisterGeofence(storeId: String)

    /** 端末再起動・アプリ更新後のジオフェンス全再登録 */
    suspend fun recheckAllGeofences()

    /**
     * アプリを開いたときの同期。店舗・権限・端末の状態が前回の同期から変わらないときは登録し直さない
     * (登録し直すと、店の中にいるときの ENTER を取りこぼすため)。既定は常に登録し直す。
     */
    suspend fun syncIfChanged() = recheckAllGeofences()

    /**
     * テスト・デバッグ用：指定店舗への進入シミュレーション
     * @return 通知送信条件を満たして通知が行われた場合 true
     */
    suspend fun simulateStoreVisit(storeId: String): Boolean
}
