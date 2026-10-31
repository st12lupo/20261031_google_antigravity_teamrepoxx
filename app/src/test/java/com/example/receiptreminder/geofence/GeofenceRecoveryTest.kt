package com.example.receiptreminder.geofence

import com.google.android.gms.location.GeofenceStatusCodes
import geofence.GeofenceRecovery
import geofence.GeofenceResyncWorker
import geofence.GeofenceSyncGate
import geofence.InMemorySyncStateStore
import geofence.buildGeofenceEnvironment
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeofenceRecoveryTest {
    private fun env(location: Boolean) = buildGeofenceEnvironment(
        fine = true, coarse = true, background = true,
        locationEnabled = location, boot = 3, updated = 100L
    )

    @Test
    fun geofenceNotAvailable_invalidatesStateAndSchedulesResync() {
        val store = InMemorySyncStateStore()
        val gate = GeofenceSyncGate(store)
        gate.markSynced("sig")
        var scheduled = 0
        val recovery = GeofenceRecovery(invalidateSyncState = { gate.invalidate() }, scheduleResync = { scheduled++ })

        assertTrue(recovery.onError(GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE))

        assertNull(store.get())
        assertTrue(gate.shouldSync("sig"))
        assertEquals(1, scheduled)
    }

    @Test
    fun otherErrors_doNothing() {
        var invalidated = 0
        var scheduled = 0
        val recovery = GeofenceRecovery({ invalidated++ }, { scheduled++ })
        for (code in listOf(
            GeofenceStatusCodes.GEOFENCE_TOO_MANY_GEOFENCES,
            GeofenceStatusCodes.GEOFENCE_TOO_MANY_PENDING_INTENTS,
            GeofenceStatusCodes.GEOFENCE_INSUFFICIENT_LOCATION_PERMISSION,
            GeofenceStatusCodes.ERROR
        )) {
            assertFalse(recovery.onError(code))
        }
        assertEquals(0, invalidated)
        assertEquals(0, scheduled)
    }

    @Test
    fun locationToggle_changesSignature_soNextStartResyncs() {
        val gate = GeofenceSyncGate(InMemorySyncStateStore())
        gate.markSynced(GeofenceSyncGate.signatureOf(emptyList(), env(true)))
        // オフにした状態では印が違う(再登録を試みる)
        val off = GeofenceSyncGate.signatureOf(emptyList(), env(false))
        assertTrue(gate.shouldSync(off))
        gate.markSynced(off)
        // オンに戻したときも印が違うので、onStart で再登録される
        assertTrue(gate.shouldSync(GeofenceSyncGate.signatureOf(emptyList(), env(true))))
        assertNotEquals(env(true), env(false))
    }

    @Test
    fun workerWaitsWhileLocationIsOff_withoutGivingUp() = runBlocking {
        var called = false
        val block: suspend () -> Unit = { called = true }
        // 位置情報オフ: 試さず、試行回数が上限を超えていても諦めない
        assertEquals(
            GeofenceResyncWorker.Companion.Outcome.Wait,
            GeofenceResyncWorker.outcomeOf(GeofenceResyncWorker.MAX_ATTEMPTS + 10, false, block)
        )
        assertFalse(called)
        // 復帰したら登録する
        assertEquals(
            GeofenceResyncWorker.Companion.Outcome.Success,
            GeofenceResyncWorker.outcomeOf(GeofenceResyncWorker.MAX_ATTEMPTS + 10, true, block)
        )
        assertTrue(called)
    }

    @Test
    fun workerWaitsAtFixedIntervalWhileLocationIsOff_andBoundsTheWaiting() = runBlocking {
        val block: suspend () -> Unit = { error("must not be called while location is off") }
        val wait = GeofenceResyncWorker.Companion.Outcome.Wait
        assertEquals(wait, GeofenceResyncWorker.outcomeOf(0, false, 0, block))
        assertEquals(wait, GeofenceResyncWorker.outcomeOf(0, false, GeofenceResyncWorker.MAX_WAITS - 2, block))
        // 上限に達したら諦める(アプリを開くと onStart で再登録される)
        assertEquals(
            GeofenceResyncWorker.Companion.Outcome.GiveUp,
            GeofenceResyncWorker.outcomeOf(0, false, GeofenceResyncWorker.MAX_WAITS - 1, block)
        )
        // 最悪の見逃しは固定間隔 1 回分(WorkManager の backoff に比べ短い)
        assertTrue(GeofenceResyncWorker.WAIT_INTERVAL_MINUTES <= 5)
        assertTrue(GeofenceResyncWorker.WAIT_INTERVAL_MINUTES * GeofenceResyncWorker.MAX_WAITS >= 24 * 60)
    }

    @Test
    fun oneFailureRightAfterLocationReturns_doesNotGiveUp() = runBlocking {
        // 待ちは新しい作業として予約し直すので、復帰後の失敗回数は 0 から数える
        val failing: suspend () -> Unit = { throw IllegalStateException("not ready") }
        assertEquals(
            GeofenceResyncWorker.Companion.Outcome.Retry,
            GeofenceResyncWorker.outcomeOf(0, true, 200, failing)
        )
        assertEquals(
            GeofenceResyncWorker.Companion.Outcome.GiveUp,
            GeofenceResyncWorker.outcomeOf(GeofenceResyncWorker.MAX_ATTEMPTS - 1, true, 200, failing)
        )
    }
}
