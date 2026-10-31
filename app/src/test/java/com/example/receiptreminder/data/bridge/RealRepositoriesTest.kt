package com.example.receiptreminder.data.bridge

import com.example.receiptreminder.domain.model.NotificationTimeRange
import com.example.receiptreminder.domain.model.PurchaseRecord
import com.example.receiptreminder.domain.model.PurchaseSource as UiPurchaseSource
import com.example.receiptreminder.domain.model.StockStatus
import com.example.receiptreminder.domain.model.Store as UiStore
import data.DefaultReminderRepository
import data.FakeProductDao
import data.FakePurchaseDao
import data.FakeReceiptDao
import data.FakeStoreDao
import domain.model.PurchaseSource as DbPurchaseSource
import domain.model.ReceiptItemData
import domain.model.Store as DbStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import prediction.DefaultConsumptionPredictor
import java.time.LocalDate

class RealRepositoriesTest {

    private lateinit var productDao: FakeProductDao
    private lateinit var purchaseDao: FakePurchaseDao
    private lateinit var receiptDao: FakeReceiptDao
    private lateinit var storeDao: FakeStoreDao
    private lateinit var reminderRepository: DefaultReminderRepository

    private val fixedToday = LocalDate.of(2026, 10, 31)
    private val todayProvider: () -> LocalDate = { fixedToday }

    @Before
    fun setup() {
        productDao = FakeProductDao()
        purchaseDao = FakePurchaseDao()
        receiptDao = FakeReceiptDao()
        storeDao = FakeStoreDao()
        reminderRepository = DefaultReminderRepository(productDao, purchaseDao, receiptDao, storeDao)
    }

    // =========================================================================
    // RealShoppingListRepository Tests
    // =========================================================================

    @Test
    fun `ShoppingList - getShoppingItemsStream returns tracked items sorted by remainingDays and partitions urgent vs safe`() = runTest {
        val repo = RealShoppingListRepository(
            reminderRepository = reminderRepository,
            todayProvider = todayProvider
        )

        // 1. 商品作成
        // 牛乳: カテゴリ「定番食品」(周期14日)
        // 洗剤: カテゴリ「日用品」(周期30日)
        // 米: カテゴリ「定番食品」(周期14日)
        val milk = reminderRepository.getOrCreateProduct("牛乳", "定番食品")
        val soap = reminderRepository.getOrCreateProduct("食器用洗剤", "日用品")
        val rice = reminderRepository.getOrCreateProduct("米", "定番食品")

        // 牛乳: 10日前に購入 -> 残り 14 - 10 = 4日 (urgent: <= 7日)
        reminderRepository.recordManualPurchase(
            productId = milk.id,
            purchasedOnEpochDay = fixedToday.minusDays(10).toEpochDay(),
            quantity = 1.0
        )

        // 洗剤: 5日前に購入 -> 残り 30 - 5 = 25日 (safe: > 7日)
        reminderRepository.recordManualPurchase(
            productId = soap.id,
            purchasedOnEpochDay = fixedToday.minusDays(5).toEpochDay(),
            quantity = 1.0
        )

        // 米: 追跡対象外にする
        reminderRepository.setTracked(rice.id, false)

        val items = repo.getShoppingItemsStream().first()

        // 追跡対象外の米は含まれない
        assertEquals(2, items.size)
        // 残り日数の昇順でソートされている (牛乳: 4日, 洗剤: 25日)
        assertEquals("牛乳", items[0].product.name)
        assertEquals(4, items[0].product.remainingDays)
        assertEquals(StockStatus.RUNNING_OUT, items[0].product.status)

        assertEquals("食器用洗剤", items[1].product.name)
        assertEquals(25, items[1].product.remainingDays)
        assertEquals(StockStatus.SUFFICIENT, items[1].product.status)

        // urgent と safe の分離確認
        val urgent = items.filter { it.product.status != StockStatus.SUFFICIENT }
        val safe = items.filter { it.product.status == StockStatus.SUFFICIENT }
        assertEquals(listOf("牛乳"), urgent.map { it.product.name })
        assertEquals(listOf("食器用洗剤"), safe.map { it.product.name })
    }

