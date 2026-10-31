package com.example.receiptreminder.domain.repository

import com.example.receiptreminder.domain.model.ReceiptCandidate

/**
 * レシートスキャン（撮影・OCR・解析）インターフェース
 */
interface ReceiptScanner {
    /** 撮影または選択した画像(複数ページ可)を読み取り、解析候補を返す */
    suspend fun scanReceipt(imageUris: List<String>): Result<ReceiptCandidate>

    /** 撮影で作られた画像を削除する(R-6)。利用者が選択した元の画像には使わない */
    fun deleteImages(imageUris: List<String>)
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

    /**
     * 確認画面で商品名を直したときに、予測の対象にするかの目安を返す(R-4, P-4)。
     * 判断できない実装は null を返し、画面は現在の指定を変えない。
     */
    suspend fun suggestTracked(name: String): Boolean? = null
}
