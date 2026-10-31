package domain.model

data class Product(
    val id: Long = 0,
    val name: String,
    val category: String = "日用品",
    val isTracked: Boolean = true,
    val snoozedUntilEpochDay: Long? = null,
    /** 「もう買わない」で外した印。詳細画面の OFF(P-4)では立てない。購入で対象に戻すと消す(F-6) */
    val stoppedByUser: Boolean = false
)

data class ProductAlias(
    val id: Long = 0,
    val rawText: String,
    val productId: Long
)
