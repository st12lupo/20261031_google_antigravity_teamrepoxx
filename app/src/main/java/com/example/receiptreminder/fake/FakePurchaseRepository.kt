package com.example.receiptreminder.fake

import com.example.receiptreminder.domain.model.PurchaseRecord
import com.example.receiptreminder.domain.repository.PurchaseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

class FakePurchaseRepository(
    initialPurchases: List<PurchaseRecord> = SampleData.initialPurchasesForDishSoap()
) : PurchaseRepository {

    private val _purchasesFlow = MutableStateFlow(initialPurchases)

    override fun getAllPurchasesStream(): Flow<List<PurchaseRecord>> {
        return _purchasesFlow.asStateFlow().map { list ->
            list.sortedByDescending { it.purchasedOn }
        }
    }

    override suspend fun insertPurchase(purchase: PurchaseRecord) {
        val current = _purchasesFlow.value.toMutableList()
        current.add(0, purchase)
        _purchasesFlow.value = current
    }

    override suspend fun updatePurchase(purchase: PurchaseRecord) {
        val current = _purchasesFlow.value.toMutableList()
        val index = current.indexOfFirst { it.id == purchase.id }
        if (index != -1) {
            current[index] = purchase
            _purchasesFlow.value = current
        }
    }

    override suspend fun deletePurchase(purchaseId: String) {
        val current = _purchasesFlow.value.toMutableList()
        current.removeAll { it.id == purchaseId }
        _purchasesFlow.value = current
    }

    fun resetData() {
        _purchasesFlow.value = emptyList()
    }
}
