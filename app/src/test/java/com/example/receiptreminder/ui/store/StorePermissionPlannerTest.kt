package com.example.receiptreminder.ui.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StorePermissionPlannerTest {

    private val none = StorePermissionStatus(false, false, false)
    private val foregroundOnly = StorePermissionStatus(true, false, false)
    private val all = StorePermissionStatus(true, true, true)

    @Test
    fun firstRequest_android13_includesLocationAndNotification_butNotBackground() {
        val request = StorePermissionPlanner.firstRequest(none, 33)
        assertTrue("android.permission.ACCESS_FINE_LOCATION" in request)
        assertTrue("android.permission.POST_NOTIFICATIONS" in request)
        assertFalse("android.permission.ACCESS_BACKGROUND_LOCATION" in request)
    }

    @Test
    fun firstRequest_belowAndroid13_omitsNotification() {
        val request = StorePermissionPlanner.firstRequest(none, 30)
        assertFalse("android.permission.POST_NOTIFICATIONS" in request)
        assertTrue("android.permission.ACCESS_FINE_LOCATION" in request)
    }

    @Test
    fun firstRequest_whenGranted_isEmpty() {
        assertEquals(emptyList<String>(), StorePermissionPlanner.firstRequest(all, 34))
    }

    @Test
    fun background_requestedOnlyAfterForeground() {
        assertFalse(StorePermissionPlanner.needsBackgroundRequest(none, 33))
        assertTrue(StorePermissionPlanner.needsBackgroundRequest(foregroundOnly, 33))
        assertTrue(StorePermissionPlanner.needsBackgroundRequest(foregroundOnly, 29))
        assertFalse(StorePermissionPlanner.needsBackgroundRequest(foregroundOnly, 28))
        assertFalse(StorePermissionPlanner.needsBackgroundRequest(all, 33))
    }

    @Test
    fun shouldExplain_beforeFirstRequestOrWhenForegroundMissing() {
        assertTrue(StorePermissionPlanner.shouldExplain(none, alreadyAsked = false))
        assertTrue(StorePermissionPlanner.shouldExplain(none, alreadyAsked = true))
        assertTrue(StorePermissionPlanner.shouldExplain(foregroundOnly, alreadyAsked = false))
        assertFalse(StorePermissionPlanner.shouldExplain(foregroundOnly, alreadyAsked = true))
        assertFalse(StorePermissionPlanner.shouldExplain(all, alreadyAsked = false))
    }

    @Test
    fun storeNotificationsAvailable_needsAllThree() {
        assertTrue(all.storeNotificationsAvailable)
        assertFalse(foregroundOnly.storeNotificationsAvailable)
        assertFalse(StorePermissionStatus(true, true, false).storeNotificationsAvailable)
    }
}
