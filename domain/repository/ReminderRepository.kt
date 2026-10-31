package domain.repository

import domain.model.Product
import domain.model.ProductAlias
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
    /** 詳細画面の切り替え(P-4)。ON にすると「もう買わない」の印を消す。OFF では印を立てない */
    suspend fun setTracked(productId: Long, isTracked: Boolean)
    /** 「もう買わない」(F-6): 予測対象から外し、印を立てる。次にレシートで購入すると戻る */
    suspend fun stopTracking(productId: Long)
    /** 今日(または現在のスヌーズ日)から extraDays 後ろへずらす。切れる日基準でずらすときは snoozeProductUntil を使う */
    suspend fun snoozeProduct(productId: Long, extraDays: Long)
    /** 「まだある」: 切れる日を絶対日付で指定する。購入を記録するとスヌーズは解除される */
    suspend fun snoozeProductUntil(productId: Long, untilEpochDay: Long) {
        throw UnsupportedOperationException()
    }
    suspend fun deleteProduct(id: Long)

    // === 商品の別名(P-2) ===
    /** 確認画面の修正から覚えた別名。既定の実装は何も覚えない */
    suspend fun getAllAliases(): List<ProductAlias> = emptyList()
    suspend fun saveAlias(rawText: String, productId: Long) {}
    /** 別名を 1 件取り消す。rawText は保存時と同じ照合キー */
    suspend fun deleteAlias(rawText: String) {}
    suspend fun deleteAllAliases() {}

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
