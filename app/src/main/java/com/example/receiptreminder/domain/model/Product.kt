package com.example.receiptreminder.domain.model

import java.time.LocalDate

/**
 * 在庫・消耗品の状態
 */
enum class StockStatus {
    /** 期限超過・切れ（例: 1日過ぎています） */
    OUT_OF_STOCK_OR_OVERDUE,

    /** なくなりそう（例: あと2日） */
    RUNNING_OUT,

    /** まだ大丈夫（例: あと18日） */
    SUFFICIENT,

    /** 追跡対象外（もう買わない） */
    NOT_TRACKED
}

/**
 * 商品モデル
 */
data class Product(
    val id: String,
    val name: String,
    val category: String? = null,
    val isTracked: Boolean = true,
    val isEstimated: Boolean = false,
    val cycleDays: Double = 30.0,
    val predictedOutDate: LocalDate = LocalDate.now().plusDays(30),
    val becameLowDate: LocalDate? = null,
    val remainingDays: Int = 30,
    /**
     * 予測側(F-4)の「なくなりそう」判定。null のときは旧来の「残り 7 日以内」で判定する(Fake 用)。
     */
    val isRunningOut: Boolean? = null,
    /** 購入履歴があるか。false(履歴を全部削除した商品など)は予測の対象外として扱う */
    val hasPurchaseHistory: Boolean = true
) {
    val status: StockStatus
        get() {
            if (!isTracked || !hasPurchaseHistory) return StockStatus.NOT_TRACKED
            return when {
                remainingDays < 0 -> StockStatus.OUT_OF_STOCK_OR_OVERDUE
                isRunningOut ?: (remainingDays <= 7) -> StockStatus.RUNNING_OUT
                else -> StockStatus.SUFFICIENT
            }
        }
}

/**
 * 買い物リスト用アイテム（表示情報つき）
 */
data class ShoppingItem(
    val product: Product,
    val notice: String? = null
)
