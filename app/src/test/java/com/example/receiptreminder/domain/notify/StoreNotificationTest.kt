package com.example.receiptreminder.domain.notify

import com.example.receiptreminder.data.bridge.AndroidStoreNotificationSender
import com.example.receiptreminder.domain.model.NotificationTimeRange
import com.example.receiptreminder.domain.model.Product
import com.example.receiptreminder.domain.model.ShoppingItem
import com.example.receiptreminder.domain.model.StockStatus
import com.example.receiptreminder.domain.model.Store
import com.example.receiptreminder.fake.FakeNotificationSettingsRepository
import com.example.receiptreminder.fake.FakeShoppingListRepository
import com.example.receiptreminder.fake.FakeStoreRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class StoreNotificationPolicyTest {
    private val today = LocalDate.of(2026, 10, 31)
    private val noon = LocalTime.of(12, 0)
    private val range = NotificationTimeRange()
    private val store = Store(id = "1", name = "A", latitude = 0.0, longitude = 0.0)

    private fun item(name: String, status: StockStatus): ShoppingItem {
        val days = when (status) {
            StockStatus.OUT_OF_STOCK_OR_OVERDUE -> -1
            StockStatus.RUNNING_OUT -> 3
            else -> 30
        }
        val product = Product(id = name, name = name, remainingDays = days)
        assertEquals(status, product.status)
        return ShoppingItem(product = product)
    }

    private val running = listOf(item("洗剤", StockStatus.RUNNING_OUT))

    @Test
    fun storeNotFound() {
        assertEquals(
            StoreNotificationDecision.StoreNotFound,
            StoreNotificationPolicy.decide(null, range, noon, today, running)
        )
    }

    @Test
    fun notifyDisabled() {
        assertEquals(
            StoreNotificationDecision.NotifyDisabled,
            StoreNotificationPolicy.decide(store.copy(notifyEnabled = false), range, noon, today, running)
        )
    }

    @Test
    fun outsideTimeRange_usesConfiguredRange() {
        val custom = NotificationTimeRange(10, 0, 12, 0)
        assertEquals(
            StoreNotificationDecision.OutsideTimeRange,
            StoreNotificationPolicy.decide(store, custom, LocalTime.of(12, 1), today, running)
        )
        assertEquals(
            StoreNotificationDecision.OutsideTimeRange,
            StoreNotificationPolicy.decide(store, range, LocalTime.of(7, 59), today, running)
        )
        assertTrue(
            StoreNotificationPolicy.decide(store, custom, LocalTime.of(10, 0), today, running)
                is StoreNotificationDecision.Notify
        )
    }

    @Test
    fun alreadyNotifiedToday_butNotYesterday() {
        assertEquals(
            StoreNotificationDecision.AlreadyNotifiedToday,
            StoreNotificationPolicy.decide(store.copy(lastNotifiedOn = today), range, noon, today, running)
        )
        assertTrue(
            StoreNotificationPolicy.decide(store.copy(lastNotifiedOn = today.minusDays(1)), range, noon, today, running)
                is StoreNotificationDecision.Notify
        )
    }

    @Test
    fun nothingRunningOut() {
        val sufficient = listOf(item("洗剤", StockStatus.SUFFICIENT))
        assertEquals(
            StoreNotificationDecision.NothingRunningOut,
            StoreNotificationPolicy.decide(store, range, noon, today, sufficient)
        )
        assertEquals(
            StoreNotificationDecision.NothingRunningOut,
            StoreNotificationPolicy.decide(store, range, noon, today, emptyList())
        )
    }

    @Test
    fun notify_messageForOneAndMany() {
        val one = StoreNotificationPolicy.decide(store, range, noon, today, running) as StoreNotificationDecision.Notify
        assertEquals("洗剤がなくなりそうです", one.message)
        val many = StoreNotificationPolicy.decide(
            store, range, noon, today,
            listOf(
                item("洗剤", StockStatus.OUT_OF_STOCK_OR_OVERDUE),
                item("ティッシュ", StockStatus.RUNNING_OUT),
                item("歯磨き粉", StockStatus.RUNNING_OUT),
                item("牛乳", StockStatus.SUFFICIENT)
            )
        ) as StoreNotificationDecision.Notify
        assertEquals("洗剤など 3 点がなくなりそうです", many.message)
        assertEquals(3, many.itemCount)
    }

    @Test
    fun notificationIds_differPerStore() {
        assertNotEquals(
            AndroidStoreNotificationSender.notificationIdFor("1"),
            AndroidStoreNotificationSender.notificationIdFor("2")
        )
        assertEquals(
            AndroidStoreNotificationSender.notificationIdFor("7"),
            AndroidStoreNotificationSender.notificationIdFor("7")
        )
    }
}

class StoreArrivalNotifierTest {
    private val today = LocalDate.of(2026, 10, 31)

    private class Env {
        val stores = FakeStoreRepository()
        val settings = FakeNotificationSettingsRepository()
        val list = FakeShoppingListRepository()
        val sent = mutableListOf<Triple<String, String, String>>()
        var senderResult = true
    }

    private fun notifier(env: Env, time: LocalTime = LocalTime.of(12, 0), day: LocalDate = today) =
        StoreArrivalNotifier(
            storeRepository = env.stores,
            shoppingListRepository = env.list,
            notificationSettingsRepository = env.settings,
            sender = StoreNotificationSender { id, t, m ->
                env.sent += Triple(id, t, m)
                env.senderResult
            },
            currentTimeProvider = { time },
            todayProvider = { day }
        )

    private suspend fun enabledStores(env: Env) = env.stores.getStoresStream().first().filter { it.notifyEnabled }

    @Test
    fun notifiesOncePerDayAndRecordsDate() = runTest {
        val env = Env()
        val s = enabledStores(env).first()
        val n = notifier(env)

        assertTrue(n.onStoreEntered(s.id))
        assertEquals(1, env.sent.size)
        assertEquals(s.id, env.sent[0].first)
        assertEquals(today, env.stores.getStoresStream().first().first { it.id == s.id }.lastNotifiedOn)

        assertFalse(n.onStoreEntered(s.id))
        assertEquals(1, env.sent.size)

        // 翌日は再び通知する
        assertTrue(notifier(env, day = today.plusDays(1)).onStoreEntered(s.id))
    }

    @Test
    fun outsideTimeRange_doesNotSend() = runTest {
        val env = Env()
        val s = enabledStores(env).first()
        assertFalse(notifier(env, time = LocalTime.of(22, 0)).onStoreEntered(s.id))
        assertTrue(env.sent.isEmpty())
    }

    @Test
    fun unknownStore_doesNotSend() = runTest {
        val env = Env()
        assertFalse(notifier(env).onStoreEntered("no-such"))
        assertTrue(env.sent.isEmpty())
    }

    @Test
    fun senderFailure_doesNotRecordDate() = runTest {
        val env = Env()
        val s = enabledStores(env).first()
        env.senderResult = false
        assertFalse(notifier(env).onStoreEntered(s.id))
        assertNull(env.stores.getStoresStream().first().first { it.id == s.id }.lastNotifiedOn)
    }

    @Test
    fun multipleStores_judgedIndividually() = runTest {
        val env = Env()
        val ids = enabledStores(env).map { it.id }
        val disabled = env.stores.getStoresStream().first().first { !it.notifyEnabled }.id
        val count = notifier(env).onStoresEntered(ids + disabled + "no-such" + ids.first())
        assertEquals(ids.size, count)
        assertEquals(ids.size, env.sent.map { it.first }.distinct().size)
    }
}
