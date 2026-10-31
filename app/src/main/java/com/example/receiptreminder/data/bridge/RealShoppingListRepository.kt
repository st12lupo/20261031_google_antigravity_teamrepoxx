package com.example.receiptreminder.data.bridge

import com.example.receiptreminder.domain.model.ShoppingItem
import com.example.receiptreminder.domain.model.Product as UiProduct
import com.example.receiptreminder.domain.repository.ShoppingListRepository
import domain.model.Product as DbProduct
import domain.model.PurchaseSource as DbPurchaseSource
import domain.prediction.ConsumptionPredictor
import domain.repository.ReminderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import prediction.DefaultConsumptionPredictor
import java.time.LocalDate
import kotlin.math.roundToLong

class RealShoppingListRepository(
    private val reminderRepository: ReminderRepository,
    private val consumptionPredictor: ConsumptionPredictor = DefaultConsumptionPredictor(),
    private val todayProvider: () -> LocalDate = { LocalDate.now() },
    private val trigger: DataUpdateTrigger = DataUpdateTrigger()
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
                val predictedOutDate = LocalDate.ofEpochDay(prediction.predictedOutOfStockOnEpochDay)
                val uiProduct = UiProduct(
                    id = domainProduct.id.toString(),
                    name = domainProduct.name,
                    category = domainProduct.category,
                    isTracked = domainProduct.isTracked,
                    isEstimated = prediction.isEstimatedCycle,
                    cycleDays = prediction.cycleDaysPerUnit,
                    predictedOutDate = predictedOutDate,
                    becameLowDate = null,
                    remainingDays = prediction.remainingDays.toInt()
                )
                ShoppingItem(product = uiProduct)
            }
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
        val extraDays = (prediction.cycleDaysPerUnit * 0.2).roundToLong().coerceIn(2L, 7L)
        reminderRepository.snoozeProduct(id, extraDays)
        trigger.notifyChanged()
    }

    override suspend fun stopTracking(productId: String) {
        val id = productId.toLongOrNull() ?: return
        reminderRepository.setTracked(id, false)
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
        val product = reminderRepository.getOrCreateProduct(trimmed)
        if (!product.isTracked) {
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
