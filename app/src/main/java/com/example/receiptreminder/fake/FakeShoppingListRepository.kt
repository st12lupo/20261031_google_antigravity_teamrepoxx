package com.example.receiptreminder.fake

import com.example.receiptreminder.domain.model.Product
import com.example.receiptreminder.domain.model.ShoppingItem
import com.example.receiptreminder.domain.repository.ShoppingListRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import kotlin.math.roundToInt

class FakeShoppingListRepository(
    initialItems: List<ShoppingItem> = SampleData.initialShoppingItems()
) : ShoppingListRepository {

    private val _itemsFlow = MutableStateFlow(initialItems)

    override fun getShoppingItemsStream(): Flow<List<ShoppingItem>> {
        // なくなりそう順（残り日数が少ない順）にソートして返却
        return _itemsFlow.asStateFlow().map { list ->
            list.filter { it.product.isTracked }
                .sortedBy { it.product.remainingDays }
        }
    }

    override suspend fun markAsPurchased(productId: String, quantity: Int) {
        val current = _itemsFlow.value.toMutableList()
        val index = current.indexOfFirst { it.product.id == productId }
        if (index != -1) {
            val old = current[index].product
            val daysToAdd = (old.cycleDays * quantity).roundToInt()
            val newPredictedOut = LocalDate.now().plusDays(daysToAdd.toLong())
            val updated = old.copy(
                remainingDays = daysToAdd,
                predictedOutDate = newPredictedOut,
                becameLowDate = null
            )
            current[index] = ShoppingItem(updated)
            _itemsFlow.value = current
        }
    }

    override suspend fun snooze(productId: String) {
        // 「まだある」：周期の2割（最短2日、最長7日）後ろにずらす（F-5, Q-2）
        val current = _itemsFlow.value.toMutableList()
        val index = current.indexOfFirst { it.product.id == productId }
        if (index != -1) {
            val old = current[index].product
            val shiftDays = (old.cycleDays * 0.2).roundToInt().coerceIn(2, 7)
            val updated = old.copy(
                remainingDays = old.remainingDays + shiftDays,
                predictedOutDate = old.predictedOutDate.plusDays(shiftDays.toLong())
            )
            current[index] = ShoppingItem(updated)
            _itemsFlow.value = current
        }
    }

    override suspend fun stopTracking(productId: String) {
        // 「もう買わない」：予測対象から除外（F-6）
        val current = _itemsFlow.value.toMutableList()
        val index = current.indexOfFirst { it.product.id == productId }
        if (index != -1) {
            val old = current[index].product
            val updated = old.copy(isTracked = false)
            current[index] = ShoppingItem(updated)
            _itemsFlow.value = current
        }
    }

    override suspend fun addManualPurchase(
        productName: String,
        purchasedOn: LocalDate,
        quantity: Int,
        price: Int?,
        storeName: String?
    ) {
        val current = _itemsFlow.value.toMutableList()
        val index = current.indexOfFirst { it.product.name == productName }
        val cycle = if (index != -1) current[index].product.cycleDays else 30.0
        val daysToAdd = (cycle * quantity).roundToInt()
        val newProduct = if (index != -1) {
            current[index].product.copy(
                isTracked = true,
                remainingDays = daysToAdd,
                predictedOutDate = purchasedOn.plusDays(daysToAdd.toLong())
            )
        } else {
            Product(
                id = "prod-${System.currentTimeMillis()}",
                name = productName,
                isTracked = true,
                isEstimated = true,
                cycleDays = cycle,
                remainingDays = daysToAdd,
                predictedOutDate = purchasedOn.plusDays(daysToAdd.toLong())
            )
        }

        if (index != -1) {
            current[index] = ShoppingItem(newProduct)
        } else {
            current.add(ShoppingItem(newProduct))
        }
        _itemsFlow.value = current
    }

    override suspend fun addPurchaseForProduct(productId: String, purchasedOn: LocalDate, quantity: Int, price: Int?) {
        val name = _itemsFlow.value.firstOrNull { it.product.id == productId }?.product?.name ?: return
        addManualPurchase(name, purchasedOn, quantity, price, null)
    }

    fun resetData(items: List<ShoppingItem> = emptyList()) {
        _itemsFlow.value = items
    }
}
