package com.example.receiptreminder.data.bridge

import android.content.Context
import com.example.receiptreminder.domain.model.Store as UiStore
import com.example.receiptreminder.domain.notify.StoreArrivalNotifier
import com.example.receiptreminder.domain.repository.GeofenceManager as UiGeofenceManager
import com.example.receiptreminder.domain.repository.NotificationSettingsRepository
import com.example.receiptreminder.domain.repository.ShoppingListRepository
import com.example.receiptreminder.domain.repository.StoreRepository
import domain.geofence.GeofenceManager as DomainGeofenceManager
import domain.model.Store as DbStore
import geofence.DefaultGeofenceManager
import geofence.GeofenceSyncGate
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalTime

/**
 * ジオフェンス管理インターフェースの実装ブリッジ。
 * Play Services の GeofenceClient (DefaultGeofenceManager) に委譲し、
 * 店舗への進入シミュレーションおよび通知判定ロジックを実行する。
 * 登録・解除の失敗は例外で呼び出し元（StoresViewModel）に伝える。
 */
class RealGeofenceManager(
    private val context: Context? = null,
    private val defaultGeofenceManager: DomainGeofenceManager? = context?.let { DefaultGeofenceManager(it) },
    private val storeRepository: StoreRepository,
    private val shoppingListRepository: ShoppingListRepository? = null,
    private val notificationSettingsRepository: NotificationSettingsRepository? = null,
    private val currentTimeProvider: () -> LocalTime = { LocalTime.now() },
    private val todayProvider: () -> LocalDate = { LocalDate.now() },
    /** 前回同期の記録。null のときは毎回登録し直す */
    private val syncGate: GeofenceSyncGate? = null,
    /** 権限・起動回数・更新時刻など、変わったら登録し直すべき端末の状態の印 */
    private val environmentProvider: () -> String = { "" }
) : UiGeofenceManager {

    /** 登録に失敗したとき(権限なしなど)は握りつぶさず、例外を呼び出し元へ伝える */
    override suspend fun registerGeofence(store: UiStore) {
        val stores = storeRepository.getStoresStream().first()
            .filter { it.notifyEnabled || it.id == store.id }
            .map { it.toDbStore() }
        applyAndRecord(stores)
    }

    /** 削除・通知オフ後に、残りの通知オンの店舗だけを登録し直す(S-1, S-3)。失敗は例外で伝える */
    override suspend fun unregisterGeofence(storeId: String) {
        syncActiveStores { it.id != storeId }
    }

    /** 再起動・更新・起動時に、通知オンの店舗の監視を登録し直す(N-6)。失敗は例外で伝える */
    override suspend fun recheckAllGeofences() {
        syncActiveStores { true }
    }

    /** アプリ起動時: 店舗・権限・端末の状態が前回の同期から変わらなければ登録し直さない(ENTER の取りこぼし防止) */
    override suspend fun syncIfChanged() {
        val gate = syncGate ?: return recheckAllGeofences()
        val stores = storeRepository.getStoresStream().first()
            .filter { it.notifyEnabled }
            .map { it.toDbStore() }
        if (!gate.shouldSync(GeofenceSyncGate.signatureOf(stores, environmentProvider()))) return
        applyAndRecord(stores)
    }

    private suspend fun syncActiveStores(include: (UiStore) -> Boolean) {
        val stores = storeRepository.getStoresStream().first()
            .filter { it.notifyEnabled && include(it) }
            .map { it.toDbStore() }
        applyAndRecord(stores)
    }

    /**
     * 登録を反映し、成功したときだけ同期済みの印を残す。
     * 先に印を消すので、失敗・中断したときは次の起動で必ず登録し直される。
     */
    private suspend fun applyAndRecord(stores: List<DbStore>) {
        syncGate?.invalidate()
        if (stores.none { it.notifyEnabled }) {
            defaultGeofenceManager?.unregisterAllGeofences()
        } else {
            defaultGeofenceManager?.registerGeofences(stores)
        }
        syncGate?.markSynced(GeofenceSyncGate.signatureOf(stores, environmentProvider()))
    }

    /** 判定は本番の受信と同じ StoreArrivalNotifier を使う */
    override suspend fun simulateStoreVisit(storeId: String): Boolean {
        return try {
            StoreArrivalNotifier(
                storeRepository = storeRepository,
                shoppingListRepository = shoppingListRepository,
                notificationSettingsRepository = notificationSettingsRepository,
                sender = context?.let { AndroidStoreNotificationSender(it) },
                currentTimeProvider = currentTimeProvider,
                todayProvider = todayProvider
            ).onStoreEntered(storeId)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
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
}
