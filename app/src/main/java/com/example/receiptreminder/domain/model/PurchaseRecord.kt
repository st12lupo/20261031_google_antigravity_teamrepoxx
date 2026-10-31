package com.example.receiptreminder.domain.model

import java.time.LocalDate

/**
 * 購入経路
 */
enum class PurchaseSource {
    /** レシート読み取り */
    RECEIPT,

    /** 手動入力 */
    MANUAL
}

/**
 * 購入履歴レコード
 */
data class PurchaseRecord(
    val id: String,
    val productId: String,
    val productName: String,
    val purchasedOn: LocalDate,
    val quantity: Int = 1,
    val price: Int? = null,
    val source: PurchaseSource = PurchaseSource.RECEIPT,
    val storeName: String? = null,
    val receiptId: String? = null
)
