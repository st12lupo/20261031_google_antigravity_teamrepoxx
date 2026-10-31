package com.example.receiptreminder.data.bridge

import com.example.receiptreminder.domain.model.StockStatus
import data.DefaultReminderRepository
import data.FakeProductDao
import data.FakePurchaseDao
import data.FakeReceiptDao
import data.FakeStoreDao
import domain.model.ReceiptItemData
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/** F-4 / F-5 / 履歴全削除(S-5, B-4) を、リポジトリを通した経路で確認する */
class PredictionStatusAndSnoozeTest {

    private val today = LocalDate.of(2026, 10, 31)
    private val todayEpoch = today.toEpochDay()

    private lateinit var productDao: FakeProductDao
    private lateinit var reminder: DefaultReminderRepository
    private lateinit var shopping: RealShoppingListRepository
    private lateinit var products: RealProductRepository

    @Before
    fun setup() {
        productDao = FakeProductDao()
        reminder = DefaultReminderRepository(
            productDao, FakePurchaseDao(), FakeReceiptDao(), FakeStoreDao(), { today }
        )
        val trigger = DataUpdateTrigger()
        shopping = RealShoppingListRepository(reminder, todayProvider = { today }, trigger = trigger)
        products = RealProductRepository(reminder, todayProvider = { today }, trigger = trigger)
    }

    /** 周期 cycle 日(購入 2 回の間隔)で、残り remaining 日になる商品を作る */
    private suspend fun productWith(name: String, cycle: Int, remaining: Int): Long {
        val p = reminder.getOrCreateProduct(name)
        val last = todayEpoch - (cycle - remaining)
        reminder.recordManualPurchase(p.id, last - cycle, 1.0)
        reminder.recordManualPurchase(p.id, last, 1.0)
        return p.id
    }

    private suspend fun statusOf(id: Long) = products.getProduct(id.toString())!!.status

    private suspend fun remainingOf(id: Long) = products.getProduct(id.toString())!!.remainingDays

    @Test
    fun `F-4 boundary - buffer is 20 percent of cycle clamped to 2 and 7 days`() = runTest {
        // 周期 -> 判定幅: 5->2, 10->2, 14->3, 30->6, 50->7(上限), 100->7
        val cases = listOf(5 to 2, 10 to 2, 14 to 3, 30 to 6, 50 to 7, 100 to 7)
        for ((cycle, buffer) in cases) {
            val inside = productWith("c$cycle-in", cycle, buffer)
            val outside = productWith("c$cycle-out", cycle, buffer + 1)
            assertEquals("cycle=$cycle rem=$buffer", StockStatus.RUNNING_OUT, statusOf(inside))
            assertEquals("cycle=$cycle rem=${buffer + 1}", StockStatus.SUFFICIENT, statusOf(outside))
        }
    }

    @Test
    fun `F-4 - fixed 7 days rule is gone for short cycles`() = runTest {
        // 周期 10 の残り 5 日: 旧仕様(7日以内)なら「なくなりそう」だが、周期の2割は 2 日
        assertEquals(StockStatus.SUFFICIENT, statusOf(productWith("short", 10, 5)))
        // 周期 100 の残り 7 日は上限 7 日以内
        assertEquals(StockStatus.RUNNING_OUT, statusOf(productWith("long", 100, 7)))
    }

    @Test
    fun `F-4 - overdue products are shown as overdue and listed`() = runTest {
        val id = productWith("late", 30, -3)
        val p = products.getProduct(id.toString())!!
        assertEquals(StockStatus.OUT_OF_STOCK_OR_OVERDUE, p.status)
        assertEquals(true, p.isRunningOut)
        val item = shopping.getShoppingItemsStream().first().single()
        assertEquals(StockStatus.OUT_OF_STOCK_OR_OVERDUE, item.product.status)
    }

    @Test
    fun `F-4 - shopping list and product list use the same status`() = runTest {
        productWith("a", 30, 6)
        productWith("b", 30, 7)
        productWith("c", 10, 3)
        val list = products.getProductsStream().first().associate { it.name to it.status }
        val shop = shopping.getShoppingItemsStream().first().associate { it.product.name to it.product.status }
        assertEquals(list, shop)
        assertEquals(StockStatus.RUNNING_OUT, list["a"])
        assertEquals(StockStatus.SUFFICIENT, list["b"])
        assertEquals(StockStatus.SUFFICIENT, list["c"])
    }

