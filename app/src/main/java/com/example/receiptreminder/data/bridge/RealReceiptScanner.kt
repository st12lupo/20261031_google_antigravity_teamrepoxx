package com.example.receiptreminder.data.bridge

import com.example.receiptreminder.domain.model.ReceiptCandidate
import com.example.receiptreminder.domain.model.ReceiptItemCandidate
import com.example.receiptreminder.domain.repository.ReceiptScanner as UiReceiptScanner
import domain.receipt.ProductNormalizer
import domain.receipt.ReceiptParser
import domain.receipt.ReceiptScanner as OcrReceiptScanner
import receipt.normalize.DefaultProductNormalizer
import receipt.parser.DefaultReceiptParser
import java.time.LocalDate

/**
 * レシート撮影・OCRテキスト取得・パース・名寄せを行い、
 * UI用 ReceiptCandidate を生成するブリッジ実装クラス。
 */
class RealReceiptScanner(
    private val ocrScanner: OcrReceiptScanner? = null,
    private val parser: ReceiptParser = DefaultReceiptParser(),
    private val normalizer: ProductNormalizer = DefaultProductNormalizer(),
    private val reviewService: RealReceiptReviewService? = null,
    private var imageUriProvider: (suspend () -> String?)? = null,
    private var directLinesProvider: (suspend () -> List<String>?)? = null
) : UiReceiptScanner {

    /**
     * スキャン対象の画像URIプロバイダを設定する
     */
    fun setImageUriProvider(provider: suspend () -> String?) {
        this.imageUriProvider = provider
    }

    /**
     * テストやOCRテキスト直接指定用のプロバイダを設定する
     */
    fun setDirectLinesProvider(provider: suspend () -> List<String>?) {
        this.directLinesProvider = provider
    }

    /**
     * 指定した画像URIから直接レシートをスキャンする
     */
    suspend fun scanImage(imageUri: String): Result<ReceiptCandidate> {
        val prev = imageUriProvider
        imageUriProvider = { imageUri }
        return try {
            scanReceipt()
        } finally {
            imageUriProvider = prev
        }
    }

    /**
     * 指定したOCR行文字列リストから直接レシート候補を生成する
     */
    suspend fun scanLines(lines: List<String>): Result<ReceiptCandidate> {
        val prev = directLinesProvider
        directLinesProvider = { lines }
        return try {
            scanReceipt()
        } finally {
            directLinesProvider = prev
        }
    }

    override suspend fun scanReceipt(): Result<ReceiptCandidate> {
        return runCatching {
            var scannedImageUri: String? = null
            val ocrLines: List<String> = directLinesProvider?.invoke()
                ?: run {
                    val uri = imageUriProvider?.invoke()
                        ?: throw IllegalStateException("スキャン対象の画像URIが設定されていません")
                    scannedImageUri = uri
                    val scanner = ocrScanner
                        ?: throw IllegalStateException("OCRスキャナーが設定されていません")
                    scanner.recognizeText(uri)
                }

            val parsed = parser.parse(ocrLines)
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

            // スキャンされた候補と元画像URIを確認待ちサービスに登録
            reviewService?.setPendingReceipt(candidate, scannedImageUri)

            candidate
        }
    }
}
