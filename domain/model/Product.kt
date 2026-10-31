package domain.model

data class Product(
    val id: Long = 0,
    val name: String,
    val category: String = "日用品",
    val isTracked: Boolean = true,
    val snoozedUntilEpochDay: Long? = null
)

data class ProductAlias(
    val id: Long = 0,
    val rawText: String,
    val productId: Long
)
