package domain.repository

import domain.model.Product
import domain.model.Purchase
import domain.model.PurchaseSource
import domain.model.Receipt
import domain.model.ReceiptItemData
import domain.model.Store

interface ReminderRepository {

    // === 商品 ===
    suspend fun getAllProducts(): List<Product>
    suspend fun getProductById(id: Long): Product?
    suspend fun getOrCreateProduct(name: String, category: String = "日用品"): Product
    suspend fun setTracked(productId: Long, isTracked: Boolean)
    suspend fun snoozeProduct(productId: Long, extraDays: Long)
    suspend fun deleteProduct(id: Long)

    // === 購入履歴 ===
    suspend fun getAllPurchases(): List<Purchase>
    suspend fun getPurchasesForProduct(productId: Long): List<Purchase>
    suspend fun recordManualPurchase(
        productId: Long,
        purchasedOnEpochDay: Long,
        quantity: Double = 1.0,
        price: Int = 0,
        source: PurchaseSource = PurchaseSource.MANUAL
    ): Long
    suspend fun updatePurchase(purchase: Purchase)
    suspend fun deletePurchase(purchaseId: Long)
    suspend fun deleteAllPurchases()

    // === レシート ===
    suspend fun getAllReceipts(): List<Receipt>
    suspend fun findDuplicateReceipt(
        storeName: String,
        purchasedOnEpochDay: Long,
        totalAmount: Int
    ): Receipt?
    suspend fun saveReceiptAndPurchases(
        storeName: String,
        purchasedOnEpochDay: Long,
        totalAmount: Int,
        items: List<ReceiptItemData>
    ): Long
    suspend fun deleteReceipt(receiptId: Long)

    // === 店舗 ===
    suspend fun getAllStores(): List<Store>
    suspend fun getActiveStores(): List<Store>
    suspend fun insertStore(store: Store): Long
    suspend fun updateStore(store: Store)
    suspend fun deleteStore(storeId: Long)
    suspend fun updateLastNotified(storeId: Long, epochDay: Long)
    suspend fun deleteAllStores()
}
