package com.example.receiptreminder.fake

import com.example.receiptreminder.domain.model.PurchaseRecord
import com.example.receiptreminder.domain.model.PurchaseSource
import com.example.receiptreminder.domain.model.ReceiptCandidate
import com.example.receiptreminder.domain.repository.ProductRepository
import com.example.receiptreminder.domain.repository.ReceiptReviewService
import com.example.receiptreminder.domain.repository.ShoppingListRepository
import kotlinx.coroutines.flow.MutableStateFlow

class FakeReceiptReviewService(
    private val shoppingListRepository: ShoppingListRepository? = null,
    private val productRepository: ProductRepository? = null,
    initialPending: ReceiptCandidate? = SampleData.sampleReceiptCandidate()
) : ReceiptReviewService {

    private val _pendingReceipt = MutableStateFlow(initialPending)

    override suspend fun getPendingReceipt(): ReceiptCandidate? {
        return _pendingReceipt.value
    }

    override suspend fun checkDuplicate(storeName: String, dateString: String, totalAmount: Int): Boolean {
        // 店名、日付、合計金額が一致するレシートの警告（R-5）
        val current = _pendingReceipt.value ?: return false
        return current.storeName == storeName &&
                current.purchasedOn.toString() == dateString &&
                current.totalAmount == totalAmount
    }

    override suspend fun saveReceipt(receipt: ReceiptCandidate): Result<Unit> {
        // 各商品を買い物リストや履歴に反映し、レシート画像を破棄（シミュレート）
        receipt.items.forEach { item ->
            if (item.isTracked && shoppingListRepository != null) {
                shoppingListRepository.addManualPurchase(
                    productName = item.normalizedName,
                    purchasedOn = receipt.purchasedOn,
                    quantity = item.quantity,
                    price = item.price,
                    storeName = receipt.storeName
                )
            }
        }
        _pendingReceipt.value = null
        return Result.success(Unit)
    }

    override suspend fun discardReceipt() {
        _pendingReceipt.value = null
    }

    fun setPendingReceipt(receipt: ReceiptCandidate?) {
        _pendingReceipt.value = receipt
    }
}
