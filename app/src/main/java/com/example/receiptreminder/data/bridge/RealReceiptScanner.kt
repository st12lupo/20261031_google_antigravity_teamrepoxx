package com.example.receiptreminder.data.bridge

import com.example.receiptreminder.domain.model.ReceiptCandidate
import com.example.receiptreminder.domain.model.ReceiptItemCandidate
import com.example.receiptreminder.domain.repository.ReceiptScanner as UiReceiptScanner
import domain.receipt.ProductNormalizer
import domain.receipt.ReceiptParser
import domain.receipt.ReceiptScanner as OcrReceiptScanner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import receipt.normalize.DefaultProductNormalizer
import receipt.parser.DefaultReceiptParser
import java.time.LocalDate

/**
 * 画像の OCR テキスト取得・パース・名寄せを行い、
 * UI用 ReceiptCandidate を生成するブリッジ実装クラス。
 */
class RealReceiptScanner(
    private val ocrScanner: OcrReceiptScanner? = null,
    private val parser: ReceiptParser = DefaultReceiptParser(),
    private val normalizer: ProductNormalizer = DefaultProductNormalizer(),
    private val reviewService: RealReceiptReviewService? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : UiReceiptScanner {

    /**
     * 複数の画像を 1 枚のレシートのページとして順に読み取る(R-7)。
     * 利用者が選択した元の画像を消さないよう、確認待ちサービスには画像を渡さず、
     * 削除は呼び出し側が撮影画像にだけ deleteImages で行う。
     */
    override suspend fun scanReceipt(imageUris: List<String>): Result<ReceiptCandidate> =
        recognize(imageUris, pendingImageUri = null)

    override fun deleteImages(imageUris: List<String>) {
        imageUris.forEach { ocrScanner?.deleteImage(it) }
    }

    /**
     * 指定した画像URIから読み取り、保存または破棄のときに確認待ちサービスが削除するよう登録する
     */
    suspend fun scanImage(imageUri: String): Result<ReceiptCandidate> =
        recognize(listOf(imageUri), pendingImageUri = imageUri)

    /**
     * 指定したOCR行文字列リストから直接レシート候補を生成する
     */
    suspend fun scanLines(lines: List<String>): Result<ReceiptCandidate> =
        guarded { createCandidate(lines, pendingImageUri = null) }

    private suspend fun recognize(imageUris: List<String>, pendingImageUri: String?): Result<ReceiptCandidate> =
        guarded {
            val scanner = ocrScanner
                ?: throw IllegalStateException("OCRスキャナーが設定されていません")
            val lines = withContext(ioDispatcher) { imageUris.flatMap { scanner.recognizeText(it) } }
            createCandidate(lines, pendingImageUri)
        }

    private suspend fun guarded(block: suspend () -> ReceiptCandidate): Result<ReceiptCandidate> =
        try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }

    private suspend fun createCandidate(ocrLines: List<String>, pendingImageUri: String?): ReceiptCandidate {
        val parsed = parser.parse(ocrLines)
        if (parsed.items.isEmpty()) throw IllegalStateException("商品を読み取れませんでした")
        val purchasedOn = LocalDate.ofEpochDay(parsed.purchasedOnEpochDay)

        // 店名・日付・合計金額で重複判定を事前チェック
        val isDuplicate = reviewService?.checkDuplicate(
            storeName = parsed.storeName,
            dateString = purchasedOn.toString(),
            totalAmount = parsed.totalAmount
        ) ?: false

        val items = parsed.items.mapIndexed { index, item ->
            val normResult = normalizer.normalize(item.rawName)
            ReceiptItemCandidate(
                id = "item_${System.currentTimeMillis()}_$index",
                rawOcrText = item.rawName,
                normalizedName = normResult.normalizedName,
                quantity = item.quantity.toInt().coerceAtLeast(1),
                price = item.price,
                isTracked = normResult.isMatchedInDictionary,
                mergedNotice = null
            )
        }

        val candidate = ReceiptCandidate(
            id = "receipt_${System.currentTimeMillis()}",
            storeName = parsed.storeName,
            purchasedOn = purchasedOn,
            totalAmount = parsed.totalAmount,
            isDuplicateWarning = isDuplicate,
            items = items
        )

        // スキャンされた候補を確認待ちサービスに登録
        reviewService?.setPendingReceipt(candidate, pendingImageUri)

        return candidate
    }
}
