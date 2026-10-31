package geofence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.receiptreminder.ReceiptReminderApplication
import com.example.receiptreminder.data.bridge.AndroidStoreNotificationSender
import com.example.receiptreminder.domain.notify.StoreArrivalNotifier
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * ジオフェンスへの進入を受け取り、発火した店舗ごとに通知の可否を判定して通知する。
 * 判定は StoreArrivalNotifier(S-3〜S-6)に任せ、goAsync で非同期に処理する。
 */
class GeofenceBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) {
            // 位置情報が無効になると Play services が登録を消す。印を消して Worker で復帰を待つ
            runCatching { GeofenceRecovery(
                invalidateSyncState = { GeofenceSyncGate(PrefsSyncStateStore.of(context)).invalidate() },
                scheduleResync = { GeofenceResyncWorker.enqueue(context) }
            ).onError(event.errorCode) }
            return
        }
        if (event.geofenceTransition != Geofence.GEOFENCE_TRANSITION_ENTER) return
        val storeIds = event.triggeringGeofences?.map { it.requestId }.orEmpty()
        if (storeIds.isEmpty()) return

        val appContext = context.applicationContext
        val container = (appContext as? ReceiptReminderApplication)?.container ?: return
        val notifier = StoreArrivalNotifier(
            storeRepository = container.storeRepository,
            shoppingListRepository = container.shoppingListRepository,
            notificationSettingsRepository = container.notificationSettingsRepository,
            sender = AndroidStoreNotificationSender(appContext)
        )
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                withTimeoutOrNull(RECEIVER_TIMEOUT_MS) { notifier.onStoresEntered(storeIds) }
            } catch (_: Exception) {
                // 受信側で落とさない
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        /** goAsync の猶予(約 10 秒)より前に終える */
        private const val RECEIVER_TIMEOUT_MS = 8_000L
    }
}
