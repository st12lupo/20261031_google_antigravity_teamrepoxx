package com.example.receiptreminder.ui.store

import com.example.receiptreminder.domain.model.NotificationTimeRange
import com.example.receiptreminder.domain.model.Store
import com.example.receiptreminder.domain.repository.GeofenceManager
import com.example.receiptreminder.domain.repository.StoreRepository
import com.example.receiptreminder.fake.FakeNotificationSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** 失敗を起こせるテスト用のリポジトリ(架空の店舗だけを使う) */
private class TestStoreRepository(initial: List<Store> = emptyList()) : StoreRepository {
    private val flow = MutableStateFlow(initial)
    private var counter = 0
    var failRegister = false
    var failUpdate = false
    var failDelete = false
    var failClear = false
    var clearCalls = 0

    override fun getStoresStream(): Flow<List<Store>> = flow.asStateFlow()

    override suspend fun registerCurrentLocation(name: String, radiusMeters: Int): Store {
        if (failRegister) throw IllegalStateException("location")
        val store = Store("new-${counter++}", name, 0.0, 0.0, radiusMeters)
        flow.value = listOf(store) + flow.value
        return store
    }

    override suspend fun updateStore(store: Store) {
        if (failUpdate) throw IllegalStateException("update")
        flow.value = flow.value.map { if (it.id == store.id) store else it }
    }

    override suspend fun deleteStore(storeId: String) {
        if (failDelete) throw IllegalStateException("delete")
        flow.value = flow.value.filterNot { it.id == storeId }
    }

    override suspend fun clearAllData() {
        if (failClear) throw IllegalStateException("clear")
        clearCalls++
        flow.value = emptyList()
    }
}

private class RecordingGeofenceManager : GeofenceManager {
    val registered = mutableListOf<Store>()
    val unregistered = mutableListOf<String>()
    var failRegister = false
    var failUnregister = false

    override suspend fun registerGeofence(store: Store) {
        if (failRegister) throw SecurityException("permission")
        registered += store
    }

    override suspend fun unregisterGeofence(storeId: String) {
        if (failUnregister) throw IllegalStateException("unregister")
        unregistered += storeId
    }

    override suspend fun recheckAllGeofences() = Unit
    override suspend fun simulateStoreVisit(storeId: String) = false
}

@OptIn(ExperimentalCoroutinesApi::class)
class StoresViewModelTest {

    private lateinit var stores: TestStoreRepository
    private lateinit var settings: FakeNotificationSettingsRepository
    private lateinit var geofence: RecordingGeofenceManager

    private val storeA = Store("a", "テスト店A", 0.0, 0.0, 200, true)
    private val storeB = Store("b", "テスト店B", 0.0, 0.0, 300, false)

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        stores = TestStoreRepository(listOf(storeA, storeB))
        settings = FakeNotificationSettingsRepository()
        geofence = RecordingGeofenceManager()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = StoresViewModel(stores, settings, geofence)

    @Test
    fun initialState_isNotLoadedThenLoadedWithStores() = runTest {
        val vm = viewModel()
        assertFalse(vm.uiState.value.isLoaded)
        advanceUntilIdle()
        val state = vm.uiState.value
        assertTrue(state.isLoaded)
        assertEquals(2, state.stores.size)
        assertEquals(NotificationTimeRange(8, 0, 21, 0), state.timeRange)
        assertFalse(state.isEmpty)
    }

