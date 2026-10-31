package com.example.receiptreminder.data.bridge

import com.example.receiptreminder.domain.model.NotificationTimeRange
import com.example.receiptreminder.domain.model.Store as UiStore
import data.DefaultReminderRepository
import data.FakeProductDao
import data.FakePurchaseDao
import data.FakeReceiptDao
import data.FakeStoreDao
import domain.geofence.GeofenceManager as DomainGeofenceManager
import domain.model.PurchaseSource
import domain.model.Store as DbStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class RealGeofenceManagerTest {

    private lateinit var productDao: FakeProductDao
    private lateinit var purchaseDao: FakePurchaseDao
    private lateinit var receiptDao: FakeReceiptDao
    private lateinit var storeDao: FakeStoreDao
    private lateinit var reminderRepository: DefaultReminderRepository

    private lateinit var storeRepository: RealStoreRepository
    private lateinit var shoppingListRepository: RealShoppingListRepository
    private lateinit var notificationSettingsRepository: RealNotificationSettingsRepository
    private lateinit var fakeDomainGeofenceManager: TestDomainGeofenceManager

    private val fixedToday = LocalDate.of(2026, 10, 31)
    private var currentTime: LocalTime = LocalTime.of(12, 0)

    @Before
    fun setup() {
        productDao = FakeProductDao()
        purchaseDao = FakePurchaseDao()
        receiptDao = FakeReceiptDao()
        storeDao = FakeStoreDao()
        reminderRepository = DefaultReminderRepository(productDao, purchaseDao, receiptDao, storeDao)

        val trigger = DataUpdateTrigger()
        storeRepository = RealStoreRepository(
            reminderRepository = reminderRepository,
            currentLocationProvider = { 35.6812 to 139.7671 },
            trigger = trigger
        )
        shoppingListRepository = RealShoppingListRepository(
            reminderRepository = reminderRepository,
            todayProvider = { fixedToday },
            trigger = trigger
        )
        notificationSettingsRepository = RealNotificationSettingsRepository(
            initialRange = NotificationTimeRange(8, 0, 21, 0)
        )
        fakeDomainGeofenceManager = TestDomainGeofenceManager()
    }

    private fun createGeofenceManager(): RealGeofenceManager {
        return RealGeofenceManager(
            context = null,
            defaultGeofenceManager = fakeDomainGeofenceManager,
            storeRepository = storeRepository,
            shoppingListRepository = shoppingListRepository,
            notificationSettingsRepository = notificationSettingsRepository,
            currentTimeProvider = { currentTime },
            todayProvider = { fixedToday }
        )
    }

    @Test
    fun registerGeofence_delegatesActiveStores() = runTest {
        val manager = createGeofenceManager()
        val store1 = storeRepository.registerCurrentLocation("スーパーA", 200)

        manager.registerGeofence(store1)

        assertEquals(1, fakeDomainGeofenceManager.registeredStores.size)
        assertEquals("スーパーA", fakeDomainGeofenceManager.registeredStores[0].name)
    }

    @Test
    fun registerGeofence_propagatesSecurityExceptionToCaller() = runTest {
        fakeDomainGeofenceManager.shouldThrowSecurityException = true
        val manager = createGeofenceManager()
        val store1 = storeRepository.registerCurrentLocation("スーパーA", 200)

        // 失敗を握りつぶさず呼び出し元(StoresViewModel)に伝える(GeofenceRegisterFailed の元)
        try {
            manager.registerGeofence(store1)
            org.junit.Assert.fail("SecurityException が伝わるはず")
        } catch (_: SecurityException) {
        }
    }

    @Test
    fun unregisterGeofence_propagatesFailure() = runTest {
        val manager = createGeofenceManager()
        val store1 = storeRepository.registerCurrentLocation("スーパーA", 200)
        fakeDomainGeofenceManager.shouldThrowSecurityException = true

        try {
            manager.unregisterGeofence(store1.id)
            org.junit.Assert.fail("例外が伝わるはず")
        } catch (_: SecurityException) {
        }
    }

    @Test
    fun unregisterGeofence_afterNotifyOff_registersOnlyEnabledStores() = runTest {
        val manager = createGeofenceManager()
        val a = storeRepository.registerCurrentLocation("スーパーA", 200)
        val b = storeRepository.registerCurrentLocation("スーパーB", 200)
        storeRepository.updateStore(a.copy(notifyEnabled = false))

        manager.unregisterGeofence(a.id)

        assertEquals(listOf(b.id.toLong()), fakeDomainGeofenceManager.registeredStores.map { it.id })
    }

    @Test
    fun recheckAllGeofences_afterReboot_registersAgain() = runTest {
        val manager = createGeofenceManager()
        val a = storeRepository.registerCurrentLocation("スーパーA", 200)
        manager.registerGeofence(a)
        // 再起動で端末側の登録が消えた状態
        fakeDomainGeofenceManager.registeredStores.clear()

        manager.recheckAllGeofences()

        assertEquals(listOf(a.id.toLong()), fakeDomainGeofenceManager.registeredStores.map { it.id })
    }

    @Test
    fun recheckAllGeofences_withNoActiveStores_unregistersAll() = runTest {
        val manager = createGeofenceManager()
        val a = storeRepository.registerCurrentLocation("スーパーA", 200)
        storeRepository.updateStore(a.copy(notifyEnabled = false))

        manager.recheckAllGeofences()

        assertTrue(fakeDomainGeofenceManager.unregisterAllCalled)
    }

    @Test
    fun unregisterGeofence_reRegistersRemainingStores() = runTest {
        val manager = createGeofenceManager()
        val store1 = storeRepository.registerCurrentLocation("スーパーA", 200)
        val store2 = storeRepository.registerCurrentLocation("スーパーB", 300)

        manager.unregisterGeofence(store1.id)

        assertEquals(1, fakeDomainGeofenceManager.registeredStores.size)
        assertEquals(store2.id.toLong(), fakeDomainGeofenceManager.registeredStores[0].id)
    }

    @Test
    fun unregisterGeofence_callsUnregisterAllWhenNoStoresRemain() = runTest {
        val manager = createGeofenceManager()
        val store1 = storeRepository.registerCurrentLocation("スーパーA", 200)

        manager.unregisterGeofence(store1.id)

        assertTrue(fakeDomainGeofenceManager.unregisterAllCalled)
    }

    @Test
    fun recheckAllGeofences_registersAllActiveStores() = runTest {
        val manager = createGeofenceManager()
        storeRepository.registerCurrentLocation("スーパーA", 200)
        val store2 = storeRepository.registerCurrentLocation("スーパーB", 300)
        storeRepository.updateStore(store2.copy(notifyEnabled = false))

        manager.recheckAllGeofences()

        // notifyEnabled == true の店舗のみが登録される
        assertEquals(1, fakeDomainGeofenceManager.registeredStores.size)
        assertEquals("スーパーA", fakeDomainGeofenceManager.registeredStores[0].name)
    }

    private var environment = "fine=true,bg=true,boot=1,upd=1"

    private fun createGatedManager(gate: geofence.GeofenceSyncGate) = RealGeofenceManager(
        context = null,
        defaultGeofenceManager = fakeDomainGeofenceManager,
        storeRepository = storeRepository,
        shoppingListRepository = shoppingListRepository,
        notificationSettingsRepository = notificationSettingsRepository,
        currentTimeProvider = { currentTime },
        todayProvider = { fixedToday },
        syncGate = gate,
        environmentProvider = { environment }
    )

    @Test
    fun syncIfChanged_doesNotReRegisterWhenNothingChanged() = runTest {
        val manager = createGatedManager(geofence.GeofenceSyncGate(geofence.InMemorySyncStateStore()))
        storeRepository.registerCurrentLocation("スーパーA", 200)

        manager.syncIfChanged()
        assertEquals(1, fakeDomainGeofenceManager.registerCalls)
        // アプリを開き直しても、店に入った直後の ENTER を失わないよう登録し直さない
        manager.syncIfChanged()
        manager.syncIfChanged()
        assertEquals(1, fakeDomainGeofenceManager.registerCalls)
    }

    @Test
    fun syncIfChanged_reRegistersWhenStoresOrPermissionChange() = runTest {
        val manager = createGatedManager(geofence.GeofenceSyncGate(geofence.InMemorySyncStateStore()))
        val a = storeRepository.registerCurrentLocation("スーパーA", 200)
        manager.syncIfChanged()

        environment = "fine=true,bg=false,boot=1,upd=1"
        manager.syncIfChanged()
        assertEquals(2, fakeDomainGeofenceManager.registerCalls)

        storeRepository.updateStore(a.copy(radiusMeters = 400))
        manager.syncIfChanged()
        assertEquals(3, fakeDomainGeofenceManager.registerCalls)

        environment = "fine=true,bg=false,boot=2,upd=1" // 端末の再起動
        manager.syncIfChanged()
        assertEquals(4, fakeDomainGeofenceManager.registerCalls)
    }

    @Test
    fun recheckAllGeofences_alwaysReRegisters_evenWhenUnchanged() = runTest {
        val manager = createGatedManager(geofence.GeofenceSyncGate(geofence.InMemorySyncStateStore()))
        storeRepository.registerCurrentLocation("スーパーA", 200)
        manager.syncIfChanged()
        manager.recheckAllGeofences() // 再起動・更新
        assertEquals(2, fakeDomainGeofenceManager.registerCalls)
    }

    @Test
    fun failedSync_isRetriedOnNextStart() = runTest {
        val manager = createGatedManager(geofence.GeofenceSyncGate(geofence.InMemorySyncStateStore()))
        storeRepository.registerCurrentLocation("スーパーA", 200)
        manager.syncIfChanged()

        fakeDomainGeofenceManager.shouldThrowSecurityException = true
        try {
            manager.recheckAllGeofences()
            org.junit.Assert.fail("例外が伝わるはず")
        } catch (_: SecurityException) {
        }
        fakeDomainGeofenceManager.shouldThrowSecurityException = false
        val before = fakeDomainGeofenceManager.registerCalls
        manager.syncIfChanged()
        assertEquals(before + 1, fakeDomainGeofenceManager.registerCalls)
    }

    @Test
    fun simulateStoreVisit_storeNotFound_returnsFalse() = runTest {
        val manager = createGeofenceManager()
        val result = manager.simulateStoreVisit("non-existent-id")
        assertFalse(result)
    }

    @Test
    fun simulateStoreVisit_notifyDisabled_returnsFalse() = runTest {
        val manager = createGeofenceManager()
        val store = storeRepository.registerCurrentLocation("薬局", 200)
        storeRepository.updateStore(store.copy(notifyEnabled = false))

        val result = manager.simulateStoreVisit(store.id)
        assertFalse(result)
    }

    @Test
    fun simulateStoreVisit_outsideNotificationHours_returnsFalse() = runTest {
        currentTime = LocalTime.of(7, 30) // 8:00〜21:00 の範囲外
        val manager = createGeofenceManager()
        val store = storeRepository.registerCurrentLocation("薬局", 200)

        val result = manager.simulateStoreVisit(store.id)
        assertFalse(result)
    }

    @Test
    fun simulateStoreVisit_alreadyNotifiedToday_returnsFalse() = runTest {
        val manager = createGeofenceManager()
        val store = storeRepository.registerCurrentLocation("薬局", 200)
        storeRepository.updateStore(store.copy(lastNotifiedOn = fixedToday))

        val result = manager.simulateStoreVisit(store.id)
        assertFalse(result)
    }

    @Test
    fun simulateStoreVisit_noRunningOutItems_returnsFalse() = runTest {
        val manager = createGeofenceManager()
        val store = storeRepository.registerCurrentLocation("薬局", 200)

        // 商品が存在しない場合、通知しない
        val result = manager.simulateStoreVisit(store.id)
        assertFalse(result)
    }

    @Test
    fun simulateStoreVisit_hasRunningOutItems_returnsTrueAndUpdatesLastNotified() = runTest {
        val manager = createGeofenceManager()
        val store = storeRepository.registerCurrentLocation("薬局", 200)

        // なくなりそうな商品（30日前購入、既定周期30日 → 残り0日でRUNNING_OUT）を追加
        val product = reminderRepository.getOrCreateProduct("シャンプー", "日用品")
        reminderRepository.recordManualPurchase(
            productId = product.id,
            purchasedOnEpochDay = fixedToday.minusDays(30).toEpochDay(),
            quantity = 1.0,
            price = 500,
            source = PurchaseSource.MANUAL
        )

        val result = manager.simulateStoreVisit(store.id)
        assertTrue(result)

        // 最終通知日が今日に更新されたことを確認
        val updatedStore = reminderRepository.getAllStores().first { it.id == store.id.toLong() }
        assertEquals(fixedToday.toEpochDay(), updatedStore.lastNotifiedOnEpochDay)
    }

    private class TestDomainGeofenceManager : DomainGeofenceManager {
        val registeredStores = mutableListOf<DbStore>()
        var unregisterAllCalled = false
        var shouldThrowSecurityException = false
        var registerCalls = 0

        override suspend fun registerGeofences(stores: List<DbStore>) {
            registerCalls++
            if (shouldThrowSecurityException) {
                throw SecurityException("Permission denied")
            }
            registeredStores.clear()
            registeredStores.addAll(stores)
        }

        override suspend fun unregisterAllGeofences() {
            if (shouldThrowSecurityException) {
                throw SecurityException("Permission denied")
            }
            unregisterAllCalled = true
            registeredStores.clear()
        }
    }
}