    @Test
    fun `ShoppingList - markAsPurchased records manual purchase with quantity 1 and updates flow`() = runTest {
        val repo = RealShoppingListRepository(
            reminderRepository = reminderRepository,
            todayProvider = todayProvider
        )

        val soap = reminderRepository.getOrCreateProduct("食器用洗剤", "日用品") // 周期30日
        reminderRepository.recordManualPurchase(
            productId = soap.id,
            purchasedOnEpochDay = fixedToday.minusDays(30).toEpochDay(),
            quantity = 1.0
        )

        // 購入前: 残り 0日 (30 - 30)
        var items = repo.getShoppingItemsStream().first()
        assertEquals(0, items[0].product.remainingDays)

        // 「購入済み」操作
        repo.markAsPurchased(soap.id.toString(), quantity = 1)

        // 購入後: 今日の日付で購入記録が作られ、前回との間隔 30 日が新周期となり、残り日数が 30 日になる
        items = repo.getShoppingItemsStream().first()
        assertEquals(30, items[0].product.remainingDays)

        // 購入履歴が追加されていることを確認
        val purchases = reminderRepository.getPurchasesForProduct(soap.id)
        assertEquals(2, purchases.size)
        val latest = purchases.last()
        assertEquals(fixedToday.toEpochDay(), latest.purchasedOnEpochDay)
        assertEquals(DbPurchaseSource.MANUAL, latest.source)
    }

    @Test
    fun `ShoppingList - snooze shifts predicted out date by 20 percent of cycle (2 to 7 days)`() = runTest {
        val repo = RealShoppingListRepository(
            reminderRepository = reminderRepository,
            todayProvider = todayProvider
        )

        // 洗剤: 周期30日 -> 30 * 0.2 = 6日
        val soap = reminderRepository.getOrCreateProduct("食器用洗剤", "日用品")
        reminderRepository.recordManualPurchase(
            productId = soap.id,
            purchasedOnEpochDay = fixedToday.minusDays(28).toEpochDay(),
            quantity = 1.0
        )

        // スヌーズ前: 残り 30 - 28 = 2日
        var items = repo.getShoppingItemsStream().first()
        assertEquals(2, items[0].product.remainingDays)

        // スヌーズ実行
        repo.snooze(soap.id.toString())

        // スヌーズ後: 切れる日が今日(Day T)から+6日ずれるため、残りは6日
        items = repo.getShoppingItemsStream().first()
        assertEquals(6, items[0].product.remainingDays)

        // 飲料・乳製品: 周期7日 -> 7 * 0.2 = 1.4 -> 最短2日に丸められる
        val milk = reminderRepository.getOrCreateProduct("牛乳", "飲料・乳製品")
        reminderRepository.recordManualPurchase(
            productId = milk.id,
            purchasedOnEpochDay = fixedToday.minusDays(7).toEpochDay(),
            quantity = 1.0
        )
        repo.snooze(milk.id.toString())
        val milkItem = repo.getShoppingItemsStream().first().first { it.product.name == "牛乳" }
        assertEquals(2, milkItem.product.remainingDays)
    }

    @Test
    fun `ShoppingList - stopTracking sets isTracked to false and removes from shopping list`() = runTest {
        val repo = RealShoppingListRepository(
            reminderRepository = reminderRepository,
            todayProvider = todayProvider
        )

        val soap = reminderRepository.getOrCreateProduct("食器用洗剤", "日用品")
        reminderRepository.recordManualPurchase(
            productId = soap.id,
            purchasedOnEpochDay = fixedToday.toEpochDay(),
            quantity = 1.0
        )

        assertEquals(1, repo.getShoppingItemsStream().first().size)

        repo.stopTracking(soap.id.toString())

        val items = repo.getShoppingItemsStream().first()
        assertTrue(items.isEmpty())

        val productInDb = reminderRepository.getProductById(soap.id)
        assertNotNull(productInDb)
        assertFalse(productInDb!!.isTracked)
    }

