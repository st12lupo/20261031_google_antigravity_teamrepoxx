package com.example.receiptreminder.fake

import com.example.receiptreminder.domain.model.Store
import com.example.receiptreminder.domain.notify.StoreArrivalNotifier
import com.example.receiptreminder.domain.notify.StoreNotificationSender
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
    private val notificationSettingsRepository: NotificationSettingsRepository,
    private val currentTimeProvider: () -> LocalTime = { LocalTime.now() },
    private val todayProvider: () -> LocalDate = { LocalDate.now() }
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
        val notifier = StoreArrivalNotifier(
            storeRepository = storeRepository,
            shoppingListRepository = shoppingListRepository,
            notificationSettingsRepository = notificationSettingsRepository,
            sender = StoreNotificationSender { _, _, message ->
                lastSimulatedNotificationMessage = message
                true
            },
            currentTimeProvider = currentTimeProvider,
            todayProvider = todayProvider
        )
        return notifier.onStoreEntered(storeId)
    }

    fun isGeofenceRegistered(storeId: String): Boolean {
        return registeredGeofences.containsKey(storeId)
    }
}
