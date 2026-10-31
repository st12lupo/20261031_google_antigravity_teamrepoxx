package data

import data.dao.ProductDao
import data.dao.PurchaseDao
import data.dao.ReceiptDao
import data.dao.StoreDao
import data.entity.ProductEntity
import data.entity.PurchaseEntity
import data.entity.ReceiptEntity
import data.entity.StoreEntity
import domain.model.Product
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
    private val storeDao: StoreDao
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
        productDao.updateTracked(productId, isTracked)
    }

    override suspend fun snoozeProduct(productId: Long, extraDays: Long) {
        val product = productDao.getProductById(productId) ?: return
        val baseDay = product.snoozedUntilEpochDay ?: java.time.LocalDate.now().toEpochDay()
        productDao.updateSnooze(productId, baseDay + extraDays)
    }

    override suspend fun deleteProduct(id: Long) {
        productDao.deleteProduct(id)
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
    ): Long {
        val receiptEntity = ReceiptEntity(
            storeName = storeName,
            purchasedOnEpochDay = purchasedOnEpochDay,
            totalAmount = totalAmount
        )
        val receiptId = receiptDao.insertReceipt(receiptEntity)

        for (item in items) {
            val product = getOrCreateProduct(item.normalizedName, item.category)

            // F-6: 「もう買わない」で予測対象から外した商品をレシートで購入したら自動で戻す
            if (!product.isTracked) {
                productDao.updateTracked(product.id, true)
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
