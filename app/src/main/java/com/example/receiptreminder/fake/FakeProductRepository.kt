package com.example.receiptreminder.fake

import com.example.receiptreminder.domain.model.Product
import com.example.receiptreminder.domain.model.PurchaseRecord
import com.example.receiptreminder.domain.repository.ProductRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

class FakeProductRepository(
    initialProducts: List<Product> = SampleData.initialProducts(),
    initialPurchases: List<PurchaseRecord> = SampleData.initialPurchasesForDishSoap()
) : ProductRepository {

    private val _productsFlow = MutableStateFlow(initialProducts)
    private val _purchasesFlow = MutableStateFlow(initialPurchases)

    override fun getProductsStream(): Flow<List<Product>> = _productsFlow.asStateFlow()

    override suspend fun getProduct(id: String): Product? {
        return _productsFlow.value.firstOrNull { it.id == id }
    }

    override fun getProductPurchasesStream(productId: String): Flow<List<PurchaseRecord>> {
        return _purchasesFlow.asStateFlow().map { list ->
            list.filter { it.productId == productId }
                .sortedByDescending { it.purchasedOn }
        }
    }

    override suspend fun updateTrackedStatus(productId: String, isTracked: Boolean) {
        val current = _productsFlow.value.toMutableList()
        val index = current.indexOfFirst { it.id == productId }
        if (index != -1) {
            current[index] = current[index].copy(isTracked = isTracked)
            _productsFlow.value = current
        }
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

    fun addProduct(product: Product) {
        val current = _productsFlow.value.toMutableList()
        val index = current.indexOfFirst { it.id == product.id }
        if (index != -1) {
            current[index] = product
        } else {
            current.add(product)
        }
        _productsFlow.value = current
    }

    fun addPurchase(purchase: PurchaseRecord) {
        val current = _purchasesFlow.value.toMutableList()
        current.add(0, purchase)
        _purchasesFlow.value = current
    }

    fun resetData() {
        _productsFlow.value = emptyList()
        _purchasesFlow.value = emptyList()
    }
}
