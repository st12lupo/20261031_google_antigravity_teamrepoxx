package com.example.receiptreminder.domain.repository

import com.example.receiptreminder.domain.model.ShoppingItem
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * 買い物リストの操作リポジトリ
 */
interface ShoppingListRepository {
    /** 買い物リスト（なくなりそう順）の監視ストリーム */
    fun getShoppingItemsStream(): Flow<List<ShoppingItem>>

    /** 商品を購入済みにする（既定数量 1） */
    suspend fun markAsPurchased(productId: String, quantity: Int = 1)

    /** 「まだある」操作（切れる日を周期の2割ずらす） */
    suspend fun snooze(productId: String)

    /** 「もう買わない」操作（予測対象から除外） */
    suspend fun stopTracking(productId: String)

    /** レシートなしの購入を手動で追加 */
    suspend fun addManualPurchase(
        productName: String,
        purchasedOn: LocalDate,
        quantity: Int = 1,
        price: Int? = null,
        storeName: String? = null
    )
}
