package com.example.receiptreminder.fake

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class FakeGeofenceManagerTest {

    private lateinit var storeRepository: FakeStoreRepository
    private lateinit var shoppingListRepository: FakeShoppingListRepository
    private lateinit var notificationSettingsRepository: FakeNotificationSettingsRepository
    private lateinit var geofenceManager: FakeGeofenceManager

    @Before
    fun setUp() {
        storeRepository = FakeStoreRepository()
        shoppingListRepository = FakeShoppingListRepository()
        notificationSettingsRepository = FakeNotificationSettingsRepository()
        geofenceManager = FakeGeofenceManager(
            storeRepository = storeRepository,
            shoppingListRepository = shoppingListRepository,
            notificationSettingsRepository = notificationSettingsRepository,
            // 実時刻に依存しないよう、通知時間帯内(8〜21 時)の時刻に固定する
            currentTimeProvider = { java.time.LocalTime.of(12, 0) }
        )
    }

    @Test
    fun simulateStoreVisit_doesNotTriggerOutsideTimeRange() = runTest {
        val manager = FakeGeofenceManager(
            storeRepository = storeRepository,
            shoppingListRepository = shoppingListRepository,
            notificationSettingsRepository = notificationSettingsRepository,
            currentTimeProvider = { java.time.LocalTime.of(23, 0) }
        )
        val store = storeRepository.getStoresStream().first().first { it.notifyEnabled }

        assertFalse(manager.simulateStoreVisit(store.id))
    }

    @Test
    fun simulateStoreVisit_triggersNotificationWhenConditionsMet() = runTest {
        val stores = storeRepository.getStoresStream().first()
        val enabledStore = stores.first { it.notifyEnabled }

        val triggered = geofenceManager.simulateStoreVisit(enabledStore.id)

        assertTrue(triggered)
        assertNotNull(geofenceManager.lastSimulatedNotificationMessage)
        assertTrue(geofenceManager.lastSimulatedNotificationMessage!!.contains("なくなりそうです"))
    }

    @Test
    fun simulateStoreVisit_doesNotTriggerWhenStoreNotificationDisabled() = runTest {
        val stores = storeRepository.getStoresStream().first()
        val disabledStore = stores.first { !it.notifyEnabled } // まるやまスーパー

        val triggered = geofenceManager.simulateStoreVisit(disabledStore.id)

        assertFalse(triggered)
    }

    @Test
    fun simulateStoreVisit_doesNotTriggerTwiceOnSameDay() = runTest {
        val stores = storeRepository.getStoresStream().first()
        val enabledStore = stores.first { it.notifyEnabled }

        // 1回目：通知される
        val firstTrigger = geofenceManager.simulateStoreVisit(enabledStore.id)
        assertTrue(firstTrigger)

        // 2回目（同日）：通知されない（S-6）
        val secondTrigger = geofenceManager.simulateStoreVisit(enabledStore.id)
        assertFalse(secondTrigger)
    }
}
