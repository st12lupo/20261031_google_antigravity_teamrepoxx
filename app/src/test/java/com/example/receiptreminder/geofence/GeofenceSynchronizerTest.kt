package com.example.receiptreminder.geofence

import domain.model.Store
import geofence.BootReceiver
import geofence.GeofenceBackend
import geofence.GeofenceSynchronizer
import geofence.InMemoryRegisteredIdStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class GeofenceSynchronizerTest {
    /** Play Services と同じ「add は同じ ID を上書きするだけ」を再現する */
    private class FakeBackend : GeofenceBackend {
        val registered = linkedMapOf<Long, Store>()
        var removeAllCount = 0
        var failOnAdd = false
        var failOnRemoveIds = false
        val calls = mutableListOf<String>()
        override suspend fun removeAll() {
            calls += "removeAll"
            removeAllCount++
            registered.clear()
        }

        override suspend fun removeIds(ids: List<String>) {
            calls += "removeIds"
            if (failOnRemoveIds) throw IllegalStateException("remove failed")
            ids.forEach { registered.remove(it.toLong()) }
        }

        override suspend fun add(stores: List<Store>) {
            calls += "add"
            if (failOnAdd) throw SecurityException("denied")
            stores.forEach { registered[it.id] = it }
        }
    }

    private fun store(id: Long, enabled: Boolean = true) =
        Store(id = id, name = "s$id", latitude = 0.0, longitude = 0.0, notifyEnabled = enabled)

    @Test
    fun staleStoreIsRemovedWhenOthersRemain() = runTest {
        val backend = FakeBackend()
        val sync = GeofenceSynchronizer(backend)
        sync.sync(listOf(store(1), store(2)))
        // 店舗 1 を削除した
        sync.sync(listOf(store(2)))
        assertEquals(listOf(2L), backend.registered.keys.toList())
        // 店舗 1 を通知オフにした
        sync.sync(listOf(store(1, enabled = false), store(2)))
        assertEquals(listOf(2L), backend.registered.keys.toList())
    }

    @Test
    fun reRegistersAfterRebootClear() = runTest {
        val backend = FakeBackend()
        val sync = GeofenceSynchronizer(backend)
        sync.sync(listOf(store(1)))
        backend.registered.clear() // 再起動で消えた
        sync.sync(listOf(store(1)))
        assertTrue(backend.registered.containsKey(1L))
    }

    @Test
    fun noActiveStores_registersNothing() = runTest {
        val backend = FakeBackend()
        GeofenceSynchronizer(backend).sync(listOf(store(1, enabled = false)))
        assertTrue(backend.registered.isEmpty())
        assertEquals(1, backend.removeAllCount)
    }

    @Test
    fun addsBeforeRemovingStaleIds_andNeverRemovesAllWhileStoresRemain() = runTest {
        val backend = FakeBackend()
        val sync = GeofenceSynchronizer(backend)
        sync.sync(listOf(store(1), store(2)))
        backend.calls.clear()
        sync.sync(listOf(store(2), store(3)))
        assertEquals(listOf("add", "removeIds"), backend.calls)
        assertEquals(setOf(2L, 3L), backend.registered.keys)
    }

    @Test
    fun failureOnAdd_keepsExistingRegistrations() = runTest {
        val backend = FakeBackend()
        val sync = GeofenceSynchronizer(backend)
        sync.sync(listOf(store(1), store(2)))
        backend.failOnAdd = true
        try {
            sync.sync(listOf(store(2), store(3)))
            fail("例外が伝わるはず")
        } catch (_: SecurityException) {
        }
        // 全解除されていない: 既存の店舗の監視は続く
        assertEquals(setOf(1L, 2L), backend.registered.keys)
        assertEquals(0, backend.removeAllCount)
    }

    @Test
    fun cancellationBetweenAddAndRemove_keepsMonitoring_andRetriesStaleRemoval() = runTest {
        val backend = FakeBackend()
        val ids = InMemoryRegisteredIdStore()
        val sync = GeofenceSynchronizer(backend, ids)
        sync.sync(listOf(store(1), store(2)))
        backend.failOnRemoveIds = true
        try {
            sync.sync(listOf(store(2)))
            fail("例外が伝わるはず")
        } catch (_: IllegalStateException) {
        }
        // 解除に失敗しても監視は止まらず、古い ID は覚えたまま
        assertTrue(backend.registered.keys.containsAll(listOf(2L)))
        assertEquals(setOf("1", "2"), ids.get())
        backend.failOnRemoveIds = false
        sync.sync(listOf(store(2)))
        assertEquals(setOf(2L), backend.registered.keys)
        assertEquals(setOf("2"), ids.get())
    }

    @Test
    fun withoutRecord_onlyAdds_doesNotRemoveAll() = runTest {
        val backend = FakeBackend()
        backend.registered[9L] = store(9)
        GeofenceSynchronizer(backend).sync(listOf(store(1)))
        assertEquals(listOf("add"), backend.calls)
        assertEquals(0, backend.removeAllCount)
    }

    @Test
    fun allStoresDisabled_removesAllAndForgetsIds() = runTest {
        val backend = FakeBackend()
        val ids = InMemoryRegisteredIdStore()
        val sync = GeofenceSynchronizer(backend, ids)
        sync.sync(listOf(store(1)))
        sync.sync(listOf(store(1, enabled = false)))
        assertTrue(backend.registered.isEmpty())
        assertEquals(emptySet<String>(), ids.get())
    }

    @Test
    fun failureIsPropagated() = runTest {
        val backend = FakeBackend().apply { failOnAdd = true }
        try {
            GeofenceSynchronizer(backend).sync(listOf(store(1)))
            fail("例外が伝わるはず")
        } catch (_: SecurityException) {
        }
    }

    @Test
    fun bootReceiverActions() {
        assertTrue(BootReceiver.shouldRestore("android.intent.action.BOOT_COMPLETED"))
        assertTrue(BootReceiver.shouldRestore("android.intent.action.MY_PACKAGE_REPLACED"))
        assertFalse(BootReceiver.shouldRestore("android.intent.action.USER_PRESENT"))
        assertFalse(BootReceiver.shouldRestore(null))
    }
}
