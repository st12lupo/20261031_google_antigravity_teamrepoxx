package com.example.receiptreminder.domain.repository

import com.example.receiptreminder.domain.model.Product
import com.example.receiptreminder.domain.model.PurchaseRecord
import kotlinx.coroutines.flow.Flow

/**
 * 商品および購入履歴の閲覧・管理リポジトリ
 */
interface ProductRepository {
    /** 全商品一覧のストリーム */
    fun getProductsStream(): Flow<List<Product>>

    /** 単一商品の詳細取得 */
    suspend fun getProduct(id: String): Product?

    /** 特定商品の購入履歴一覧ストリーム */
    fun getProductPurchasesStream(productId: String): Flow<List<PurchaseRecord>>

    /** 予測対象のオン/オフ切り替え */
    suspend fun updateTrackedStatus(productId: String, isTracked: Boolean)

    /** 購入履歴の編集（Q-1） */
    suspend fun updatePurchase(purchase: PurchaseRecord)

    /** 購入履歴の削除（Q-1） */
    suspend fun deletePurchase(purchaseId: String)
}
