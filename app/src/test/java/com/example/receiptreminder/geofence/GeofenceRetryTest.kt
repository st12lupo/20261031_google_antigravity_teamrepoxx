package com.example.receiptreminder.geofence

import geofence.GeofenceResyncWorker
import geofence.GeofenceResyncWorker.Companion.Outcome
import geofence.retryWithBackoff
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class GeofenceRetryTest {
    @Test
    fun succeedsOnFirstTry_noDelay() = runTest {
        var calls = 0
        assertTrue(retryWithBackoff(listOf(500, 1500)) { calls++ })
        assertEquals(1, calls)
        assertEquals(0L, testScheduler.currentTime)
    }

    @Test
    fun recoversAfterTemporaryFailures() = runTest {
        var calls = 0
        val ok = retryWithBackoff(listOf(500, 1500, 3000)) {
            calls++
            if (calls < 3) throw IllegalStateException("temporary")
        }
        assertTrue(ok)
        assertEquals(3, calls)
        assertEquals(2000L, testScheduler.currentTime)
    }

    @Test
    fun givesUpAfterAllAttempts() = runTest {
        var calls = 0
        val ok = retryWithBackoff(listOf(500, 1500, 3000)) {
            calls++
            throw IllegalStateException("always")
        }
        assertFalse(ok)
        assertEquals(4, calls)
    }

    @Test
    fun missingPermission_isNotRetried() = runTest {
        var calls = 0
        val ok = retryWithBackoff(listOf(500, 1500)) {
            calls++
            throw SecurityException("no permission")
        }
        assertFalse(ok)
        assertEquals(1, calls)
    }

    @Test
    fun cancellationIsNotSwallowed() = runTest {
        try {
            retryWithBackoff(listOf(500)) { throw kotlinx.coroutines.CancellationException("c") }
            fail("キャンセルは投げ直すはず")
        } catch (_: kotlinx.coroutines.CancellationException) {
        }
    }

    @Test
    fun workerOutcome_retriesTemporaryFailureThenGivesUp() = runTest {
        val failing: suspend () -> Unit = { throw IllegalStateException("x") }
        assertEquals(Outcome.Success, GeofenceResyncWorker.outcomeOf(0) { })
        assertEquals(Outcome.Retry, GeofenceResyncWorker.outcomeOf(0, failing))
        assertEquals(Outcome.GiveUp, GeofenceResyncWorker.outcomeOf(GeofenceResyncWorker.MAX_ATTEMPTS - 1, failing))
        // 権限がなければ待っても直らないので再試行しない
        assertEquals(Outcome.Success, GeofenceResyncWorker.outcomeOf(0) { throw SecurityException("denied") })
    }
}
