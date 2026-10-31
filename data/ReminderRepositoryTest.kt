package data

import data.dao.ProductDao
import data.dao.PurchaseDao
import data.dao.ReceiptDao
import data.dao.StoreDao
import data.entity.ProductAliasEntity
import data.entity.ProductEntity
import data.entity.PurchaseEntity
import data.entity.ReceiptEntity
import data.entity.StoreEntity
import domain.model.PurchaseSource
import domain.model.ReceiptItemData
import domain.model.Store
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ReminderRepositoryTest {

    private lateinit var productDao: FakeProductDao
    private lateinit var purchaseDao: FakePurchaseDao
    private lateinit var receiptDao: FakeReceiptDao
    private lateinit var storeDao: FakeStoreDao
    private lateinit var repository: DefaultReminderRepository

    @Before
    fun setup() {
        productDao = FakeProductDao()
        purchaseDao = FakePurchaseDao()
        receiptDao = FakeReceiptDao()
        storeDao = FakeStoreDao()
        repository = DefaultReminderRepository(productDao, purchaseDao, receiptDao, storeDao)
    }

    @Test
    fun `saveReceiptAndPurchases consolidates manual purchase within 3 days (B-3)`() = runTest {
        // 1. 商品作成
        val product = repository.getOrCreateProduct("牛乳", "定番食品")

        // 2. 買い物リストで「購入済み」または手動追加 (day 100, 数量 1.0, 価格 0)
        val manualId = repository.recordManualPurchase(
            productId = product.id,
            purchasedOnEpochDay = 100L,
            quantity = 1.0,
            price = 0,
            source = PurchaseSource.SHOPPING_LIST
        )

        // 購入履歴が 1 件あることを確認
        val purchasesBefore = repository.getPurchasesForProduct(product.id)
        assertEquals(1, purchasesBefore.size)
        assertEquals(100L, purchasesBefore[0].purchasedOnEpochDay)
        assertEquals(1.0, purchasesBefore[0].quantity, 0.0)

        // 3. 2日後 (day 102、3日以内) にレシートで同商品を取り込み (数量 2.0, 価格 450)
        val receiptId = repository.saveReceiptAndPurchases(
            storeName = "架空スーパー",
            purchasedOnEpochDay = 102L,
            totalAmount = 450,
            items = listOf(
                ReceiptItemData(
                    rawName = "ｷﾞｭｳﾆｭｳ",
                    normalizedName = "牛乳",
                    category = "定番食品",
                    quantity = 2.0,
                    price = 450
                )
            )
        )

        // 4. 検証: 購入履歴は二重計上されず 1 件のまま統合され、レシートの日付(102)と数量(2.0)が優先される
        val purchasesAfter = repository.getPurchasesForProduct(product.id)
        assertEquals(1, purchasesAfter.size)
        val consolidated = purchasesAfter[0]
        assertEquals(manualId, consolidated.id)
        assertEquals(receiptId, consolidated.receiptId)
        assertEquals(102L, consolidated.purchasedOnEpochDay)
        assertEquals(2.0, consolidated.quantity, 0.0)
        assertEquals(450, consolidated.price)
        assertEquals(PurchaseSource.RECEIPT, consolidated.source)
    }

    @Test
    fun `saveReceiptAndPurchases does not consolidate manual purchase older than 3 days`() = runTest {
        val product = repository.getOrCreateProduct("牛乳", "定番食品")

        // 5日前の手動購入 (day 95)
        repository.recordManualPurchase(
            productId = product.id,
            purchasedOnEpochDay = 95L,
            quantity = 1.0,
            price = 200,
            source = PurchaseSource.MANUAL
        )

        // day 100 にレシート取込 (4日以上離れているため統合されない)
        repository.saveReceiptAndPurchases(
            storeName = "架空スーパー",
            purchasedOnEpochDay = 100L,
            totalAmount = 230,
            items = listOf(
                ReceiptItemData(
                    rawName = "ｷﾞｭｳﾆｭｳ",
                    normalizedName = "牛乳",
                    category = "定番食品",
                    quantity = 1.0,
                    price = 230
                )
            )
        )

        // 検証: 2件の独立した購入履歴として残る
        val purchases = repository.getPurchasesForProduct(product.id)
        assertEquals(2, purchases.size)
    }

    @Test
    fun `saveReceiptAndPurchases automatically restores tracked status if previously untracked (F-6)`() = runTest {
        val product = repository.getOrCreateProduct("洗剤", "日用品")
        // ユーザーが「もう買わない」で除外した状態
        repository.setTracked(product.id, false)
        assertEquals(false, repository.getProductById(product.id)?.isTracked)

        // 次にレシートで購入
        repository.saveReceiptAndPurchases(
            storeName = "ドラッグストア架空",
            purchasedOnEpochDay = 100L,
            totalAmount = 300,
            items = listOf(
                ReceiptItemData(
                    rawName = "ｾﾝｻﾞｲ",
                    normalizedName = "洗剤",
                    category = "日用品",
                    quantity = 1.0,
                    price = 300
                )
            )
        )

        // 検証: 自動で isTracked = true に戻る
        val updatedProduct = repository.getProductById(product.id)
        assertNotNull(updatedProduct)
        assertTrue(updatedProduct!!.isTracked)
    }

    @Test
    fun `findDuplicateReceipt detects matching store date and total amount`() = runTest {
        repository.saveReceiptAndPurchases(
            storeName = "架空スーパー",
            purchasedOnEpochDay = 200L,
            totalAmount = 1500,
            items = emptyList()
        )

        // 完全一致するレシートが検出される
        val duplicate = repository.findDuplicateReceipt("架空スーパー", 200L, 1500)
        assertNotNull(duplicate)
        assertEquals("架空スーパー", duplicate?.storeName)

        // 金額または日付が違うものは検出されない
        val notDuplicate = repository.findDuplicateReceipt("架空スーパー", 200L, 1600)
        assertNull(notDuplicate)
    }

    @Test
    fun `store management operations work correctly`() = runTest {
        val store = Store(
            name = "架空薬局",
            latitude = 35.6812,
            longitude = 139.7671,
            radiusMeters = 200f,
            notifyEnabled = true
        )
        val id = repository.insertStore(store)
        assertTrue(id > 0)

        val stores = repository.getAllStores()
        assertEquals(1, stores.size)
        assertEquals("架空薬局", stores[0].name)

        // 通知日更新
        repository.updateLastNotified(id, 250L)
        val updated = repository.getAllStores()[0]
        assertEquals(250L, updated.lastNotifiedOnEpochDay)
    }
}

