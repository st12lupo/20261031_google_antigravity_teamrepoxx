package com.example.receiptreminder.fake

import com.example.receiptreminder.domain.model.StockStatus
import com.example.receiptreminder.domain.model.Store
import com.example.receiptreminder.domain.repository.GeofenceManager
import com.example.receiptreminder.domain.repository.NotificationSettingsRepository
import com.example.receiptreminder.domain.repository.ShoppingListRepository
import com.example.receiptreminder.domain.repository.StoreRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalTime

class FakeGeofenceManager(
    private val storeRepository: StoreRepository,
    private val shoppingListRepository: ShoppingListRepository,
    private val notificationSettingsRepository: NotificationSettingsRepository
) : GeofenceManager {

    private val registeredGeofences = mutableMapOf<String, Store>()
    var lastSimulatedNotificationMessage: String? = null
        private set

    override suspend fun registerGeofence(store: Store) {
        registeredGeofences[store.id] = store
    }

    override suspend fun unregisterGeofence(storeId: String) {
        registeredGeofences.remove(storeId)
    }

    override suspend fun recheckAllGeofences() {
        registeredGeofences.clear()
        val stores = storeRepository.getStoresStream().first()
        stores.filter { it.notifyEnabled }.forEach {
            registeredGeofences[it.id] = it
        }
    }

    override suspend fun simulateStoreVisit(storeId: String): Boolean {
        val stores = storeRepository.getStoresStream().first()
        val store = stores.firstOrNull { it.id == storeId } ?: return false

        // 1. 店舗の通知がオンか確認（S-3）
        if (!store.notifyEnabled) return false

        // 2. 通知時間帯内か確認（S-4）
        val timeRange = notificationSettingsRepository.getNotificationTimeRangeStream().first()
        if (!timeRange.isWithinRange(LocalTime.now())) return false

        // 3. 同じ店舗での通知は1日1回まで（S-6）
        if (store.lastNotifiedOn == LocalDate.now()) return false

        // 4. なくなりそうな商品があるか確認（S-5）
        val shoppingItems = shoppingListRepository.getShoppingItemsStream().first()
        val runningOutItems = shoppingItems.filter {
            it.product.status == StockStatus.RUNNING_OUT ||
                    it.product.status == StockStatus.OUT_OF_STOCK_OR_OVERDUE
        }
        if (runningOutItems.isEmpty()) return false

        // 5. 通知メッセージ作成（例:「洗剤など 3 点がなくなりそうです」）
        val firstItemName = runningOutItems.first().product.name
        val count = runningOutItems.size
        lastSimulatedNotificationMessage = if (count > 1) {
            "${firstItemName}など ${count} 点がなくなりそうです"
        } else {
            "${firstItemName}がなくなりそうです"
        }

        // 店舗の最終通知日を更新
        storeRepository.updateStore(store.copy(lastNotifiedOn = LocalDate.now()))
        return true
    }

    fun isGeofenceRegistered(storeId: String): Boolean {
        return registeredGeofences.containsKey(storeId)
    }
}
