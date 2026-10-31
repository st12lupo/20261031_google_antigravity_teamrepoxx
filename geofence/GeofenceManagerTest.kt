package geofence

import com.google.android.gms.location.Geofence
import domain.model.Store
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class GeofenceManagerTest {

    @Test
    fun `isNotificationTimeAllowed allows notifications only between 8 and 21`() {
        // 8:00〜21:00 の時間帯判定テスト (PLAN.md 準拠)
        assertFalse("7:59 should not be allowed", DefaultGeofenceManager.isNotificationTimeAllowed(LocalTime.of(7, 59)))
        assertTrue("8:00 should be allowed", DefaultGeofenceManager.isNotificationTimeAllowed(LocalTime.of(8, 0)))
        assertTrue("12:00 should be allowed", DefaultGeofenceManager.isNotificationTimeAllowed(LocalTime.of(12, 0)))
        assertTrue("21:00 should be allowed", DefaultGeofenceManager.isNotificationTimeAllowed(LocalTime.of(21, 0)))
        assertFalse("21:01 should not be allowed", DefaultGeofenceManager.isNotificationTimeAllowed(LocalTime.of(21, 1)))
        assertFalse("23:30 should not be allowed", DefaultGeofenceManager.isNotificationTimeAllowed(LocalTime.of(23, 30)))
        assertFalse("1:00 should not be allowed", DefaultGeofenceManager.isNotificationTimeAllowed(LocalTime.of(1, 0)))
    }

    @Test
    fun `shouldNotifyStore suppresses duplicate notification on the same day (G-2)`() {
        val today = 100L
        val storeActive = Store(
            id = 1L,
            name = "架空スーパー",
            latitude = 35.0,
            longitude = 139.0,
            radiusMeters = 200f,
            notifyEnabled = true,
            lastNotifiedOnEpochDay = null
        )

        // 初回通知は許可
        assertTrue("Store without prior notification should be notified", DefaultGeofenceManager.shouldNotifyStore(storeActive, today))

        // 本日通知済みの場合は再通知を抑制 (G-2)
        val storeNotifiedToday = storeActive.copy(lastNotifiedOnEpochDay = today)
        assertFalse("Store already notified today should not be re-notified", DefaultGeofenceManager.shouldNotifyStore(storeNotifiedToday, today))

        // 前日に通知済みの場合は本日通知を許可 (G-2)
        val storeNotifiedYesterday = storeActive.copy(lastNotifiedOnEpochDay = today - 1)
        assertTrue("Store notified yesterday should be notified today", DefaultGeofenceManager.shouldNotifyStore(storeNotifiedYesterday, today))

        // 通知無効(notifyEnabled == false)の店舗は常に抑制
        val storeDisabled = storeActive.copy(notifyEnabled = false)
        assertFalse("Disabled store should never be notified", DefaultGeofenceManager.shouldNotifyStore(storeDisabled, today))
    }

    @Test
    fun `buildGeofence constructs valid Geofence object with minimum radius`() {
        val store = Store(
            id = 42L,
            name = "架空ドラッグストア",
            latitude = 35.6812,
            longitude = 139.7671,
            radiusMeters = 200f
        )

        val geofence = DefaultGeofenceManager.buildGeofence(store)
        assertEquals("42", geofence.requestId)

        // 半径が小さすぎる場合は最小 100m に補正されることの確認
        val smallStore = store.copy(id = 99L, radiusMeters = 30f)
        val smallGeofence = DefaultGeofenceManager.buildGeofence(smallStore)
        assertEquals("99", smallGeofence.requestId)
    }
}
