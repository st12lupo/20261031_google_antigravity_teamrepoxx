package prediction

import domain.model.PredictionStatus
import domain.model.Product
import domain.model.ProductPrediction
import domain.model.Purchase
import domain.prediction.ConsumptionPredictor
import kotlin.math.roundToLong

class DefaultConsumptionPredictor(
    private val defaultCategoryCycleDays: Map<String, Double> = DEFAULT_CATEGORY_CYCLES
) : ConsumptionPredictor {

    companion object {
        const val DEFAULT_CYCLE_DAYS = 30.0
        val DEFAULT_CATEGORY_CYCLES = mapOf(
            "日用品" to 30.0,
            "定番食品" to 14.0,
            "飲料・乳製品" to 7.0,
            "調味料" to 60.0,
            "米" to 30.0
        )
    }

    override fun predict(
        product: Product,
        purchases: List<Purchase>,
        todayEpochDay: Long
    ): ProductPrediction {
        // 対象商品の購入履歴をフィルタし、同じ日の購入は 1 回に合算する(F-1, F-2)。
        // 牛乳 1 個の行が同じ日に 2 つあれば、数量 2 の購入 1 回として扱う
        val productPurchases = mergeSameDayPurchases(
            purchases.filter { it.productId == product.id }
        )

        if (productPurchases.isEmpty()) {
            return ProductPrediction(
                product = product,
                totalPurchases = 0,
                lastPurchasedOnEpochDay = null,
                cycleDaysPerUnit = DEFAULT_CYCLE_DAYS,
                isEstimatedCycle = true,
                predictedOutOfStockOnEpochDay = todayEpochDay,
                remainingDays = 0,
                isRunningOut = false,
                status = PredictionStatus.NO_DATA
            )
        }

        val lastPurchase = productPurchases.last()
        val totalPurchases = productPurchases.size

        val (cycleDaysPerUnit, isEstimated) = if (totalPurchases >= 2) {
            val intervals = calculateIntervalsPerUnit(productPurchases)
            if (intervals.isNotEmpty()) {
                Pair(calculateMedian(intervals), false)
            } else {
                Pair(getDefaultCycle(product.category), true)
            }
        } else {
            Pair(getDefaultCycle(product.category), true)
        }

        // 直近購入数量を考慮した実効サイクル日数 (F-2: 周期 × 購入数量)
        val effectiveCycleDays = cycleDaysPerUnit * lastPurchase.quantity
        val basePredictedOutOfStock = lastPurchase.purchasedOnEpochDay + effectiveCycleDays.roundToLong()

        // F-5: 「まだある」で切れる日を後ろにずらす
        val finalPredictedOutOfStock = if (product.snoozedUntilEpochDay != null) {
            maxOf(basePredictedOutOfStock, product.snoozedUntilEpochDay)
        } else {
            basePredictedOutOfStock
        }

        val remainingDays = finalPredictedOutOfStock - todayEpochDay

        // F-4: 周期(1 個あたり)の 2 割 (最短 2 日、最長 7 日) 以内に入った商品と、切れる日を過ぎた商品を「なくなりそう」と判定。
        // 要件の F-1 で周期は 1 個あたりなので、数量を掛けた日数ではなく 1 個あたりの周期から求める
        val bufferDays = calculateBufferDays(cycleDaysPerUnit)
        val isRunningOut = product.isTracked && (remainingDays <= bufferDays)

        val status = when {
            !product.isTracked -> PredictionStatus.SUFFICIENT
            remainingDays <= 0 -> PredictionStatus.OUT_OF_STOCK
            remainingDays <= bufferDays -> PredictionStatus.RUNNING_OUT
            else -> PredictionStatus.SUFFICIENT
        }

        return ProductPrediction(
            product = product,
            totalPurchases = totalPurchases,
            lastPurchasedOnEpochDay = lastPurchase.purchasedOnEpochDay,
            cycleDaysPerUnit = cycleDaysPerUnit,
            isEstimatedCycle = isEstimated,
            predictedOutOfStockOnEpochDay = finalPredictedOutOfStock,
            remainingDays = remainingDays,
            isRunningOut = isRunningOut,
            status = status,
            bufferDays = bufferDays
        )
    }

    /**
     * 商品と日付が同じ購入を 1 件にまとめ、数量と金額を足して日付昇順で返す。
     * 呼び出し側が 1 商品分だけ渡す前提だが、商品が混ざっていても商品ごとにまとめる。
     */
    fun mergeSameDayPurchases(purchases: List<Purchase>): List<Purchase> =
        purchases
            .groupBy { it.productId to it.purchasedOnEpochDay }
            .map { (_, group) ->
                if (group.size == 1) {
                    group.first()
                } else {
                    group.first().copy(
                        quantity = group.sumOf { it.quantity },
                        price = group.sumOf { it.price }
                    )
                }
            }
            .sortedBy { it.purchasedOnEpochDay }

    /**
     * (次回購入までの日数 ÷ 前回購入数量) のリストを計算
     * 同日購入など0日以下の間隔は外れ値としてスキップ
     */
    fun calculateIntervalsPerUnit(purchases: List<Purchase>): List<Double> {
        val intervals = mutableListOf<Double>()
        for (i in 0 until purchases.size - 1) {
            val prev = purchases[i]
            val next = purchases[i + 1]
            val days = (next.purchasedOnEpochDay - prev.purchasedOnEpochDay).toDouble()
            val quantity = if (prev.quantity > 0) prev.quantity else 1.0
            if (days > 0) {
                intervals.add(days / quantity)
            }
        }
        return intervals
    }

    /**
     * 中央値の計算
     */
    fun calculateMedian(values: List<Double>): Double {
        if (values.isEmpty()) return DEFAULT_CYCLE_DAYS
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[middle]
        } else {
            (sorted[middle - 1] + sorted[middle]) / 2.0
        }
    }

    /**
     * 周期(1 個あたり)の2割（最短2日、最長7日）の計算 (F-4, F-5)
     */
    fun calculateBufferDays(cycleDays: Double): Long {
        val raw = (cycleDays * 0.2).roundToLong()
        return raw.coerceIn(2L, 7L)
    }

    private fun getDefaultCycle(category: String): Double {
        return defaultCategoryCycleDays[category] ?: DEFAULT_CYCLE_DAYS
    }
}
