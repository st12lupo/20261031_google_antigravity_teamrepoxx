package com.example.receiptreminder.data.bridge

import com.example.receiptreminder.domain.model.Store as UiStore
import com.example.receiptreminder.domain.repository.StoreRepository
import domain.model.Store as DbStore
import domain.repository.ReminderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class RealStoreRepository(
    private val reminderRepository: ReminderRepository,
    private val currentLocationProvider: suspend () -> Pair<Double, Double>? = { null },
    private val trigger: DataUpdateTrigger = DataUpdateTrigger(),
    private val onClearAllData: (suspend () -> Unit)? = null
) : StoreRepository {

    override fun getStoresStream(): Flow<List<UiStore>> = trigger.version.map {
        reminderRepository.getAllStores().map { it.toUiStore() }
    }

    override suspend fun registerCurrentLocation(name: String, radiusMeters: Int): UiStore {
        val existing = reminderRepository.getAllStores()
        if (existing.size >= 100) {
            throw IllegalStateException("登録できる店舗数の上限（100件）に達しています")
        }

        val location = currentLocationProvider() ?: throw IllegalStateException("現在地を取得できませんでした")
        val domainStore = DbStore(
            name = name,
            latitude = location.first,
            longitude = location.second,
            radiusMeters = radiusMeters.toFloat(),
            notifyEnabled = true
        )
        val id = reminderRepository.insertStore(domainStore)
        val inserted = domainStore.copy(id = id)
        trigger.notifyChanged()
        return inserted.toUiStore()
    }

    override suspend fun updateStore(store: UiStore) {
        val id = store.id.toLongOrNull() ?: return
        val domainStore = DbStore(
            id = id,
            name = store.name,
            latitude = store.latitude,
            longitude = store.longitude,
            radiusMeters = store.radiusMeters.toFloat(),
            notifyEnabled = store.notifyEnabled,
            lastNotifiedOnEpochDay = store.lastNotifiedOn?.toEpochDay()
        )
        reminderRepository.updateStore(domainStore)
        trigger.notifyChanged()
    }

    override suspend fun deleteStore(storeId: String) {
        val id = storeId.toLongOrNull() ?: return
        reminderRepository.deleteStore(id)
        trigger.notifyChanged()
    }

    override suspend fun clearAllData() {
        reminderRepository.deleteAllStores()
        reminderRepository.deleteAllPurchases()
        reminderRepository.getAllProducts().forEach { reminderRepository.deleteProduct(it.id) }
        reminderRepository.getAllReceipts().forEach { reminderRepository.deleteReceipt(it.id) }
        onClearAllData?.invoke()
        trigger.notifyChanged()
    }

    private fun DbStore.toUiStore(): UiStore {
        return UiStore(
            id = id.toString(),
            name = name,
            latitude = latitude,
            longitude = longitude,
            radiusMeters = radiusMeters.toInt(),
            notifyEnabled = notifyEnabled,
            lastNotifiedOn = lastNotifiedOnEpochDay?.let { LocalDate.ofEpochDay(it) }
        )
    }
}
