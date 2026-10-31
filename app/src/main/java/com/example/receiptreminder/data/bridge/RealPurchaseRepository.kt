package com.example.receiptreminder.data.bridge

import com.example.receiptreminder.domain.model.PurchaseRecord
import com.example.receiptreminder.domain.model.PurchaseSource as UiPurchaseSource
import com.example.receiptreminder.domain.repository.PurchaseRepository
import domain.model.Purchase as DbPurchase
import domain.model.PurchaseSource as DbPurchaseSource
import domain.repository.ReminderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import kotlin.math.roundToInt

class RealPurchaseRepository(
    private val reminderRepository: ReminderRepository,
    private val trigger: DataUpdateTrigger = DataUpdateTrigger()
) : PurchaseRepository {

    override fun getAllPurchasesStream(): Flow<List<PurchaseRecord>> = trigger.version.map {
        val purchases = reminderRepository.getAllPurchases()
        val products = reminderRepository.getAllProducts().associateBy { it.id }
        val receipts = reminderRepository.getAllReceipts().associateBy { it.id }

        purchases.map { p ->
            val product = products[p.productId]
            val receipt = p.receiptId?.let { receipts[it] }
            PurchaseRecord(
                id = p.id.toString(),
                productId = p.productId.toString(),
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

    override suspend fun insertPurchase(purchase: PurchaseRecord) {
        val pId = purchase.productId.toLongOrNull() ?: run {
            reminderRepository.getOrCreateProduct(purchase.productName).id
        }
        val qty = if (purchase.quantity > 0) purchase.quantity else 1
        reminderRepository.recordManualPurchase(
            productId = pId,
            purchasedOnEpochDay = purchase.purchasedOn.toEpochDay(),
            quantity = qty.toDouble(),
            price = purchase.price ?: 0,
            source = when (purchase.source) {
                UiPurchaseSource.RECEIPT -> DbPurchaseSource.RECEIPT
                UiPurchaseSource.MANUAL -> DbPurchaseSource.MANUAL
            }
        )
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
