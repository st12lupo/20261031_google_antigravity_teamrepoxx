package com.example.receiptreminder.geofence

import domain.model.Store
import geofence.GeofenceSyncGate
import geofence.InMemorySyncStateStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeofenceSyncGateTest {
    private fun store(id: Long, enabled: Boolean = true, lat: Double = 1.0, radius: Float = 200f) =
        Store(id = id, name = "s$id", latitude = lat, longitude = 2.0, radiusMeters = radius, notifyEnabled = enabled)

    private val env = "fine=true,coarse=true,bg=true,boot=3,upd=100"

    @Test
    fun firstTime_syncs() {
        val gate = GeofenceSyncGate(InMemorySyncStateStore())
        assertTrue(gate.shouldSync(GeofenceSyncGate.signatureOf(listOf(store(1)), env)))
    }

    @Test
    fun unchanged_doesNotSyncAgain() {
        val gate = GeofenceSyncGate(InMemorySyncStateStore())
        val sig = GeofenceSyncGate.signatureOf(listOf(store(1), store(2)), env)
        gate.markSynced(sig)
        // 並び順が違っても同じ、名前や最終通知日は無関係
        val again = GeofenceSyncGate.signatureOf(listOf(store(2), store(1).copy(name = "改名")), env)
        assertFalse(gate.shouldSync(again))
    }

    @Test
    fun storeChanges_areDetected() {
        val base = GeofenceSyncGate.signatureOf(listOf(store(1)), env)
        assertNotEquals(base, GeofenceSyncGate.signatureOf(listOf(store(1), store(2)), env))
        assertNotEquals(base, GeofenceSyncGate.signatureOf(listOf(store(1, radius = 300f)), env))
        assertNotEquals(base, GeofenceSyncGate.signatureOf(listOf(store(1, lat = 5.0)), env))
        // 通知オフにした店舗は監視対象から外れる
        assertNotEquals(base, GeofenceSyncGate.signatureOf(listOf(store(1, enabled = false)), env))
        // 通知オフの店舗が増えても監視対象は変わらない
        assertEquals(base, GeofenceSyncGate.signatureOf(listOf(store(1), store(2, enabled = false)), env))
    }

    @Test
    fun permissionAndRebootAndUpdate_areDetected() {
        val stores = listOf(store(1))
        val base = GeofenceSyncGate.signatureOf(stores, env)
        assertNotEquals(base, GeofenceSyncGate.signatureOf(stores, env.replace("bg=true", "bg=false")))
        assertNotEquals(base, GeofenceSyncGate.signatureOf(stores, env.replace("boot=3", "boot=4")))
        assertNotEquals(base, GeofenceSyncGate.signatureOf(stores, env.replace("upd=100", "upd=200")))
    }

    @Test
    fun invalidate_forcesNextSync() {
        val gate = GeofenceSyncGate(InMemorySyncStateStore())
        val sig = GeofenceSyncGate.signatureOf(listOf(store(1)), env)
        gate.markSynced(sig)
        gate.invalidate()
        assertTrue(gate.shouldSync(sig))
    }
}
