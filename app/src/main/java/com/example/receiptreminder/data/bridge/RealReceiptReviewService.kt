package com.example.receiptreminder.data.bridge

import com.example.receiptreminder.domain.model.ReceiptCandidate
import com.example.receiptreminder.domain.repository.ReceiptReviewService as UiReceiptReviewService
import domain.model.ReceiptItemData
import domain.receipt.ProductNormalizer
import domain.receipt.ReceiptScanner as OcrReceiptScanner
import domain.repository.ReminderRepository
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * レシート確認画面での重複チェック・保存・破棄、および画像削除（R-6）を管理するブリッジ実装。
 */
class RealReceiptReviewService(
    private val reminderRepository: ReminderRepository,
    private val ocrScanner: OcrReceiptScanner? = null,
    private val normalizer: ProductNormalizer? = null,
    initialPendingReceipt: ReceiptCandidate? = null,
    initialImageUri: String? = null,
    private val trigger: DataUpdateTrigger = DataUpdateTrigger()
) : UiReceiptReviewService {

    private val _pendingReceipt = MutableStateFlow<ReceiptCandidate?>(initialPendingReceipt)
    private var pendingImageUri: String? = initialImageUri

    /**
     * スキャナーまたは外部から確認待ちレシートと元画像URIをセットする。
     */
    fun setPendingReceipt(receipt: ReceiptCandidate?, imageUri: String? = null) {
        _pendingReceipt.value = receipt
        pendingImageUri = imageUri
    }

    /**
     * 現在保持している元画像URIを取得（テスト等確認用）
     */
    fun getPendingImageUri(): String? = pendingImageUri

    override suspend fun getPendingReceipt(): ReceiptCandidate? {
        return _pendingReceipt.value
    }

    override suspend fun checkDuplicate(storeName: String, dateString: String, totalAmount: Int): Boolean {
        val date = parseDate(dateString) ?: return false
        val duplicate = reminderRepository.findDuplicateReceipt(
            storeName = storeName.trim(),
            purchasedOnEpochDay = date.toEpochDay(),
            totalAmount = totalAmount
        )
        return duplicate != null
    }

    override suspend fun saveReceipt(receipt: ReceiptCandidate): Result<Unit> {
        return runCatching {
            val epochDay = receipt.purchasedOn.toEpochDay()

            // 予測対象（isTracked == true）の商品のみをリポジトリへ登録
            val trackedItems = receipt.items.filter { it.isTracked }

            val itemDataList = trackedItems.map { item ->
                // P-2: ユーザーが確認画面で修正した場合は別名として辞書に登録
                if (item.rawOcrText.isNotBlank() && item.rawOcrText != item.normalizedName) {
                    normalizer?.let { n ->
                        if (n is receipt.normalize.DefaultProductNormalizer) {
                            n.registerAlias(item.rawOcrText, item.normalizedName)
                        }
                    }
                }

                val category = normalizer?.normalize(item.normalizedName)?.category ?: "日用品"
                ReceiptItemData(
                    rawName = item.rawOcrText.ifBlank { item.normalizedName },
                    normalizedName = item.normalizedName,
                    category = category,
                    quantity = item.quantity.toDouble().coerceAtLeast(1.0),
                    price = item.price
                )
            }

            reminderRepository.saveReceiptAndPurchases(
                storeName = receipt.storeName.trim(),
                purchasedOnEpochDay = epochDay,
                totalAmount = receipt.totalAmount,
                items = itemDataList
            )
            trigger.notifyChanged()

            // R-6: 保存後にレシート画像ファイルを削除し、端末内に残さない
            pendingImageUri?.let { uri ->
                ocrScanner?.deleteImage(uri)
            }

            _pendingReceipt.value = null
            pendingImageUri = null
        }
    }

    override suspend fun discardReceipt() {
        // 取り込み中止時も画像を削除し、端末内に残さない (R-6)
        pendingImageUri?.let { uri ->
            ocrScanner?.deleteImage(uri)
        }
        _pendingReceipt.value = null
        pendingImageUri = null
    }

    private fun parseDate(dateString: String): LocalDate? {
        val trimmed = dateString.trim()
        return runCatching { LocalDate.parse(trimmed) }.getOrNull()
            ?: runCatching { LocalDate.parse(trimmed, DateTimeFormatter.ofPattern("uuuu/M/d")) }.getOrNull()
            ?: runCatching { LocalDate.parse(trimmed, DateTimeFormatter.ofPattern("uuuu-M-d")) }.getOrNull()
            ?: runCatching { LocalDate.parse(trimmed, DateTimeFormatter.ofPattern("uuuu/MM/dd")) }.getOrNull()
    }
}
