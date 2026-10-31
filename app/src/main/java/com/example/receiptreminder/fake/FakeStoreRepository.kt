package com.example.receiptreminder.fake

import com.example.receiptreminder.domain.model.Store
import com.example.receiptreminder.domain.repository.StoreRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeStoreRepository(
    initialStores: List<Store> = SampleData.initialStores(),
    private val onClearAllData: (() -> Unit)? = null
) : StoreRepository {

    private val _storesFlow = MutableStateFlow(initialStores)

    override fun getStoresStream(): Flow<List<Store>> = _storesFlow.asStateFlow()

    override suspend fun registerCurrentLocation(name: String, radiusMeters: Int): Store {
        // 仮の現在地座標（都内想定）
        val newStore = Store(
            id = "store-${System.currentTimeMillis()}",
            name = name,
            latitude = 35.6812,
            longitude = 139.7671,
            radiusMeters = radiusMeters,
            notifyEnabled = true
        )
        val current = _storesFlow.value.toMutableList()
        current.add(0, newStore)
        _storesFlow.value = current
        return newStore
    }

    override suspend fun updateStore(store: Store) {
        val current = _storesFlow.value.toMutableList()
        val index = current.indexOfFirst { it.id == store.id }
        if (index != -1) {
            current[index] = store
            _storesFlow.value = current
        }
    }

    override suspend fun deleteStore(storeId: String) {
        val current = _storesFlow.value.toMutableList()
        current.removeAll { it.id == storeId }
        _storesFlow.value = current
    }

    override suspend fun clearAllData() {
        _storesFlow.value = emptyList()
        onClearAllData?.invoke()
    }

    fun resetStores(stores: List<Store> = emptyList()) {
        _storesFlow.value = stores
    }
}