// === Fake DAO 実装 ===

class FakeProductDao : ProductDao {
    private val products = mutableMapOf<Long, ProductEntity>()
    private val aliases = mutableMapOf<Long, ProductAliasEntity>()
    private var nextId = 1L

    override suspend fun getAllProducts(): List<ProductEntity> = products.values.toList()

    override suspend fun getProductById(id: Long): ProductEntity? = products[id]

    override suspend fun findProductByName(name: String): ProductEntity? =
        products.values.firstOrNull { it.name == name }

    override suspend fun insertProduct(product: ProductEntity): Long {
        val id = if (product.id == 0L) nextId++ else product.id
        products[id] = product.copy(id = id)
        return id
    }

    override suspend fun updateProduct(product: ProductEntity) {
        products[product.id] = product
    }

    override suspend fun deleteProduct(id: Long) {
        products.remove(id)
    }

    override suspend fun updateTracked(id: Long, isTracked: Boolean) {
        products[id]?.let { products[id] = it.copy(isTracked = isTracked) }
    }

    override suspend fun updateSnooze(id: Long, snoozedUntilEpochDay: Long?) {
        products[id]?.let { products[id] = it.copy(snoozedUntilEpochDay = snoozedUntilEpochDay) }
    }

    override suspend fun getAllAliases(): List<ProductAliasEntity> = aliases.values.toList()

