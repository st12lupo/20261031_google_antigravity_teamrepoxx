package domain.model

enum class PredictionStatus {
    OUT_OF_STOCK, // 0日以下 (切れています)
    RUNNING_OUT,  // 余裕日数以内 (なくなりそう)
    SUFFICIENT,   // 余裕あり
    NO_DATA       // 購入履歴なし
}

data class ProductPrediction(
    val product: Product,
    val totalPurchases: Int,
    val lastPurchasedOnEpochDay: Long?,
    val cycleDaysPerUnit: Double,
    val isEstimatedCycle: Boolean,
    val predictedOutOfStockOnEpochDay: Long,
    val remainingDays: Long,
    val isRunningOut: Boolean,
    val status: PredictionStatus
)