    @Test
    fun `ShoppingList - addManualPurchase creates product and records purchase`() = runTest {
        val repo = RealShoppingListRepository(
            reminderRepository = reminderRepository,
            todayProvider = todayProvider
        )

        repo.addManualPurchase(
            productName = "トイレットペーパー",
            purchasedOn = fixedToday,
            quantity = 2,
            price = 398
        )

        val items = repo.getShoppingItemsStream().first()
        assertEquals(1, items.size)
        assertEquals("トイレットペーパー", items[0].product.name)

        val product = reminderRepository.getAllProducts().first { it.name == "トイレットペーパー" }
        val purchases = reminderRepository.getPurchasesForProduct(product.id)
        assertEquals(1, purchases.size)
        assertEquals(2.0, purchases[0].quantity, 0.001)
        assertEquals(398, purchases[0].price)
        assertEquals(DbPurchaseSource.MANUAL, purchases[0].source)
    }

    // =========================================================================
    // RealProductRepository Tests
    // =========================================================================

    @Test
    fun `ProductRepo - getProductsStream and getProduct return accurate product details`() = runTest {
        val repo = RealProductRepository(
            reminderRepository = reminderRepository,
            todayProvider = todayProvider
        )

        val soap = reminderRepository.getOrCreateProduct("食器用洗剤", "日用品")
        reminderRepository.recordManualPurchase(
            productId = soap.id,
            purchasedOnEpochDay = fixedToday.minusDays(10).toEpochDay(),
            quantity = 1.0
        )

        val products = repo.getProductsStream().first()
        assertEquals(1, products.size)
        assertEquals("食器用洗剤", products[0].name)
        assertEquals(30.0, products[0].cycleDays, 0.001)
        assertEquals(20, products[0].remainingDays)
        assertTrue(products[0].isEstimated)

        val single = repo.getProduct(soap.id.toString())
        assertNotNull(single)
        assertEquals("食器用洗剤", single!!.name)
        assertEquals(20, single.remainingDays)

        val notFound = repo.getProduct("9999")
        assertNull(notFound)
    }

    @Test
    fun `ProductRepo - getProductPurchasesStream returns sorted purchases with storeName`() = runTest {
        val repo = RealProductRepository(
            reminderRepository = reminderRepository,
            todayProvider = todayProvider
        )

        val soap = reminderRepository.getOrCreateProduct("食器用洗剤", "日用品")

        // レシート経由の購入
        val receiptId = reminderRepository.saveReceiptAndPurchases(
            storeName = "ライフ 渋谷店",
            purchasedOnEpochDay = fixedToday.minusDays(20).toEpochDay(),
            totalAmount = 250,
            items = listOf(
                ReceiptItemData("食器用洗剤", "食器用洗剤", "日用品", 1.0, 250)
            )
        )

        // 手動購入
        repo.updatePurchase(
            PurchaseRecord(
                id = "0",
                productId = soap.id.toString(),
                productName = soap.name,
                purchasedOn = fixedToday.minusDays(5),
                quantity = 2,
                price = 400,
                source = UiPurchaseSource.MANUAL
            )
        )

        // 手動追加の購入
        reminderRepository.recordManualPurchase(
            productId = soap.id,
            purchasedOnEpochDay = fixedToday.minusDays(5).toEpochDay(),
            quantity = 2.0,
            price = 400,
            source = DbPurchaseSource.MANUAL
        )

        val purchases = repo.getProductPurchasesStream(soap.id.toString()).first()
        assertTrue(purchases.isNotEmpty())
        // 日付降順 (minusDays(5) -> minusDays(20))
        assertEquals(fixedToday.minusDays(5), purchases[0].purchasedOn)
        assertEquals(UiPurchaseSource.MANUAL, purchases[0].source)
        assertNull(purchases[0].storeName)

        val receiptPurchase = purchases.first { it.source == UiPurchaseSource.RECEIPT }
        assertEquals("ライフ 渋谷店", receiptPurchase.storeName)
        assertEquals(receiptId.toString(), receiptPurchase.receiptId)
    }

