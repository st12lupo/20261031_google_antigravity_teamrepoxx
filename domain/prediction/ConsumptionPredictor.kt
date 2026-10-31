package domain.prediction

import domain.model.Product
import domain.model.ProductPrediction
import domain.model.Purchase

interface ConsumptionPredictor {
    /**
     * 商品の購入履歴と今日の日付から、消費予測（残り日数、切れる日、なくなりそうかの判定）を計算する。
     * - 同じ商品で日付が同じ購入は、数量を足して 1 回の購入にまとめてから計算する
     * - 購入1回: カテゴリ別既定周期で計算し、isEstimatedCycle=true とする
     * - 購入2回以上: (次回購入までの日数 ÷ 前回購入数量) の中央値を周期とし、直近数量を掛ける
     * - スヌーズ(まだある): 周期(1 個あたり)の2割(2〜7日)後ろにずらす
     * - なくなりそう: 残り日数が周期(1 個あたり)の2割(最短2日、最長7日)以内、または超過
     */
    fun predict(
        product: Product,
        purchases: List<Purchase>,
        todayEpochDay: Long
    ): ProductPrediction
}
