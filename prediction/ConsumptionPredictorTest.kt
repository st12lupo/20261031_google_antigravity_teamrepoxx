package prediction

import domain.model.PredictionStatus
import domain.model.Product
import domain.model.Purchase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsumptionPredictorTest {

    private val predictor = DefaultConsumptionPredictor()
    private val today = 1000L

    @Test
    fun predict_singlePurchase_usesCategoryDefaultCycleAndMarksEstimated() {
        // 購入 1 回: カテゴリ既定周期（日用品: 30日）が使われ、isEstimatedCycle=true となること
        val product = Product(id = 1, name = "洗剤", category = "日用品")
        val purchases = listOf(
            Purchase(id = 1, productId = 1, purchasedOnEpochDay = today - 10, quantity = 1.0)
        )

        val result = predictor.predict(product, purchases, today)

        assertEquals(1, result.totalPurchases)
        assertEquals(30.0, result.cycleDaysPerUnit, 0.001)
        assertTrue(result.isEstimatedCycle)
        // 切れる日: (today - 10) + 30 = today + 20
        assertEquals(today + 20, result.predictedOutOfStockOnEpochDay)
        assertEquals(20L, result.remainingDays)
        assertFalse(result.isRunningOut)
        assertEquals(PredictionStatus.SUFFICIENT, result.status)
    }

    @Test
    fun predict_multiplePurchases_calculatesMedianCycle() {
        // 購入複数回: (次回購入までの日数 ÷ 前回購入数量) の中央値
        // Day 100 に 1個購入、Day 120 に 1個購入 (間隔 20日)
        // Day 120 に 1個購入、Day 150 に 1個購入 (間隔 30日)
        // 中央値は (20 + 30) / 2 = 25.0日
        val product = Product(id = 1, name = "シャンプー")
        val purchases = listOf(
            Purchase(id = 1, productId = 1, purchasedOnEpochDay = 100, quantity = 1.0),
            Purchase(id = 2, productId = 1, purchasedOnEpochDay = 120, quantity = 1.0),
            Purchase(id = 3, productId = 1, purchasedOnEpochDay = 150, quantity = 1.0)
        )

        val result = predictor.predict(product, purchases, todayEpochDay = 160)

        assertEquals(3, result.totalPurchases)
        assertEquals(25.0, result.cycleDaysPerUnit, 0.001)
        assertFalse(result.isEstimatedCycle)
        // 切れる日: 150 + (25 * 1) = 175
        assertEquals(175L, result.predictedOutOfStockOnEpochDay)
        // 残り日数: 175 - 160 = 15日
        assertEquals(15L, result.remainingDays)
    }

    @Test
    fun predict_multiplePurchases_withQuantity_normalizesPerUnit() {
        // 数量考慮: 前回 2個購入した場合、日数 / 2 を単位あたり周期とする
        // Day 100 に 2個購入、Day 140 に購入 -> 40日 / 2個 = 20日/個
        // Day 140 に 1個購入、Day 160 に購入 -> 20日 / 1個 = 20日/個
        // Day 160 に 3個購入 (直近購入)
        val product = Product(id = 1, name = "ティッシュ")
        val purchases = listOf(
            Purchase(id = 1, productId = 1, purchasedOnEpochDay = 100, quantity = 2.0),
            Purchase(id = 2, productId = 1, purchasedOnEpochDay = 140, quantity = 1.0),
            Purchase(id = 3, productId = 1, purchasedOnEpochDay = 160, quantity = 3.0)
        )

        val result = predictor.predict(product, purchases, todayEpochDay = 170)

        assertEquals(20.0, result.cycleDaysPerUnit, 0.001)
        // 直近数量 3個なので実効サイクルは 20 * 3 = 60日
        // 切れる日: 160 + 60 = 220
        assertEquals(220L, result.predictedOutOfStockOnEpochDay)
        assertEquals(50L, result.remainingDays)
    }

    @Test
    fun predict_withOutliers_medianResistsExtremeValues() {
        // 外れ値耐性: 間隔が 2日(買い直し等の極端値)、28日(通常)、30日(通常)、32日(通常)、180日(買い忘れ等の極端値)
        // 順序付き間隔: [2, 28, 30, 32, 180] -> 中央値は 30日
        val intervals = listOf(2.0, 28.0, 30.0, 32.0, 180.0)
        val median = predictor.calculateMedian(intervals)
        assertEquals(30.0, median, 0.001)
    }

    @Test
    fun predict_runningOut_whenRemainingDaysWithinBuffer() {
        // なくなりそう判定: 周期 30日 の 2割 = 6日 (2〜7日の範囲内)
        // 残り 5日 (<= 6日) なので isRunningOut=true, status=RUNNING_OUT
        val product = Product(id = 1, name = "洗剤", category = "日用品")
        val purchases = listOf(
            Purchase(id = 1, productId = 1, purchasedOnEpochDay = today - 25, quantity = 1.0)
        )

        val result = predictor.predict(product, purchases, today)

        // 切れる日: (today - 25) + 30 = today + 5 -> 残り 5日
        assertEquals(5L, result.remainingDays)
        assertTrue(result.isRunningOut)
        assertEquals(PredictionStatus.RUNNING_OUT, result.status)
    }

    @Test
    fun predict_outOfStock_whenRemainingDaysZeroOrNegative() {
        // 切れている判定: 切れる日を過ぎた (残り 0日以下)
        val product = Product(id = 1, name = "洗剤", category = "日用品")
        val purchases = listOf(
            Purchase(id = 1, productId = 1, purchasedOnEpochDay = today - 35, quantity = 1.0)
        )

        val result = predictor.predict(product, purchases, today)

        // 切れる日: (today - 35) + 30 = today - 5 -> 残り -5日
        assertEquals(-5L, result.remainingDays)
        assertTrue(result.isRunningOut)
        assertEquals(PredictionStatus.OUT_OF_STOCK, result.status)
    }

    @Test
    fun predict_snooze_extendsPredictedDate() {
        // 「まだある」操作 (スヌーズ): 切れる日が後ろにずれること
        // 通常の切れる日: (today - 28) + 30 = today + 2
        // スヌーズ日: today + 8 に設定されている場合、切れる日は today + 8 となる
        val snoozedDate = today + 8
        val product = Product(
            id = 1,
            name = "洗剤",
            category = "日用品",
            snoozedUntilEpochDay = snoozedDate
        )
        val purchases = listOf(
            Purchase(id = 1, productId = 1, purchasedOnEpochDay = today - 28, quantity = 1.0)
        )

        val result = predictor.predict(product, purchases, today)

        assertEquals(snoozedDate, result.predictedOutOfStockOnEpochDay)
        assertEquals(8L, result.remainingDays)
    }

    @Test
    fun predict_untrackedProduct_isNotRunningOut() {
        // 「もう買わない」操作 (isTracked = false): なくなりそうと判定しない
        val product = Product(id = 1, name = "洗剤", isTracked = false)
        val purchases = listOf(
            Purchase(id = 1, productId = 1, purchasedOnEpochDay = today - 35, quantity = 1.0)
        )

        val result = predictor.predict(product, purchases, today)

        assertFalse(result.isRunningOut)
    }

    @Test
    fun predict_noPurchases_returnsNoData() {
        // 購入履歴なし
        val product = Product(id = 1, name = "未知の商品")
        val result = predictor.predict(product, emptyList(), today)

        assertEquals(0, result.totalPurchases)
        assertFalse(result.isRunningOut)
        assertEquals(PredictionStatus.NO_DATA, result.status)
    }

    @Test
    fun calculateBufferDays_clampsBetween2And7() {
        // 2割の計算が最短2日、最長7日に収まること
        // 5日の2割 = 1日 -> 2日に丸められる
        assertEquals(2L, predictor.calculateBufferDays(5.0))
        // 20日の2割 = 4日 -> 4日
        assertEquals(4L, predictor.calculateBufferDays(20.0))
        // 50日の2割 = 10日 -> 7日に丸められる
        assertEquals(7L, predictor.calculateBufferDays(50.0))
    }
}