    @Test
    fun `ProductRepo - updateTrackedStatus, updatePurchase, deletePurchase work correctly`() = runTest {
        val repo = RealProductRepository(
            reminderRepository = reminderRepository,
            todayProvider = todayProvider
        )

        val soap = reminderRepository.getOrCreateProduct("食器用洗剤", "日用品")
        val purchaseId = reminderRepository.recordManualPurchase(
            productId = soap.id,
            purchasedOnEpochDay = fixedToday.toEpochDay(),
            quantity = 1.0,
            price = 200
        )

        // updateTrackedStatus
        repo.updateTrackedStatus(soap.id.toString(), false)
        assertFalse(reminderRepository.getProductById(soap.id)!!.isTracked)

        // updatePurchase
        val updated = PurchaseRecord(
            id = purchaseId.toString(),
            productId = soap.id.toString(),
            productName = soap.name,
            purchasedOn = fixedToday.minusDays(1),
            quantity = 3,
            price = 550,
            source = UiPurchaseSource.MANUAL
        )
        repo.updatePurchase(updated)

        val savedPurchase = reminderRepository.getPurchasesForProduct(soap.id).first()
        assertEquals(fixedToday.minusDays(1).toEpochDay(), savedPurchase.purchasedOnEpochDay)
        assertEquals(3.0, savedPurchase.quantity, 0.001)
        assertEquals(550, savedPurchase.price)

        // deletePurchase
        repo.deletePurchase(purchaseId.toString())
        assertTrue(reminderRepository.getPurchasesForProduct(soap.id).isEmpty())
    }

    // =========================================================================
    // RealPurchaseRepository Tests
    // =========================================================================

    @Test
    fun `PurchaseRepo - getAllPurchasesStream, insertPurchase, updatePurchase, deletePurchase`() = runTest {
        val repo = RealPurchaseRepository(reminderRepository)

        val soap = reminderRepository.getOrCreateProduct("食器用洗剤", "日用品")

        repo.insertPurchase(
            PurchaseRecord(
                id = "0",
                productId = soap.id.toString(),
                productName = soap.name,
                purchasedOn = fixedToday,
                quantity = 1,
                price = 198,
                source = UiPurchaseSource.MANUAL
            )
        )

        var list = repo.getAllPurchasesStream().first()
        assertEquals(1, list.size)
        assertEquals("食器用洗剤", list[0].productName)
        assertEquals(198, list[0].price)

        val pId = list[0].id
        repo.updatePurchase(list[0].copy(price = 220))
        list = repo.getAllPurchasesStream().first()
        assertEquals(220, list[0].price)

        repo.deletePurchase(pId)
        list = repo.getAllPurchasesStream().first()
        assertTrue(list.isEmpty())
    }

    // =========================================================================
    // RealStoreRepository Tests
    // =========================================================================

    @Test
    fun `StoreRepo - registerCurrentLocation registers store and enforces 100 limit`() = runTest {
        var mockLocation: Pair<Double, Double>? = 35.6812 to 139.7671
        val repo = RealStoreRepository(
            reminderRepository = reminderRepository,
            currentLocationProvider = { mockLocation }
        )

        // 正常登録
        val registered = repo.registerCurrentLocation("ライフ 渋谷店", radiusMeters = 250)
        assertEquals("ライフ 渋谷店", registered.name)
        assertEquals(35.6812, registered.latitude, 0.0001)
        assertEquals(139.7671, registered.longitude, 0.0001)
        assertEquals(250, registered.radiusMeters)
        assertTrue(registered.notifyEnabled)

        val stores = repo.getStoresStream().first()
        assertEquals(1, stores.size)
        assertEquals("ライフ 渋谷店", stores[0].name)

        // 位置情報取得失敗時の例外
        mockLocation = null
        val locationEx = try {
            repo.registerCurrentLocation("エラー店舗")
            null
        } catch (e: IllegalStateException) {
            e
        }
        assertNotNull(locationEx)
        assertTrue(locationEx!!.message!!.contains("現在地"))
        mockLocation = 35.6812 to 139.7671

        // 100件上限バリデーション
        // すでに1件あるので、残り99件追加して合計100件にする
        for (i in 2..100) {
            reminderRepository.insertStore(
                DbStore(
                    name = "店舗$i",
                    latitude = 35.0,
                    longitude = 139.0
                )
            )
        }
        assertEquals(100, reminderRepository.getAllStores().size)

        // 101件目の登録を試みると例外が発生すること
        val limitEx = try {
            repo.registerCurrentLocation("101件目の店舗")
            null
        } catch (e: IllegalStateException) {
            e
        }
        assertNotNull(limitEx)
        assertTrue(limitEx!!.message!!.contains("100件"))
    }

