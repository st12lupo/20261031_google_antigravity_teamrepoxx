package com.example.receiptreminder.data.bridge

import com.example.receiptreminder.domain.model.ShoppingItem
import com.example.receiptreminder.domain.repository.ShoppingListRepository
import domain.model.PredictionStatus
import domain.model.Product as DbProduct
import domain.model.PurchaseSource as DbPurchaseSource
import domain.prediction.ConsumptionPredictor
import domain.receipt.ProductNormalizer
import domain.repository.ReminderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import prediction.DefaultConsumptionPredictor
import receipt.normalize.DefaultProductNormalizer
import java.time.LocalDate

class RealShoppingListRepository(
    private val reminderRepository: ReminderRepository,
    private val consumptionPredictor: ConsumptionPredictor = DefaultConsumptionPredictor(),
    private val todayProvider: () -> LocalDate = { LocalDate.now() },
    private val trigger: DataUpdateTrigger = DataUpdateTrigger(),
    /** 手動追加の商品名をレシートと同じ名寄せにかける(B-1, B-3)。null なら入力のまま扱う */
    private val normalizer: ProductNormalizer? = null,
    private val aliasStore: LearnedAliasStore? = null
) : ShoppingListRepository {

    override fun getShoppingItemsStream(): Flow<List<ShoppingItem>> = trigger.version.map {
        val today = todayProvider()
        val todayEpochDay = today.toEpochDay()
        val products = reminderRepository.getAllProducts()
        val allPurchases = reminderRepository.getAllPurchases()
        val purchasesByProduct = allPurchases.groupBy { it.productId }

        products
            .filter { it.isTracked }
            .map { domainProduct ->
                val purchases = purchasesByProduct[domainProduct.id] ?: emptyList()
                val prediction = consumptionPredictor.predict(domainProduct, purchases, todayEpochDay)
                prediction to ShoppingItem(product = prediction.toUiProduct())
            }
            // 購入履歴が無い商品(履歴を全部削除した商品など)は残り 0 日でも対象にしない (F-4, S-5, B-4)
            .filter { (prediction, _) -> prediction.status != PredictionStatus.NO_DATA }
            .map { it.second }
            .sortedBy { it.product.remainingDays }
    }

    override suspend fun markAsPurchased(productId: String, quantity: Int) {
        val id = productId.toLongOrNull() ?: return
        val qty = if (quantity > 0) quantity else 1
        reminderRepository.recordManualPurchase(
            productId = id,
            purchasedOnEpochDay = todayProvider().toEpochDay(),
            quantity = qty.toDouble(),
            price = 0,
            source = DbPurchaseSource.MANUAL
        )
        trigger.notifyChanged()
    }

    override suspend fun snooze(productId: String) {
        val id = productId.toLongOrNull() ?: return
        val product = reminderRepository.getProductById(id) ?: return
        val purchases = reminderRepository.getPurchasesForProduct(id)
        val todayEpochDay = todayProvider().toEpochDay()
        val prediction = consumptionPredictor.predict(product, purchases, todayEpochDay)
        if (prediction.status == PredictionStatus.NO_DATA) return
        // F-5: 切れる日を周期の 2 割(最短 2 日、最長 7 日)後ろにずらす。
        // すでに切れる日を過ぎている商品は、今日を起点にする(過去の日付に足しても「まだある」にならないため)。
        val baseDay = maxOf(prediction.predictedOutOfStockOnEpochDay, todayEpochDay)
        reminderRepository.snoozeProductUntil(id, baseDay + prediction.bufferDays)
        trigger.notifyChanged()
    }

    override suspend fun stopTracking(productId: String) {
        val id = productId.toLongOrNull() ?: return
        // 「もう買わない」だけが印を立てる。次にレシートで購入すると対象に戻る(F-6)
        reminderRepository.stopTracking(id)
        trigger.notifyChanged()
    }

    override suspend fun addManualPurchase(
        productName: String,
        purchasedOn: LocalDate,
        quantity: Int,
        price: Int?,
        storeName: String?
    ) {
        val trimmed = productName.trim()
        if (trimmed.isEmpty()) return
        // レシート取り込みと同じ別名・辞書の完全一致だけを通し、「ｷﾞｭｳﾆｭｳ」と「牛乳」を同じ商品にまとめる(P-1, B-3)。
        // 部分一致は使わない。入力した名前を別の商品に黙って置き換えないため、一致しなければ容量表記を除いた名前のまま使う
        aliasStore?.ensureLoaded()
        val resolved = (normalizer as? DefaultProductNormalizer)?.resolveExact(trimmed)
        val product = if (resolved != null) {
            reminderRepository.getOrCreateProduct(resolved.normalizedName, resolved.category)
        } else {
            val cleaned = normalizer?.cleanText(trimmed)?.takeIf { it.isNotBlank() } ?: trimmed
            reminderRepository.getOrCreateProduct(cleaned)
        }
        recordPurchaseFor(product, purchasedOn, quantity, price)
    }

    override suspend fun addPurchaseForProduct(
        productId: String,
        purchasedOn: LocalDate,
        quantity: Int,
        price: Int?
    ) {
        val id = productId.toLongOrNull() ?: return
        // 名前の解決(別名・辞書)は通さない。詳細画面で開いている商品そのものに記録する(B-1)
        val product = reminderRepository.getProductById(id) ?: return
        recordPurchaseFor(product, purchasedOn, quantity, price)
    }

    private suspend fun recordPurchaseFor(product: DbProduct, purchasedOn: LocalDate, quantity: Int, price: Int?) {
        // P-4: 「もう買わない」で外した商品だけ、購入で対象に戻す。詳細画面で OFF にした商品は OFF のまま
        if (!product.isTracked && product.stoppedByUser) {
            reminderRepository.setTracked(product.id, true)
        }
        val qty = if (quantity > 0) quantity else 1
        reminderRepository.recordManualPurchase(
            productId = product.id,
            purchasedOnEpochDay = purchasedOn.toEpochDay(),
            quantity = qty.toDouble(),
            price = price ?: 0,
            source = DbPurchaseSource.MANUAL
        )
        trigger.notifyChanged()
    }
}
