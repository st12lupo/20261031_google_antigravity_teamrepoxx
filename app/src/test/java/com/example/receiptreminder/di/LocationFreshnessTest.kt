package com.example.receiptreminder.di

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationFreshnessTest {
    @Test
    fun cachedLocation_onlyUsedWhenRecent() {
        val now = 1_000_000L
        assertTrue(RealAppContainer.isRecentEnough(now - 5_000, now))
        assertTrue(RealAppContainer.isRecentEnough(now - RealAppContainer.MAX_CACHED_LOCATION_AGE_MS, now))
        assertFalse(RealAppContainer.isRecentEnough(now - RealAppContainer.MAX_CACHED_LOCATION_AGE_MS - 1, now))
        assertFalse(RealAppContainer.isRecentEnough(now + 1, now))
    }
}