    @Test
    fun `StoreRepo - updateStore, deleteStore, clearAllData`() = runTest {
        var clearCallbackCalled = false
        val repo = RealStoreRepository(
            reminderRepository = reminderRepository,
            onClearAllData = { clearCallbackCalled = true }
        )

        val storeId = reminderRepository.insertStore(
            DbStore(
                name = "ドラッグストア ひかり",
                latitude = 35.68,
                longitude = 139.76,
                radiusMeters = 200f
            )
        )

        // updateStore
        repo.updateStore(
            UiStore(
                id = storeId.toString(),
                name = "ドラッグストア ひかり 本店",
                latitude = 35.68,
                longitude = 139.76,
                radiusMeters = 300,
                notifyEnabled = false
            )
        )
        var stores = repo.getStoresStream().first()
        assertEquals("ドラッグストア ひかり 本店", stores[0].name)
        assertEquals(300, stores[0].radiusMeters)
        assertFalse(stores[0].notifyEnabled)

        // deleteStore
        repo.deleteStore(storeId.toString())
        stores = repo.getStoresStream().first()
        assertTrue(stores.isEmpty())

        // clearAllData: 店舗・購入履歴・商品・レシートを全削除
        reminderRepository.insertStore(DbStore(name = "テスト店", latitude = 35.0, longitude = 139.0))
        val prod = reminderRepository.getOrCreateProduct("テスト品")
        reminderRepository.recordManualPurchase(prod.id, fixedToday.toEpochDay())

        repo.clearAllData()

        assertTrue(reminderRepository.getAllStores().isEmpty())
        assertTrue(reminderRepository.getAllPurchases().isEmpty())
        assertTrue(reminderRepository.getAllProducts().isEmpty())
        assertTrue(clearCallbackCalled)
    }

    // =========================================================================
    // RealNotificationSettingsRepository Tests
    // =========================================================================

    @Test
    fun `NotificationSettingsRepo - in-memory default and update`() = runTest {
        val repo = RealNotificationSettingsRepository()

        // 既定値: 8:00〜21:00
        val defaultRange = repo.getNotificationTimeRangeStream().first()
        assertEquals(8, defaultRange.startHour)
        assertEquals(0, defaultRange.startMinute)
        assertEquals(21, defaultRange.endHour)
        assertEquals(0, defaultRange.endMinute)

        // 更新
        val newRange = NotificationTimeRange(startHour = 9, startMinute = 30, endHour = 20, endMinute = 45)
        repo.setNotificationTimeRange(newRange)

        val updatedRange = repo.getNotificationTimeRangeStream().first()
        assertEquals(newRange, updatedRange)
    }

    // =========================================================================
    // Shared DataUpdateTrigger Integration Test
    // =========================================================================

    @Test
    fun `Shared DataUpdateTrigger synchronizes shopping list when purchase repository adds purchase`() = runTest {
        val sharedTrigger = DataUpdateTrigger()

        val shoppingRepo = RealShoppingListRepository(
            reminderRepository = reminderRepository,
            todayProvider = todayProvider,
            trigger = sharedTrigger
        )
        val purchaseRepo = RealPurchaseRepository(
            reminderRepository = reminderRepository,
            trigger = sharedTrigger
        )

        val soap = reminderRepository.getOrCreateProduct("食器用洗剤", "日用品")
        // 最初は購入履歴なし
        val initialItems = shoppingRepo.getShoppingItemsStream().first()
        assertEquals(1, initialItems.size)

        // purchaseRepo 経由で購入を追加
        purchaseRepo.insertPurchase(
            PurchaseRecord(
                id = "0",
                productId = soap.id.toString(),
                productName = soap.name,
                purchasedOn = fixedToday,
                quantity = 1,
                price = 198,
                source = UiPurchaseSource.MANUAL
            )
        )

        // shoppingRepo の Stream が共有トリガーにより更新され、残り日数が 30 日になっていること
        val updatedItems = shoppingRepo.getShoppingItemsStream().first()
        assertEquals(30, updatedItems[0].product.remainingDays)
    }
}
