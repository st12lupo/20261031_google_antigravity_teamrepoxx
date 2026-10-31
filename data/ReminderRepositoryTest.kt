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
    fun `saveReceiptAndPurchases keeps untracked items in history as untracked products (P-2, P-4)`() = runTest {
        repository.saveReceiptAndPurchases(
            storeName = "テスト店",
            purchasedOnEpochDay = 100L,
            totalAmount = 700,
            items = listOf(
                ReceiptItemData("ｾﾞﾘｰ", "ゼリー飲料", "日用品", 1.0, 200, isTracked = false),
                ReceiptItemData("ｷﾞｭｳﾆｭｳ", "牛乳", "定番食品", 2.0, 500, isTracked = true)
            )
        )

        val jelly = repository.getAllProducts().first { it.name == "ゼリー飲料" }
        assertEquals(false, jelly.isTracked)
        val jellyPurchases = repository.getPurchasesForProduct(jelly.id)
        assertEquals(1, jellyPurchases.size)
        assertEquals(200, jellyPurchases[0].price)
        assertEquals(PurchaseSource.RECEIPT, jellyPurchases[0].source)

        val milk = repository.getAllProducts().first { it.name == "牛乳" }
        assertTrue(milk.isTracked)
    }

    @Test
    fun `saveReceiptAndPurchases applies the review toggle to an existing product (P-4)`() = runTest {
        val milk = repository.getOrCreateProduct("牛乳", "定番食品")
        assertTrue(milk.isTracked)

        repository.saveReceiptAndPurchases(
            storeName = "テスト店",
            purchasedOnEpochDay = 100L,
            totalAmount = 200,
            items = listOf(ReceiptItemData("牛乳", "牛乳", "定番食品", 1.0, 200, isTracked = false))
        )

        assertEquals(false, repository.getProductById(milk.id)?.isTracked)
        assertEquals(1, repository.getPurchasesForProduct(milk.id).size)
    }

    @Test
    fun `aliases are saved listed and deleted (P-2)`() = runTest {
        val milk = repository.getOrCreateProduct("牛乳", "定番食品")
        repository.saveAlias("ｷﾞｭｰﾆｭｰ", milk.id)
        repository.saveAlias("ｷﾞｭｰﾆｭｰ", milk.id) // 同じ文字列は重複させない

        val aliases = repository.getAllAliases()
        assertEquals(1, aliases.size)
        assertEquals("ｷﾞｭｰﾆｭｰ", aliases[0].rawText)
        assertEquals(milk.id, aliases[0].productId)

        repository.deleteAllAliases()
        assertTrue(repository.getAllAliases().isEmpty())
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

    /** 失敗したら保存前の状態に戻す、Room の withTransaction 相当のテスト用の実装 */
    private class RollbackRunner(
        private val dao: List<() -> () -> Unit>
    ) : TransactionRunner {
        var runs = 0
        var insideCount = 0
        override suspend fun <T> run(block: suspend () -> T): T {
            runs++
            val restores = dao.map { it() }
            insideCount++
            try {
                return block()
            } catch (e: Throwable) {
                restores.forEach { it() }
                throw e
            } finally {
                insideCount--
            }
        }
    }

    private fun rollbackRunner() = RollbackRunner(
        listOf(productDao::snapshot, purchaseDao::snapshot, receiptDao::snapshot)
    )

    private fun twoItems() = listOf(
        ReceiptItemData(rawName = "ｷﾞｭｳﾆｭｳ", normalizedName = "牛乳", category = "定番食品", quantity = 1.0, price = 200),
        ReceiptItemData(rawName = "ﾀﾏｺﾞ", normalizedName = "卵", category = "定番食品", quantity = 1.0, price = 250)
    )

    @Test
    fun `L-4 saveReceiptAndPurchases runs inside the transaction`() = runTest {
        val runner = rollbackRunner()
        repository = DefaultReminderRepository(
            productDao, purchaseDao, receiptDao, storeDao, transactionRunner = runner
        )

        repository.saveReceiptAndPurchases("架空スーパー", 100L, 450, twoItems())

        assertEquals(1, runner.runs)
        assertEquals(0, runner.insideCount)
        assertEquals(1, repository.getAllReceipts().size)
        assertEquals(2, repository.getAllPurchases().size)
    }

    @Test
    fun `L-4 a failure halfway leaves no receipt product or purchase behind`() = runTest {
        val runner = rollbackRunner()
        repository = DefaultReminderRepository(
            productDao, purchaseDao, receiptDao, storeDao, transactionRunner = runner
        )
        purchaseDao.failAfterInserts = 1 // 1 件目の購入は入り、2 件目で失敗する

        val failure = runCatching {
            repository.saveReceiptAndPurchases("架空スーパー", 100L, 450, twoItems())
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertTrue(repository.getAllReceipts().isEmpty())
        assertTrue(repository.getAllPurchases().isEmpty())
        assertTrue(repository.getAllProducts().isEmpty())
    }

    @Test
    fun `L-4 without a transaction the same failure would leave a partial save (the test setup is sound)`() = runTest {
        purchaseDao.failAfterInserts = 1

        runCatching { repository.saveReceiptAndPurchases("架空スーパー", 100L, 450, twoItems()) }

        assertEquals(1, repository.getAllReceipts().size)
        assertEquals(1, repository.getAllPurchases().size)
    }

    @Test
    fun `L-4 saving an alias stays outside the transaction`() = runTest {
        val runner = rollbackRunner()
        repository = DefaultReminderRepository(
            productDao, purchaseDao, receiptDao, storeDao, transactionRunner = runner
        )
        val product = repository.getOrCreateProduct("牛乳", "定番食品")

        repository.saveAlias("ｷﾞｭｳﾆｭｳ", product.id)

        assertEquals(0, runner.runs)
        assertEquals(1, repository.getAllAliases().size)
    }

    // ---- F-6 / P-4: 「もう買わない」の印 ----

    @Test
    fun `stopTracking turns tracking off and sets the stopped mark, setTracked OFF does not`() = runTest {
        val stopped = repository.getOrCreateProduct("ヨガマット")
        val off = repository.getOrCreateProduct("牛乳", "定番食品")

        repository.stopTracking(stopped.id)
        repository.setTracked(off.id, false)

        assertEquals(false, repository.getProductById(stopped.id)!!.isTracked)
        assertTrue(repository.getProductById(stopped.id)!!.stoppedByUser)
        assertEquals(false, repository.getProductById(off.id)!!.isTracked)
        assertEquals(false, repository.getProductById(off.id)!!.stoppedByUser)
    }

    @Test
    fun `setTracked ON clears the stopped mark`() = runTest {
        val product = repository.getOrCreateProduct("ヨガマット")
        repository.stopTracking(product.id)

        repository.setTracked(product.id, true)

        assertEquals(true, repository.getProductById(product.id)!!.isTracked)
        assertEquals(false, repository.getProductById(product.id)!!.stoppedByUser)
    }

    @Test
    fun `saveReceiptAndPurchases returns a stopped product to tracking and clears the mark`() = runTest {
        val product = repository.getOrCreateProduct("ヨガマット")
        repository.stopTracking(product.id)

        repository.saveReceiptAndPurchases(
            "架空スーパー", 100L, 880,
            listOf(ReceiptItemData("ヨガマット", "ヨガマット", "日用品", 1.0, 880, isTracked = true))
        )

        val saved = repository.getProductById(product.id)!!
        assertEquals(true, saved.isTracked)
        assertEquals(false, saved.stoppedByUser)
    }

    @Test
    fun `saveReceiptAndPurchases with an explicit OFF keeps a stopped product off and clears the mark`() = runTest {
        val product = repository.getOrCreateProduct("ヨガマット")
        repository.stopTracking(product.id)

        repository.saveReceiptAndPurchases(
            "架空スーパー", 100L, 880,
            listOf(ReceiptItemData("ヨガマット", "ヨガマット", "日用品", 1.0, 880, isTracked = false))
        )

        val saved = repository.getProductById(product.id)!!
        assertEquals(false, saved.isTracked)
        assertEquals(false, saved.stoppedByUser)
        assertEquals(1, repository.getPurchasesForProduct(product.id).size)
    }

    @Test
    fun `saveReceiptAndPurchases never sets the mark for a new product saved as OFF`() = runTest {
        repository.saveReceiptAndPurchases(
            "架空スーパー", 100L, 880,
            listOf(ReceiptItemData("ヨガマット", "ヨガマット", "日用品", 1.0, 880, isTracked = false))
        )

        val saved = repository.getAllProducts().single()
        assertEquals(false, saved.isTracked)
        assertEquals(false, saved.stoppedByUser)
    }

    @Test
    fun `deleteAlias removes only the given alias`() = runTest {
        val product = repository.getOrCreateProduct("牛乳", "定番食品")
        repository.saveAlias("トクバイ", product.id)
        repository.saveAlias("ミルク", product.id)

        repository.deleteAlias("トクバイ")

        assertEquals(listOf("ミルク"), repository.getAllAliases().map { it.rawText })
    }
}

// === Fake DAO 実装 ===

class FakeProductDao : ProductDao {
    private val products = mutableMapOf<Long, ProductEntity>()
    private val aliases = mutableMapOf<Long, ProductAliasEntity>()
    private var nextId = 1L

    /** トランザクションの巻き戻しを再現するための保存と復元 */
    fun snapshot(): () -> Unit {
        val p = products.toMap(); val a = aliases.toMap(); val n = nextId
        return { products.clear(); products.putAll(p); aliases.clear(); aliases.putAll(a); nextId = n }
    }

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
        // 本番の外部キー(CASCADE)と同じく、商品を消したら別名も消える
        aliases.values.filter { it.productId == id }.map { it.id }.forEach { aliases.remove(it) }
    }

    override suspend fun updateTracked(id: Long, isTracked: Boolean) {
        products[id]?.let { products[id] = it.copy(isTracked = isTracked) }
    }

    override suspend fun updateTrackedAndStopped(id: Long, isTracked: Boolean, stoppedByUser: Boolean) {
        products[id]?.let { products[id] = it.copy(isTracked = isTracked, stoppedByUser = stoppedByUser) }
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

    override suspend fun deleteAlias(rawText: String) {
        aliases.values.filter { it.rawText == rawText }.map { it.id }.forEach { aliases.remove(it) }
    }

    override suspend fun deleteAllAliases() {
        aliases.clear()
    }
}

class FakePurchaseDao : PurchaseDao {
    private val purchases = mutableMapOf<Long, PurchaseEntity>()
    private var nextId = 1L

    /** 0 以上なら、その回数だけ insertPurchase が成功したあとの insertPurchase で失敗する */
    var failAfterInserts: Int = -1
    private var insertCount = 0

    fun snapshot(): () -> Unit {
        val p = purchases.toMap(); val n = nextId
        return { purchases.clear(); purchases.putAll(p); nextId = n }
    }

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
        if (failAfterInserts >= 0 && insertCount >= failAfterInserts) {
            throw IllegalStateException("insertPurchase failed")
        }
        insertCount++
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

    fun snapshot(): () -> Unit {
        val r = receipts.toMap(); val n = nextId
        return { receipts.clear(); receipts.putAll(r); nextId = n }
    }

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