    @Test
    fun `F-5 - snooze moves out date back by buffer from the out date for various remaining days`() = runTest {
        // 周期 30(判定幅 6): どの残り日数でも +6
        for (rem in listOf(10, 6, 2, 1)) {
            val id = productWith("r$rem", 30, rem)
            shopping.snooze(id.toString())
            assertEquals("rem=$rem", rem + 6, remainingOf(id))
        }
        // 周期 100(上限 7)
        val big = productWith("big", 100, 3)
        shopping.snooze(big.toString())
        assertEquals(10, remainingOf(big))
        // 周期 5(下限 2)
        val small = productWith("small", 5, 1)
        shopping.snooze(small.toString())
        assertEquals(3, remainingOf(small))
    }

    @Test
    fun `F-5 - snooze on overdue or today starts from today`() = runTest {
        val overdue = productWith("overdue", 30, -10)
        shopping.snooze(overdue.toString())
        assertEquals(6, remainingOf(overdue))
        val zero = productWith("zero", 30, 0)
        shopping.snooze(zero.toString())
        assertEquals(6, remainingOf(zero))
    }

    @Test
    fun `F-5 - snoozes accumulate`() = runTest {
        val id = productWith("acc", 30, 2)
        shopping.snooze(id.toString())
        shopping.snooze(id.toString())
        shopping.snooze(id.toString())
        assertEquals(2 + 18, remainingOf(id))
    }

    @Test
    fun `F-5 - stale old snooze date does not stop the out date from moving`() = runTest {
        val id = productWith("stale", 30, 10)
        productDao.updateSnooze(id, todayEpoch - 20)
        shopping.snooze(id.toString())
        assertEquals(16, remainingOf(id))
    }

    @Test
    fun `F-5 - purchase clears snooze so next out date is not dragged by it`() = runTest {
        // 周期 3 日、最後の購入は 1 日前 -> 残り 2
        val id = productWith("tiny", 3, 2)
        shopping.snooze(id.toString())
        assertEquals(4, remainingOf(id))

        shopping.markAsPurchased(id.toString(), 1)
        assertNull(reminder.getProductById(id)!!.snoozedUntilEpochDay)
        // 間隔 [3, 1] の中央値 2 日 -> 今日の購入から 2 日後(スヌーズの +4 に引きずられない)
        assertEquals(2, remainingOf(id))
    }

    @Test
    fun `F-5 - receipt purchase also clears snooze`() = runTest {
        val id = productWith("洗剤", 30, 2)
        shopping.snooze(id.toString())
        assertTrue(reminder.getProductById(id)!!.snoozedUntilEpochDay != null)
        reminder.saveReceiptAndPurchases(
            "店", todayEpoch, 100, listOf(ReceiptItemData("洗剤", "洗剤", "日用品", 1.0, 100))
        )
        assertNull(reminder.getProductById(id)!!.snoozedUntilEpochDay)
    }

    @Test
    fun `snoozeProduct uses injected today`() = runTest {
        val p = reminder.getOrCreateProduct("x")
        reminder.snoozeProduct(p.id, 6)
        assertEquals(todayEpoch + 6, reminder.getProductById(p.id)!!.snoozedUntilEpochDay)
    }

    @Test
    fun `NO_DATA - product whose purchases were all deleted is not counted as running out`() = runTest {
        val id = productWith("gone", 30, 2)
        productWith("keep", 30, 2)
        assertEquals(2, shopping.getShoppingItemsStream().first().size)

        reminder.getPurchasesForProduct(id).forEach { reminder.deletePurchase(it.id) }

        val items = shopping.getShoppingItemsStream().first()
        assertEquals(listOf("keep"), items.map { it.product.name })
        assertEquals(1, items.count { it.product.status == StockStatus.RUNNING_OUT })
        val gone = products.getProduct(id.toString())!!
        assertEquals(StockStatus.NOT_TRACKED, gone.status)
        assertEquals(false, gone.hasPurchaseHistory)
        // 買い直せば戻る
        shopping.markAsPurchased(id.toString(), 1)
        assertEquals(2, shopping.getShoppingItemsStream().first().size)
    }

    @Test
    fun `NO_DATA - snooze on product without history does nothing`() = runTest {
        val p = reminder.getOrCreateProduct("empty")
        shopping.snooze(p.id.toString())
        assertNull(reminder.getProductById(p.id)!!.snoozedUntilEpochDay)
    }

