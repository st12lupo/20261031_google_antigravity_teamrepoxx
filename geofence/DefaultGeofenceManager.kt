package geofence

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Task
import domain.geofence.GeofenceManager
import domain.model.Store
import kotlinx.coroutines.suspendCancellableCoroutine
import java.time.LocalTime
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class DefaultGeofenceManager(
    private val context: Context,
    private val geofencingClient: GeofencingClient = LocationServices.getGeofencingClient(context)
) : GeofenceManager {

    private val geofencePendingIntent: PendingIntent by lazy {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java).apply {
            action = ACTION_GEOFENCE_EVENT
        }
        PendingIntent.getBroadcast(
            context,
            GEOFENCE_PENDING_INTENT_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    /**
     * 通知が有効(notifyEnabled == true)な店舗一覧を端末のジオフェンスに登録する。
     */
    override suspend fun registerGeofences(stores: List<Store>) {
        val activeStores = stores.filter { it.notifyEnabled }
        if (activeStores.isEmpty()) {
            unregisterAllGeofences()
            return
        }

        val geofences = activeStores.map { store ->
            buildGeofence(store)
        }

        val request = GeofencingRequest.Builder().apply {
            setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            addGeofences(geofences)
        }.build()

        try {
            awaitTask(geofencingClient.addGeofences(request, geofencePendingIntent))
        } catch (_: SecurityException) {
            // 位置情報権限がない場合は安全にスキップ
        } catch (_: Exception) {
            // ジオフェンス登録エラーでクラッシュさせない
        }
    }

    /**
     * 登録済みのすべての店舗ジオフェンスを解除する。
     */
    override suspend fun unregisterAllGeofences() {
        try {
            awaitTask(geofencingClient.removeGeofences(geofencePendingIntent))
        } catch (_: Exception) {
            // 解除エラーでクラッシュさせない
        }
    }

    private suspend fun <T> awaitTask(task: Task<T>): T = suspendCancellableCoroutine { continuation ->
        task.addOnSuccessListener { result ->
            continuation.resume(result)
        }.addOnFailureListener { exception ->
            continuation.resumeWithException(exception)
        }
    }

    companion object {
        const val ACTION_GEOFENCE_EVENT = "com.example.receiptreminder.ACTION_GEOFENCE_EVENT"
        const val GEOFENCE_PENDING_INTENT_REQUEST_CODE = 1001

        /**
         * Store モデルから Play Services の Geofence オブジェクトを構築する。
         */
        fun buildGeofence(store: Store): Geofence {
            return Geofence.Builder()
                .setRequestId(store.id.toString())
                .setCircularRegion(
                    store.latitude,
                    store.longitude,
                    store.radiusMeters.coerceAtLeast(100f)
                )
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
                .build()
        }

        /**
         * 通知時間帯判定 (PLAN.md: 既定 8:00〜21:00、時間外に進入しても通知しない)。
         */
        fun isNotificationTimeAllowed(
            currentTime: LocalTime,
            startHour: Int = 8,
            startMinute: Int = 0,
            endHour: Int = 21,
            endMinute: Int = 0
        ): Boolean {
            val start = LocalTime.of(startHour, startMinute)
            val end = LocalTime.of(endHour, endMinute)
            return !currentTime.isBefore(start) && !currentTime.isAfter(end)
        }

        /**
         * 同一店舗の再通知判定 (要件 G-2: その日は再通知しない)。
         */
        fun shouldNotifyStore(store: Store, currentEpochDay: Long): Boolean {
            if (!store.notifyEnabled) return false
            val lastNotified = store.lastNotifiedOnEpochDay ?: return true
            return lastNotified < currentEpochDay
        }
    }
}