    override suspend fun findAlias(rawText: String): ProductAliasEntity? =
        aliases.values.firstOrNull { it.rawText == rawText }

    override suspend fun insertAlias(alias: ProductAliasEntity): Long {
        val id = if (alias.id == 0L) nextId++ else alias.id
        aliases[id] = alias.copy(id = id)
        return id
    }
}

class FakePurchaseDao : PurchaseDao {
    private val purchases = mutableMapOf<Long, PurchaseEntity>()
    private var nextId = 1L

    override suspend fun getAllPurchases(): List<PurchaseEntity> =
        purchases.values.sortedByDescending { it.purchasedOnEpochDay }

    override suspend fun getPurchasesForProduct(productId: Long): List<PurchaseEntity> =
        purchases.values.filter { it.productId == productId }.sortedBy { it.purchasedOnEpochDay }

    override suspend fun getRecentPurchasesForProduct(
        productId: Long,
        minEpochDay: Long,
        maxEpochDay: Long
    ): List<PurchaseEntity> = purchases.values.filter {
        it.productId == productId &&
                it.purchasedOnEpochDay in minEpochDay..maxEpochDay
    }.sortedByDescending { it.purchasedOnEpochDay }

    override suspend fun insertPurchase(purchase: PurchaseEntity): Long {
        val id = if (purchase.id == 0L) nextId++ else purchase.id
        purchases[id] = purchase.copy(id = id)
        return id
    }

    override suspend fun updatePurchase(purchase: PurchaseEntity) {
        purchases[purchase.id] = purchase
    }

    override suspend fun deletePurchase(id: Long) {
        purchases.remove(id)
    }

    override suspend fun deleteAllPurchases() {
        purchases.clear()
    }

    override suspend fun deletePurchasesByReceiptId(receiptId: Long) {
        purchases.values.filter { it.receiptId == receiptId }.map { it.id }.forEach { purchases.remove(it) }
    }
}

class FakeReceiptDao : ReceiptDao {
    private val receipts = mutableMapOf<Long, ReceiptEntity>()
    private var nextId = 1L

    override suspend fun getAllReceipts(): List<ReceiptEntity> = receipts.values.toList()

    override suspend fun findDuplicateReceipt(
        storeName: String,
        purchasedOnEpochDay: Long,
        totalAmount: Int
    ): ReceiptEntity? = receipts.values.firstOrNull {
        it.storeName == storeName &&
                it.purchasedOnEpochDay == purchasedOnEpochDay &&
                it.totalAmount == totalAmount
    }

    override suspend fun getReceiptById(id: Long): ReceiptEntity? = receipts[id]

    override suspend fun insertReceipt(receipt: ReceiptEntity): Long {
        val id = if (receipt.id == 0L) nextId++ else receipt.id
        receipts[id] = receipt.copy(id = id)
        return id
    }

    override suspend fun deleteReceipt(id: Long) {
        receipts.remove(id)
    }
}

class FakeStoreDao : StoreDao {
    private val stores = mutableMapOf<Long, StoreEntity>()
    private var nextId = 1L

    override suspend fun getAllStores(): List<StoreEntity> = stores.values.toList()

    override suspend fun getActiveStores(): List<StoreEntity> = stores.values.filter { it.notifyEnabled }

    override suspend fun getStoreById(id: Long): StoreEntity? = stores[id]

    override suspend fun insertStore(store: StoreEntity): Long {
        val id = if (store.id == 0L) nextId++ else store.id
        stores[id] = store.copy(id = id)
        return id
    }

    override suspend fun updateStore(store: StoreEntity) {
        stores[store.id] = store
    }

    override suspend fun deleteStore(id: Long) {
        stores.remove(id)
    }

    override suspend fun updateLastNotified(id: Long, epochDay: Long) {
        stores[id]?.let { stores[id] = it.copy(lastNotifiedOnEpochDay = epochDay) }
    }

    override suspend fun deleteAllStores() {
        stores.clear()
    }
}