    @Test
    fun emptyStores_showsEmptyState() = runTest {
        stores = TestStoreRepository()
        val vm = viewModel()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isEmpty)
    }

    @Test
    fun registerStore_savesWithDefaultRadiusAndRegistersGeofence() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.registerStore("  テスト店C ")
        advanceUntilIdle()

        val added = vm.uiState.value.stores.first()
        assertEquals("テスト店C", added.name)
        assertEquals(200, added.radiusMeters)
        assertEquals(listOf(added), geofence.registered)
        assertNull(vm.uiState.value.error)
        assertFalse(vm.uiState.value.isBusy)
    }

    @Test
    fun registerStore_blankNameDoesNothing() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.registerStore("   ")
        advanceUntilIdle()
        assertEquals(2, vm.uiState.value.stores.size)
        assertTrue(geofence.registered.isEmpty())
    }

    @Test
    fun registerStore_atLimit_showsStoreLimitError() = runTest {
        stores = TestStoreRepository(
            List(StoreLimits.MAX_STORES) { Store("s$it", "テスト店$it", 0.0, 0.0) }
        )
        val vm = viewModel()
        advanceUntilIdle()

        assertFalse(vm.canStartRegistration())
        advanceUntilIdle()
        assertEquals(StoresError.StoreLimit, vm.uiState.value.error)

        vm.dismissError()
        advanceUntilIdle()
        vm.registerStore("あふれる店")
        advanceUntilIdle()
        assertEquals(StoreLimits.MAX_STORES, vm.uiState.value.stores.size)
        assertEquals(StoresError.StoreLimit, vm.uiState.value.error)
        assertTrue(geofence.registered.isEmpty())
    }

    @Test
    fun registerStore_belowLimit_canStart() = runTest {
        stores = TestStoreRepository(
            List(StoreLimits.MAX_STORES - 1) { Store("s$it", "テスト店$it", 0.0, 0.0) }
        )
        val vm = viewModel()
        advanceUntilIdle()
        assertTrue(vm.canStartRegistration())
    }

    @Test
    fun registerStore_locationFailure_showsError() = runTest {
        stores.failRegister = true
        val vm = viewModel()
        advanceUntilIdle()
        vm.registerStore("テスト店C")
        advanceUntilIdle()
        assertEquals(StoresError.LocationUnavailable, vm.uiState.value.error)
        assertEquals(2, vm.uiState.value.stores.size)
        assertTrue(geofence.registered.isEmpty())
    }

    @Test
    fun registerStore_geofenceFailure_showsErrorAndKeepsStore() = runTest {
        geofence.failRegister = true
        val vm = viewModel()
        advanceUntilIdle()
        vm.registerStore("テスト店C")
        advanceUntilIdle()
        assertEquals(StoresError.GeofenceRegisterFailed, vm.uiState.value.error)
        assertEquals(3, vm.uiState.value.stores.size)
        assertFalse(vm.uiState.value.isBusy)
    }

    @Test
    fun dismissError_clearsError() = runTest {
        stores.failRegister = true
        val vm = viewModel()
        advanceUntilIdle()
        vm.registerStore("テスト店C")
        advanceUntilIdle()
        vm.dismissError()
        advanceUntilIdle()
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun updateStore_changesRadiusAndReRegistersGeofence() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.updateStore("a", "テスト店A改", " 350 ")
        advanceUntilIdle()

        val updated = vm.uiState.value.stores.first { it.id == "a" }
        assertEquals("テスト店A改", updated.name)
        assertEquals(350, updated.radiusMeters)
        assertEquals(listOf(updated), geofence.registered)
    }

    @Test
    fun updateStore_invalidRadius_doesNothing() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        for (bad in listOf("abc", "", "99", "1001", "-5", "20.5")) {
            vm.updateStore("a", "テスト店A", bad)
            advanceUntilIdle()
        }
        assertEquals(200, vm.uiState.value.stores.first { it.id == "a" }.radiusMeters)
        assertTrue(geofence.registered.isEmpty())
    }

    @Test
    fun updateStore_boundaryRadiusAccepted() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.updateStore("a", "テスト店A", "100")
        advanceUntilIdle()
        assertEquals(100, vm.uiState.value.stores.first { it.id == "a" }.radiusMeters)
        vm.updateStore("a", "テスト店A", "1000")
        advanceUntilIdle()
        assertEquals(1000, vm.uiState.value.stores.first { it.id == "a" }.radiusMeters)
    }

    @Test
    fun updateStore_saveFailure_showsErrorAndSkipsGeofence() = runTest {
        stores.failUpdate = true
        val vm = viewModel()
        advanceUntilIdle()
        vm.updateStore("a", "テスト店A", "300")
        advanceUntilIdle()
        assertEquals(StoresError.SaveFailed, vm.uiState.value.error)
        assertTrue(geofence.registered.isEmpty())
    }

    @Test
    fun setNotifyEnabled_off_unregistersGeofence() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.setNotifyEnabled("a", false)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.stores.first { it.id == "a" }.notifyEnabled)
        assertEquals(listOf("a"), geofence.unregistered)
    }

    @Test
    fun setNotifyEnabled_on_registersGeofence() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.setNotifyEnabled("b", true)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.stores.first { it.id == "b" }.notifyEnabled)
        assertEquals(listOf("b"), geofence.registered.map { it.id })
    }

    @Test
    fun setNotifyEnabled_geofenceFailure_showsError() = runTest {
        geofence.failRegister = true
        val vm = viewModel()
        advanceUntilIdle()
        vm.setNotifyEnabled("b", true)
        advanceUntilIdle()
        assertEquals(StoresError.GeofenceRegisterFailed, vm.uiState.value.error)
    }

    @Test
    fun deleteStore_removesStoreAndUnregistersGeofence() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.deleteStore("a")
        advanceUntilIdle()
        assertEquals(listOf("b"), vm.uiState.value.stores.map { it.id })
        assertEquals(listOf("a"), geofence.unregistered)
    }

    @Test
    fun deleteStore_unregisterFailure_showsError() = runTest {
        geofence.failUnregister = true
        val vm = viewModel()
        advanceUntilIdle()
        vm.deleteStore("a")
        advanceUntilIdle()
        assertEquals(StoresError.GeofenceUnregisterFailed, vm.uiState.value.error)
        assertEquals(listOf("b"), vm.uiState.value.stores.map { it.id })
    }

    @Test
    fun deleteStore_deleteFailure_showsErrorAndKeepsGeofence() = runTest {
        stores.failDelete = true
        val vm = viewModel()
        advanceUntilIdle()
        vm.deleteStore("a")
        advanceUntilIdle()
        assertEquals(StoresError.DeleteFailed, vm.uiState.value.error)
        assertTrue(geofence.unregistered.isEmpty())
    }

    @Test
    fun setTimeRange_validRange_isSaved() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.setTimeRange("9:30", "20:15")
        advanceUntilIdle()
        assertEquals(NotificationTimeRange(9, 30, 20, 15), vm.uiState.value.timeRange)
    }

    @Test
    fun setTimeRange_invalidRange_isNotSaved() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        for ((s, e) in listOf("21:00" to "8:00", "8:00" to "8:00", "x" to "9:00", "8:00" to "24:00", "8:60" to "9:00")) {
            vm.setTimeRange(s, e)
            advanceUntilIdle()
        }
        assertEquals(NotificationTimeRange(8, 0, 21, 0), vm.uiState.value.timeRange)
    }

    @Test
    fun clearAllData_clearsAndUnregistersEveryGeofence() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.clearAllData()
        advanceUntilIdle()
        assertEquals(1, stores.clearCalls)
        assertTrue(vm.uiState.value.isEmpty)
        assertEquals(setOf("a", "b"), geofence.unregistered.toSet())
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun clearAllData_failure_keepsGeofencesAndShowsError() = runTest {
        stores.failClear = true
        val vm = viewModel()
        advanceUntilIdle()
        vm.clearAllData()
        advanceUntilIdle()
        assertEquals(StoresError.DeleteFailed, vm.uiState.value.error)
        assertTrue(geofence.unregistered.isEmpty())
        assertEquals(2, vm.uiState.value.stores.size)
    }

    @Test
    fun clearAllData_unregisterFailure_showsError() = runTest {
        geofence.failUnregister = true
        val vm = viewModel()
        advanceUntilIdle()
        vm.clearAllData()
        advanceUntilIdle()
        assertEquals(StoresError.GeofenceUnregisterFailed, vm.uiState.value.error)
        assertTrue(vm.uiState.value.isEmpty)
    }
}
