package com.example.receiptreminder.domain.notify

import com.example.receiptreminder.domain.model.NotificationTimeRange
import com.example.receiptreminder.domain.model.ShoppingItem
import com.example.receiptreminder.domain.model.StockStatus
import com.example.receiptreminder.domain.model.Store
import com.example.receiptreminder.domain.repository.NotificationSettingsRepository
import com.example.receiptreminder.domain.repository.ShoppingListRepository
import com.example.receiptreminder.domain.repository.StoreRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalTime

/** 店舗に入ったときの通知判定の結果 */
sealed interface StoreNotificationDecision {
    /** 店舗が登録されていない(削除済みなど) */
    data object StoreNotFound : StoreNotificationDecision

    /** 店舗の通知がオフ(S-3) */
    data object NotifyDisabled : StoreNotificationDecision

    /** 通知する時間帯の外(S-4) */
    data object OutsideTimeRange : StoreNotificationDecision

    /** 今日はこの店舗で通知済み(S-6) */
    data object AlreadyNotifiedToday : StoreNotificationDecision

    /** なくなりそうな商品がない(S-5) */
    data object NothingRunningOut : StoreNotificationDecision

    /** 通知する */
    data class Notify(val store: Store, val itemCount: Int, val message: String) : StoreNotificationDecision
}

/** Android に依存しない通知判定(S-3〜S-6、S-8)。本番の受信とシミュレーションで共用する */
object StoreNotificationPolicy {

    const val TITLE = "近くに店舗があります"

    fun decide(
        store: Store?,
        timeRange: NotificationTimeRange,
        now: LocalTime,
        today: LocalDate,
        shoppingItems: List<ShoppingItem>
    ): StoreNotificationDecision {
        if (store == null) return StoreNotificationDecision.StoreNotFound
        if (!store.notifyEnabled) return StoreNotificationDecision.NotifyDisabled
        if (!timeRange.isWithinRange(now)) return StoreNotificationDecision.OutsideTimeRange
        if (store.lastNotifiedOn == today) return StoreNotificationDecision.AlreadyNotifiedToday
        // 初版では全商品を対象とする(S-8)
        val runningOut = shoppingItems.filter {
            it.product.status == StockStatus.RUNNING_OUT ||
                it.product.status == StockStatus.OUT_OF_STOCK_OR_OVERDUE
        }
        if (runningOut.isEmpty()) return StoreNotificationDecision.NothingRunningOut
        return StoreNotificationDecision.Notify(
            store = store,
            itemCount = runningOut.size,
            message = buildMessage(runningOut.first().product.name, runningOut.size)
        )
    }

    /** 例:「洗剤など 3 点がなくなりそうです」 */
    fun buildMessage(firstItemName: String, count: Int): String =
        if (count > 1) "${firstItemName}など $count 点がなくなりそうです"
        else "${firstItemName}がなくなりそうです"
}

/** 通知を出す処理。端末の通知を出せたとき true を返す */
fun interface StoreNotificationSender {
    fun send(storeId: String, title: String, message: String): Boolean
}

/**
 * 店舗に入ったときの判定と通知、最終通知日の更新をまとめて行う。
 * [sender] が null のときは通知を出さず、判定と最終通知日の更新だけ行う(テスト・デバッグ用)。
 */
class StoreArrivalNotifier(
    private val storeRepository: StoreRepository,
    private val shoppingListRepository: ShoppingListRepository?,
    private val notificationSettingsRepository: NotificationSettingsRepository?,
    private val sender: StoreNotificationSender?,
    private val currentTimeProvider: () -> LocalTime = { LocalTime.now() },
    private val todayProvider: () -> LocalDate = { LocalDate.now() }
) {
    /** 1 店舗分を判定して通知する。通知したとき true */
    suspend fun onStoreEntered(storeId: String): Boolean {
        val store = storeRepository.getStoresStream().first().firstOrNull { it.id == storeId }
        val range = notificationSettingsRepository?.getNotificationTimeRangeStream()?.first()
            ?: NotificationTimeRange()
        val items = shoppingListRepository?.getShoppingItemsStream()?.first() ?: emptyList()
        val today = todayProvider()
        val decision = StoreNotificationPolicy.decide(store, range, currentTimeProvider(), today, items)
        if (decision !is StoreNotificationDecision.Notify) return false

        if (sender != null && !sender.send(decision.store.id, StoreNotificationPolicy.TITLE, decision.message)) {
            return false
        }
        storeRepository.updateStore(decision.store.copy(lastNotifiedOn = today))
        return true
    }

    /** 発火した店舗ごとに判定する。1 店舗の失敗で他の店舗を止めない。通知した件数を返す */
    suspend fun onStoresEntered(storeIds: List<String>): Int {
        var notified = 0
        for (id in storeIds.distinct()) {
            val ok = try {
                onStoreEntered(id)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            }
            if (ok) notified++
        }
        return notified
    }
}
