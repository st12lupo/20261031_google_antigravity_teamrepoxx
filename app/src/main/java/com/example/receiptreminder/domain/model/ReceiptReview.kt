package com.example.receiptreminder.domain.model

import java.time.LocalDate

/**
 * レシート確認画面での商品行候補
 */
data class ReceiptItemCandidate(
    val id: String,
    val rawOcrText: String,
    val normalizedName: String,
    val quantity: Int = 1,
    val price: Int,
    val isTracked: Boolean = true,
    val mergedNotice: String? = null
)

/**
 * レシート読み取り確認データ
 */
data class ReceiptCandidate(
    val id: String,
    val storeName: String,
    val purchasedOn: LocalDate,
    val totalAmount: Int,
    val isDuplicateWarning: Boolean = false,
    val items: List<ReceiptItemCandidate> = emptyList()
)
