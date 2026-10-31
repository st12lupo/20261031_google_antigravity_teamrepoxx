package com.example.receiptreminder.domain.repository

import com.example.receiptreminder.domain.model.ReceiptCandidate

/**
 * レシートスキャン（撮影・OCR・解析）インターフェース
 */
interface ReceiptScanner {
    /** レシートをスキャンして解析候補を返す */
    suspend fun scanReceipt(): Result<ReceiptCandidate>
}

/**
 * レシート確認・保存サービス
 */
interface ReceiptReviewService {
    /** 現在確認待ちのレシートデータを取得 */
    suspend fun getPendingReceipt(): ReceiptCandidate?

    /** 同じレシートの重複チェック（店名、日付、合計金額の一致判定） */
    suspend fun checkDuplicate(storeName: String, dateString: String, totalAmount: Int): Boolean

    /** レシートの確認・編集内容を保存（画像は即時破棄） */
    suspend fun saveReceipt(receipt: ReceiptCandidate): Result<Unit>

    /** レシート取り込みの破棄 */
    suspend fun discardReceipt()
}
