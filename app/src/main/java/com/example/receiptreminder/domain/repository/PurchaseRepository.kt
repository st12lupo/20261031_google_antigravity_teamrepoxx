package com.example.receiptreminder.domain.repository

import com.example.receiptreminder.domain.model.PurchaseRecord
import kotlinx.coroutines.flow.Flow

/**
 * 購入履歴全般のデータリポジトリ
 */
interface PurchaseRepository {
    /** 全購入履歴のストリーム */
    fun getAllPurchasesStream(): Flow<List<PurchaseRecord>>

    /** 購入レコードの追加 */
    suspend fun insertPurchase(purchase: PurchaseRecord)

    /** 購入レコードの更新 */
    suspend fun updatePurchase(purchase: PurchaseRecord)

    /** 購入レコードの削除 */
    suspend fun deletePurchase(purchaseId: String)
}
