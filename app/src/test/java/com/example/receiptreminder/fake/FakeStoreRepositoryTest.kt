package com.example.receiptreminder.fake

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class FakeStoreRepositoryTest {

    private lateinit var storeRepository: FakeStoreRepository
    private var clearAllCalled = false

    @Before
    fun setUp() {
        clearAllCalled = false
        storeRepository = FakeStoreRepository(
            onClearAllData = { clearAllCalled = true }
        )
    }

    @Test
    fun getStoresStream_returnsInitialStoresMatchingMockup() = runTest {
        val stores = storeRepository.getStoresStream().first()

        assertEquals(3, stores.size)
        assertEquals("みどりマート 中央店", stores[0].name)
        assertEquals(200, stores[0].radiusMeters)
        assertTrue(stores[0].notifyEnabled)

        assertEquals("ドラッグひかり 駅前店", stores[1].name)
        assertEquals(300, stores[1].radiusMeters)
        assertTrue(stores[1].notifyEnabled)

        assertEquals("まるやまスーパー", stores[2].name)
        assertEquals(200, stores[2].radiusMeters)
        assertFalse(stores[2].notifyEnabled) // 通知オフ
    }

    @Test
    fun registerCurrentLocation_addsNewStore() = runTest {
        val newStore = storeRepository.registerCurrentLocation("架空ストア 桜町店", radiusMeters = 250)
        val stores = storeRepository.getStoresStream().first()

        assertEquals("架空ストア 桜町店", newStore.name)
        assertEquals(4, stores.size)
        assertEquals(newStore.id, stores[0].id)
    }

    @Test
    fun updateStore_updatesStoreSettings() = runTest {
        val stores = storeRepository.getStoresStream().first()
        val target = stores[0]
        val updated = target.copy(radiusMeters = 500, notifyEnabled = false)

        storeRepository.updateStore(updated)

        val updatedStores = storeRepository.getStoresStream().first()
        val result = updatedStores.first { it.id == target.id }
        assertEquals(500, result.radiusMeters)
        assertFalse(result.notifyEnabled)
    }

    @Test
    fun deleteStore_removesStore() = runTest {
        val stores = storeRepository.getStoresStream().first()
        val target = stores[0]

        storeRepository.deleteStore(target.id)

        val updatedStores = storeRepository.getStoresStream().first()
        assertEquals(2, updatedStores.size)
        assertNull(updatedStores.firstOrNull { it.id == target.id })
    }

    @Test
    fun clearAllData_clearsStoresAndTriggersCallback() = runTest {
        storeRepository.clearAllData()

        val stores = storeRepository.getStoresStream().first()
        assertTrue(stores.isEmpty())
        assertTrue(clearAllCalled)
    }
}
