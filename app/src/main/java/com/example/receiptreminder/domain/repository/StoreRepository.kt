package com.example.receiptreminder.domain.repository

import com.example.receiptreminder.domain.model.NotificationTimeRange
import com.example.receiptreminder.domain.model.Store
import kotlinx.coroutines.flow.Flow

/**
 * 登録店舗の管理リポジトリ
 */
interface StoreRepository {
    /** 登録店舗一覧のストリーム */
    fun getStoresStream(): Flow<List<Store>>

    /** 現在地を店舗として登録（既定半径 200m） */
    suspend fun registerCurrentLocation(name: String, radiusMeters: Int = 200): Store

    /** 店舗情報の更新（通知オン/オフ、半径、店名など） */
    suspend fun updateStore(store: Store)

    /** 店舗の削除 */
    suspend fun deleteStore(storeId: String)

    /** 全履歴と店舗の全件削除（D-1 要件） */
    suspend fun clearAllData()
}

/**
 * 通知設定リポジトリ
 */
interface NotificationSettingsRepository {
    /** 通知時間帯のストリーム */
    fun getNotificationTimeRangeStream(): Flow<NotificationTimeRange>

    /** 通知時間帯の更新 */
    suspend fun setNotificationTimeRange(range: NotificationTimeRange)
}
