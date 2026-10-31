package domain.model

data class Receipt(
    val id: Long = 0,
    val storeName: String,
    val purchasedOnEpochDay: Long,
    val totalAmount: Int,
    val createdAtMillis: Long = System.currentTimeMillis()
)

data class ParsedReceiptItem(
    val rawName: String,
    val quantity: Double = 1.0,
    val price: Int = 0
)

data class ParsedReceipt(
    val storeName: String,
    val purchasedOnEpochDay: Long,
    val totalAmount: Int,
    val items: List<ParsedReceiptItem>
)

data class ReceiptItemData(
    val rawName: String,
    val normalizedName: String,
    val category: String = "日用品",
    val quantity: Double = 1.0,
    val price: Int = 0
)
