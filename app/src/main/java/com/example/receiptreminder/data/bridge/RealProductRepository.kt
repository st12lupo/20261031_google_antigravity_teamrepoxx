package com.example.receiptreminder.data.bridge

import com.example.receiptreminder.domain.model.PurchaseRecord
import com.example.receiptreminder.domain.model.PurchaseSource as UiPurchaseSource
import com.example.receiptreminder.domain.model.Product as UiProduct
import com.example.receiptreminder.domain.repository.ProductRepository
import domain.model.Product as DbProduct
import domain.model.Purchase as DbPurchase
import domain.model.PurchaseSource as DbPurchaseSource
import domain.prediction.ConsumptionPredictor
import domain.repository.ReminderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import prediction.DefaultConsumptionPredictor
import java.time.LocalDate
import kotlin.math.roundToInt

class RealProductRepository(
    private val reminderRepository: ReminderRepository,
    private val consumptionPredictor: ConsumptionPredictor = DefaultConsumptionPredictor(),
    private val todayProvider: () -> LocalDate = { LocalDate.now() },
    private val trigger: DataUpdateTrigger = DataUpdateTrigger()
) : ProductRepository {

    override fun getProductsStream(): Flow<List<UiProduct>> = trigger.version.map {
        val today = todayProvider()
        val todayEpochDay = today.toEpochDay()
        val products = reminderRepository.getAllProducts()
        val allPurchases = reminderRepository.getAllPurchases()
        val purchasesByProduct = allPurchases.groupBy { it.productId }

        products.map { domainProduct ->
            val purchases = purchasesByProduct[domainProduct.id] ?: emptyList()
            val prediction = consumptionPredictor.predict(domainProduct, purchases, todayEpochDay)
            prediction.toUiProduct()
        }
    }

    override suspend fun getProduct(id: String): UiProduct? {
        val productId = id.toLongOrNull() ?: return null
        val domainProduct = reminderRepository.getProductById(productId) ?: return null
        val purchases = reminderRepository.getPurchasesForProduct(productId)
        val today = todayProvider()
        val prediction = consumptionPredictor.predict(domainProduct, purchases, today.toEpochDay())
        return prediction.toUiProduct()
    }

    override fun getProductPurchasesStream(productId: String): Flow<List<PurchaseRecord>> = trigger.version.map {
        val pId = productId.toLongOrNull() ?: return@map emptyList()
        val product = reminderRepository.getProductById(pId)
        val purchases = reminderRepository.getPurchasesForProduct(pId)
        val receipts = reminderRepository.getAllReceipts().associateBy { it.id }

        purchases.map { p ->
            val receipt = p.receiptId?.let { receipts[it] }
            PurchaseRecord(
                id = p.id.toString(),
                productId = productId,
                productName = product?.name ?: "不明な商品",
                purchasedOn = LocalDate.ofEpochDay(p.purchasedOnEpochDay),
                quantity = p.quantity.roundToInt().coerceAtLeast(1),
                price = p.price.takeIf { it > 0 },
                source = when (p.source) {
                    DbPurchaseSource.RECEIPT -> UiPurchaseSource.RECEIPT
                    else -> UiPurchaseSource.MANUAL
                },
                storeName = receipt?.storeName,
                receiptId = p.receiptId?.toString()
            )
        }.sortedByDescending { it.purchasedOn }
    }

    override suspend fun updateTrackedStatus(productId: String, isTracked: Boolean) {
        val id = productId.toLongOrNull() ?: return
        reminderRepository.setTracked(id, isTracked)
        trigger.notifyChanged()
    }

    override suspend fun updatePurchase(purchase: PurchaseRecord) {
        val id = purchase.id.toLongOrNull() ?: return
        val pId = purchase.productId.toLongOrNull() ?: run {
            reminderRepository.getOrCreateProduct(purchase.productName).id
        }
        val domainPurchase = DbPurchase(
            id = id,
            receiptId = purchase.receiptId?.toLongOrNull(),
            productId = pId,
            purchasedOnEpochDay = purchase.purchasedOn.toEpochDay(),
            quantity = purchase.quantity.toDouble(),
            price = purchase.price ?: 0,
            source = when (purchase.source) {
                UiPurchaseSource.RECEIPT -> DbPurchaseSource.RECEIPT
                UiPurchaseSource.MANUAL -> DbPurchaseSource.MANUAL
            }
        )
        reminderRepository.updatePurchase(domainPurchase)
        trigger.notifyChanged()
    }

    override suspend fun deletePurchase(purchaseId: String) {
        val id = purchaseId.toLongOrNull() ?: return
        reminderRepository.deletePurchase(id)
        trigger.notifyChanged()
    }
}
