package com.example.receiptreminder.data.bridge

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.receiptreminder.domain.model.StockStatus
import com.example.receiptreminder.domain.model.Store as UiStore
import com.example.receiptreminder.domain.repository.GeofenceManager as UiGeofenceManager
import com.example.receiptreminder.domain.repository.NotificationSettingsRepository
import com.example.receiptreminder.domain.repository.ShoppingListRepository
import com.example.receiptreminder.domain.repository.StoreRepository
import domain.geofence.GeofenceManager as DomainGeofenceManager
import domain.model.Store as DbStore
import geofence.DefaultGeofenceManager
import geofence.GeofenceBroadcastReceiver
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalTime

/**
 * ジオフェンス管理インターフェースの実装ブリッジ。
 * Play Services の GeofenceClient (DefaultGeofenceManager) に委譲し、
 * 店舗への進入シミュレーションおよび通知判定ロジックを実行する。
 * 位置情報権限の拒否時（SecurityException）でも例外でクラッシュさせず、安全に処理する。
 */
class RealGeofenceManager(
    private val context: Context? = null,
    private val defaultGeofenceManager: DomainGeofenceManager? = context?.let { DefaultGeofenceManager(it) },
    private val storeRepository: StoreRepository,
    private val shoppingListRepository: ShoppingListRepository? = null,
    private val notificationSettingsRepository: NotificationSettingsRepository? = null,
    private val currentTimeProvider: () -> LocalTime = { LocalTime.now() },
    private val todayProvider: () -> LocalDate = { LocalDate.now() }
) : UiGeofenceManager {

    override suspend fun registerGeofence(store: UiStore) {
        try {
            val stores = storeRepository.getStoresStream().first()
                .filter { it.notifyEnabled || it.id == store.id }
                .map { it.toDbStore() }
            defaultGeofenceManager?.registerGeofences(stores)
        } catch (_: SecurityException) {
            // 位置情報権限拒否時はクラッシュさせずにスキップ
        } catch (_: Exception) {
            // ジオフェンス登録エラーでクラッシュさせない
        }
    }

    override suspend fun unregisterGeofence(storeId: String) {
        try {
            val stores = storeRepository.getStoresStream().first()
                .filter { it.notifyEnabled && it.id != storeId }
                .map { it.toDbStore() }
            if (stores.isEmpty()) {
                defaultGeofenceManager?.unregisterAllGeofences()
            } else {
                defaultGeofenceManager?.registerGeofences(stores)
            }
        } catch (_: SecurityException) {
            // 位置情報権限拒否時は安全にスキップ
        } catch (_: Exception) {
            // エラーでクラッシュさせない
        }
    }

    override suspend fun recheckAllGeofences() {
        try {
            val stores = storeRepository.getStoresStream().first()
                .filter { it.notifyEnabled }
                .map { it.toDbStore() }
            if (stores.isEmpty()) {
                defaultGeofenceManager?.unregisterAllGeofences()
            } else {
                defaultGeofenceManager?.registerGeofences(stores)
            }
        } catch (_: SecurityException) {
            // 位置情報権限拒否時は安全にスキップ
        } catch (_: Exception) {
            // エラーでクラッシュさせない
        }
    }

    override suspend fun simulateStoreVisit(storeId: String): Boolean {
        return try {
            val stores = storeRepository.getStoresStream().first()
            val store = stores.firstOrNull { it.id == storeId } ?: return false

            // 1. 店舗の通知がオンか確認 (要件 S-3, G-2)
            if (!store.notifyEnabled) return false

            // 2. 通知時間帯内か確認 (要件 S-4, PLAN.md: 既定 8:00〜21:00)
            val now = currentTimeProvider()
            val isTimeAllowed = if (notificationSettingsRepository != null) {
                val range = notificationSettingsRepository.getNotificationTimeRangeStream().first()
                range.isWithinRange(now)
            } else {
                DefaultGeofenceManager.isNotificationTimeAllowed(now)
            }
            if (!isTimeAllowed) return false

            // 3. 同じ店舗での通知は1日1回まで (要件 S-6, G-2)
            val today = todayProvider()
            if (store.lastNotifiedOn == today) return false

            // 4. なくなりそうな商品があるか確認 (要件 S-5)
            val shoppingItems = shoppingListRepository?.getShoppingItemsStream()?.first() ?: emptyList()
            val runningOutItems = shoppingItems.filter {
                it.product.status == StockStatus.RUNNING_OUT ||
                    it.product.status == StockStatus.OUT_OF_STOCK_OR_OVERDUE
            }
            if (runningOutItems.isEmpty()) return false

            // 5. 通知メッセージ作成 (例:「洗剤など 3 点がなくなりそうです」)
            val firstItemName = runningOutItems.first().product.name
            val count = runningOutItems.size
            val message = if (count > 1) {
                "${firstItemName}など ${count} 点がなくなりそうです"
            } else {
                "${firstItemName}がなくなりそうです"
            }

            // 6. 通知送信
            context?.let { ctx ->
                sendStoreNotification(ctx, "近くに店舗があります", message)
            }

            // 7. 店舗の最終通知日を更新
            storeRepository.updateStore(store.copy(lastNotifiedOn = today))
            true
        } catch (_: SecurityException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    private fun UiStore.toDbStore(): DbStore {
        return DbStore(
            id = id.toLongOrNull() ?: 0L,
            name = name,
            latitude = latitude,
            longitude = longitude,
            radiusMeters = radiusMeters.toFloat(),
            notifyEnabled = notifyEnabled,
            lastNotifiedOnEpochDay = lastNotifiedOn?.toEpochDay()
        )
    }

    private fun sendStoreNotification(context: Context, title: String, message: String) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    GeofenceBroadcastReceiver.CHANNEL_ID,
                    "買い物リマインダー",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "店舗近くに進入した際のお買い忘れ防止通知"
                }
                notificationManager.createNotificationChannel(channel)
            }

            val launchIntent = context.packageManager?.getLaunchIntentForPackage(context.packageName)?.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val contentPendingIntent = launchIntent?.let {
                PendingIntent.getActivity(
                    context,
                    GeofenceBroadcastReceiver.NOTIFICATION_PENDING_INTENT_REQUEST_CODE,
                    it,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            }

            val notification = NotificationCompat.Builder(context, GeofenceBroadcastReceiver.CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .apply {
                    if (contentPendingIntent != null) {
                        setContentIntent(contentPendingIntent)
                    }
                }
                .build()

            notificationManager.notify(GeofenceBroadcastReceiver.NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS 権限拒否時などにクラッシュさせない
        } catch (_: Exception) {
            // クラッシュさせない
        }
    }
}
