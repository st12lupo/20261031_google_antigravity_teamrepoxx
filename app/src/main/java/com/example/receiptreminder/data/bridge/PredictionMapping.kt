package com.example.receiptreminder.data.bridge

import com.example.receiptreminder.domain.model.Product as UiProduct
import domain.model.PredictionStatus
import domain.model.ProductPrediction
import java.time.LocalDate

/** 予測結果を UI のモデルへ変換する。なくなりそうの判定(F-4)は予測側の結果をそのまま渡す。 */
internal fun ProductPrediction.toUiProduct(): UiProduct = UiProduct(
    id = product.id.toString(),
    name = product.name,
    category = product.category,
    isTracked = product.isTracked,
    // 履歴 0 件は予測器が既定周期扱い(isEstimatedCycle=true)で返すが、「推定」は購入 1 回の商品だけに出す(F-3)
    isEstimated = isEstimatedCycle && status != PredictionStatus.NO_DATA,
    cycleDays = cycleDaysPerUnit,
    predictedOutDate = LocalDate.ofEpochDay(predictedOutOfStockOnEpochDay),
    becameLowDate = null,
    remainingDays = remainingDays.toInt(),
    isRunningOut = isRunningOut,
    hasPurchaseHistory = status != PredictionStatus.NO_DATA
)
