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
import domain.model.Product
import domain.model.ProductAlias
import domain.model.Purchase
import domain.model.PurchaseSource
import domain.model.Receipt
import domain.model.ReceiptItemData
import domain.model.Store
import domain.repository.ReminderRepository
import kotlin.math.abs

class DefaultReminderRepository(
    private val productDao: ProductDao,
    private val purchaseDao: PurchaseDao,
    private val receiptDao: ReceiptDao,
    private val storeDao: StoreDao,
    private val todayProvider: () -> java.time.LocalDate = { java.time.LocalDate.now() },
    /** レシートと購入の保存を 1 つのトランザクションにする(L-4)。本番は Room の withTransaction */
    private val transactionRunner: TransactionRunner = TransactionRunner.None
) : ReminderRepository {

    // === 商品 ===

    override suspend fun getAllProducts(): List<Product> {
        return productDao.getAllProducts().map { it.toDomain() }
    }

    override suspend fun getProductById(id: Long): Product? {
        return productDao.getProductById(id)?.toDomain()
    }

    override suspend fun getOrCreateProduct(name: String, category: String): Product {
        val existing = productDao.findProductByName(name)
        if (existing != null) {
            return existing.toDomain()
        }
        val newEntity = ProductEntity(
            name = name,
            category = category,
            isTracked = true
        )
        val id = productDao.insertProduct(newEntity)
        return newEntity.copy(id = id).toDomain()
    }

    override suspend fun setTracked(productId: Long, isTracked: Boolean) {
        if (isTracked) {
            productDao.updateTrackedAndStopped(productId, isTracked = true, stoppedByUser = false)
        } else {
            productDao.updateTracked(productId, false)
        }
    }

    override suspend fun stopTracking(productId: Long) {
        productDao.updateTrackedAndStopped(productId, isTracked = false, stoppedByUser = true)
    }

    override suspend fun snoozeProduct(productId: Long, extraDays: Long) {
        val product = productDao.getProductById(productId) ?: return
        val today = todayProvider().toEpochDay()
        val baseDay = maxOf(product.snoozedUntilEpochDay ?: today, today)
        productDao.updateSnooze(productId, baseDay + extraDays)
    }

    override suspend fun snoozeProductUntil(productId: Long, untilEpochDay: Long) {
        productDao.getProductById(productId) ?: return
        productDao.updateSnooze(productId, untilEpochDay)
    }

    override suspend fun deleteProduct(id: Long) {
        productDao.deleteProduct(id)
    }

    // === 商品の別名(P-2) ===

    override suspend fun getAllAliases(): List<ProductAlias> {
        return productDao.getAllAliases().map { it.toDomain() }
    }

    override suspend fun saveAlias(rawText: String, productId: Long) {
        val existing = productDao.findAlias(rawText)
        productDao.insertAlias(
            ProductAliasEntity(id = existing?.id ?: 0, rawText = rawText, productId = productId)
        )
    }

    override suspend fun deleteAlias(rawText: String) {
        productDao.deleteAlias(rawText)
    }

    override suspend fun deleteAllAliases() {
        productDao.deleteAllAliases()
    }

    // === 購入履歴 ===

    override suspend fun getAllPurchases(): List<Purchase> {
        return purchaseDao.getAllPurchases().map { it.toDomain() }
    }

    override suspend fun getPurchasesForProduct(productId: Long): List<Purchase> {
        return purchaseDao.getPurchasesForProduct(productId).map { it.toDomain() }
    }

    override suspend fun recordManualPurchase(
        productId: Long,
        purchasedOnEpochDay: Long,
        quantity: Double,
        price: Int,
        source: PurchaseSource
    ): Long {
        val entity = PurchaseEntity(
            productId = productId,
            purchasedOnEpochDay = purchasedOnEpochDay,
            quantity = quantity,
            price = price,
            source = source.name
        )
        // 購入したらスヌーズ(まだある)を解除する。次の切れる日は購入日と周期で決まる
        productDao.updateSnooze(productId, null)
        return purchaseDao.insertPurchase(entity)
    }

    override suspend fun updatePurchase(purchase: Purchase) {
        purchaseDao.updatePurchase(PurchaseEntity.fromDomain(purchase))
    }

    override suspend fun deletePurchase(purchaseId: Long) {
        purchaseDao.deletePurchase(purchaseId)
    }

    override suspend fun deleteAllPurchases() {
        purchaseDao.deleteAllPurchases()
    }

    // === レシート ===

    override suspend fun getAllReceipts(): List<Receipt> {
        return receiptDao.getAllReceipts().map { it.toDomain() }
    }

    override suspend fun findDuplicateReceipt(
        storeName: String,
        purchasedOnEpochDay: Long,
        totalAmount: Int
    ): Receipt? {
        return receiptDao.findDuplicateReceipt(storeName, purchasedOnEpochDay, totalAmount)?.toDomain()
    }

    override suspend fun saveReceiptAndPurchases(
        storeName: String,
        purchasedOnEpochDay: Long,
        totalAmount: Int,
        items: List<ReceiptItemData>
    ): Long = transactionRunner.run {
        saveReceiptAndPurchasesInternal(storeName, purchasedOnEpochDay, totalAmount, items)
    }

    private suspend fun saveReceiptAndPurchasesInternal(
        storeName: String,
        purchasedOnEpochDay: Long,
        totalAmount: Int,
        items: List<ReceiptItemData>
    ): Long {
        val receiptEntity = ReceiptEntity(
            storeName = storeName,
            purchasedOnEpochDay = purchasedOnEpochDay,
            totalAmount = totalAmount
        )
        val receiptId = receiptDao.insertReceipt(receiptEntity)

        for (item in items) {
            // 予測対象外の商品も購入履歴には残す(P-2, P-4)。新しい商品は確認画面の指定で作る
            val existingProduct = productDao.findProductByName(item.normalizedName)
            val product = if (existingProduct != null) {
                existingProduct.toDomain()
            } else {
                val entity = ProductEntity(
                    name = item.normalizedName,
                    category = item.category,
                    isTracked = item.isTracked
                )
                entity.copy(id = productDao.insertProduct(entity)).toDomain()
            }

            productDao.updateSnooze(product.id, null)

            // F-6: 「もう買わない」で予測対象から外した商品をレシートで購入したら自動で戻す(印も消す)。
            // 確認画面で予測対象を切り替えたときは、その指定を商品に反映し、印は消す(P-4)
            if (product.isTracked != item.isTracked || product.stoppedByUser) {
                productDao.updateTrackedAndStopped(product.id, item.isTracked, stoppedByUser = false)
            }

            // B-3: 「購入済み」または手動追加から 3 日以内に同じ商品をレシートで取り込んだら
            // 1 件にまとめ、レシートの日付と数量を優先する
            val minDay = purchasedOnEpochDay - 3
            val maxDay = purchasedOnEpochDay + 3
            val candidates = purchaseDao.getRecentPurchasesForProduct(product.id, minDay, maxDay)
                .filter { it.source != PurchaseSource.RECEIPT.name }

            val nearestManual = candidates.minByOrNull {
                abs(it.purchasedOnEpochDay - purchasedOnEpochDay)
            }

            if (nearestManual != null) {
                // 既存の購入済み/手動レコードを更新し、レシート情報で統合
                val consolidated = nearestManual.copy(
                    receiptId = receiptId,
                    purchasedOnEpochDay = purchasedOnEpochDay,
                    quantity = item.quantity,
                    price = item.price,
                    source = PurchaseSource.RECEIPT.name
                )
                purchaseDao.updatePurchase(consolidated)
            } else {
                // 統合対象がない場合は新規購入として登録
                val newPurchase = PurchaseEntity(
                    receiptId = receiptId,
                    productId = product.id,
                    purchasedOnEpochDay = purchasedOnEpochDay,
                    quantity = item.quantity,
                    price = item.price,
                    source = PurchaseSource.RECEIPT.name
                )
                purchaseDao.insertPurchase(newPurchase)
            }
        }

        return receiptId
    }

    override suspend fun deleteReceipt(receiptId: Long) {
        purchaseDao.deletePurchasesByReceiptId(receiptId)
        receiptDao.deleteReceipt(receiptId)
    }

    // === 店舗 ===

    override suspend fun getAllStores(): List<Store> {
        return storeDao.getAllStores().map { it.toDomain() }
    }

    override suspend fun getActiveStores(): List<Store> {
        return storeDao.getActiveStores().map { it.toDomain() }
    }

    override suspend fun insertStore(store: Store): Long {
        return storeDao.insertStore(StoreEntity.fromDomain(store))
    }

    override suspend fun updateStore(store: Store) {
        storeDao.updateStore(StoreEntity.fromDomain(store))
    }

    override suspend fun deleteStore(storeId: Long) {
        storeDao.deleteStore(storeId)
    }

    override suspend fun updateLastNotified(storeId: Long, epochDay: Long) {
        storeDao.updateLastNotified(storeId, epochDay)
    }

    override suspend fun deleteAllStores() {
        storeDao.deleteAllStores()
    }
}
