package domain.model

enum class PurchaseSource {
    RECEIPT,
    MANUAL,
    SHOPPING_LIST
}

data class Purchase(
    val id: Long = 0,
    val receiptId: Long? = null,
    val productId: Long,
    val purchasedOnEpochDay: Long,
    val quantity: Double = 1.0,
    val price: Int = 0,
    val source: PurchaseSource = PurchaseSource.RECEIPT
)