    /** 周期 cycle 日(1 個あたり)で、直近に quantity 個買い、残り remaining 日になる商品を作る */
    private suspend fun bulkProduct(name: String, cycle: Int, quantity: Int, remaining: Int): Long {
        val p = reminder.getOrCreateProduct(name)
        val last = todayEpoch - (cycle * quantity - remaining)
        reminder.recordManualPurchase(p.id, last - cycle, 1.0)
        reminder.recordManualPurchase(p.id, last, quantity.toDouble())
        return p.id
    }

    @Test
    fun `F-4 - buffer is 20 percent of cycle per unit - cycle 10 x 3 gives 2 days`() = runTest {
        val inside = bulkProduct("bulk-in", 10, 3, 2)
        val outside = bulkProduct("bulk-out", 10, 3, 3)
        assertEquals(StockStatus.RUNNING_OUT, products.getProduct(inside.toString())!!.status)
        assertEquals(StockStatus.SUFFICIENT, products.getProduct(outside.toString())!!.status)
        // 買い物リスト(通知の対象も同じ一覧)にも同じ判定が出る
        val listed = shopping.getShoppingItemsStream().first()
        assertEquals(true, listed.first { it.product.id == inside.toString() }.product.isRunningOut)
        assertEquals(false, listed.first { it.product.id == outside.toString() }.product.isRunningOut)
    }

    @Test
    fun `F-5 - snooze uses the same buffer as the judgement - cycle 10 x 3 moves 2 days`() = runTest {
        val id = bulkProduct("bulk-snooze", 10, 3, 2)
        shopping.snooze(id.toString())
        assertEquals(4, remainingOf(id))
        // 判定幅 2 日より先になったので、もう「なくなりそう」ではない
        assertEquals(StockStatus.SUFFICIENT, products.getProduct(id.toString())!!.status)
    }

    @Test
    fun `F-5 - snoozing an overdue product leaves it running out with the buffer left`() = runTest {
        // 周期 30 で切れる日を過ぎた商品に「まだある」: 今日 + 6 日で残り 6 日。判定幅 6 日以内なので RUNNING_OUT のまま
        val id = productWith("overdue-status", 30, -10)
        shopping.snooze(id.toString())
        val product = products.getProduct(id.toString())!!
        assertEquals(6, product.remainingDays)
        assertEquals(StockStatus.RUNNING_OUT, product.status)
    }

    @Test
    fun `F-1 F-2 - two manual rows for the same day count as one purchase of two`() = runTest {
        val p = reminder.getOrCreateProduct("牛乳", "飲料・乳製品")
        reminder.recordManualPurchase(p.id, todayEpoch - 3, 1.0)
        reminder.recordManualPurchase(p.id, todayEpoch - 3, 1.0)
        // 周期 7 日 × 2 個 = 14 日後。3 日前に買ったので残り 11 日
        assertEquals(11, remainingOf(p.id))
    }

    @Test
    fun `F-1 - same day rows do not distort the cycle of a product with history`() = runTest {
        val p = reminder.getOrCreateProduct("same-day-history")
        reminder.recordManualPurchase(p.id, todayEpoch - 30, 1.0)
        reminder.recordManualPurchase(p.id, todayEpoch - 20, 1.0)
        reminder.recordManualPurchase(p.id, todayEpoch - 20, 1.0)
        reminder.recordManualPurchase(p.id, todayEpoch, 1.0)
        val product = products.getProduct(p.id.toString())!!
        assertEquals(10.0, product.cycleDays, 0.001)
        assertEquals(10, product.remainingDays)
        assertEquals(false, product.isEstimated)
    }

    @Test
    fun `product without history is reported as having no purchase history`() = runTest {
        val p = reminder.getOrCreateProduct("no-history")
        val product = products.getProduct(p.id.toString())!!
        assertEquals(false, product.hasPurchaseHistory)
        assertEquals(StockStatus.NOT_TRACKED, product.status)
        assertEquals(true, product.isTracked)
    }

    @Test
    fun `F-3 - estimated badge is off with no history and on with one purchase`() = runTest {
        val p = reminder.getOrCreateProduct("badge-check")
        assertEquals(false, products.getProduct(p.id.toString())!!.isEstimated)

        reminder.recordManualPurchase(p.id, todayEpoch - 3, 1.0)
        assertEquals(true, products.getProduct(p.id.toString())!!.isEstimated)

        reminder.getPurchasesForProduct(p.id).forEach { reminder.deletePurchase(it.id) }
        assertEquals(false, products.getProduct(p.id.toString())!!.isEstimated)
    }
}
