package com.example.receiptreminder.fake

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class FakeProductRepositoryTest {

    private lateinit var repository: FakeProductRepository

    @Before
    fun setUp() {
        repository = FakeProductRepository()
    }

    @Test
    fun getProduct_returnsProductDetails() = runTest {
        val product = repository.getProduct(SampleData.dishSoap.id)

        assertNotNull(product)
        assertEquals("食器用洗剤", product?.name)
        assertEquals(30.0, product?.cycleDays ?: 0.0, 0.01)
    }

    @Test
    fun getProductPurchasesStream_returnsPurchasesForProduct() = runTest {
        val purchases = repository.getProductPurchasesStream(SampleData.dishSoap.id).first()

        assertEquals(4, purchases.size)
        assertEquals("みどりマート 中央店", purchases[0].storeName)
    }

    @Test
    fun updateTrackedStatus_togglesIsTracked() = runTest {
        repository.updateTrackedStatus(SampleData.dishSoap.id, false)
        val product = repository.getProduct(SampleData.dishSoap.id)

        assertFalse(product?.isTracked ?: true)
    }

    @Test
    fun updatePurchase_and_deletePurchase_workProperly() = runTest {
        val purchases = repository.getProductPurchasesStream(SampleData.dishSoap.id).first()
        val targetPurchase = purchases.first()

        // 編集テスト (Q-1)
        val updated = targetPurchase.copy(quantity = 3, price = 594)
        repository.updatePurchase(updated)

        val updatedList = repository.getProductPurchasesStream(SampleData.dishSoap.id).first()
        assertEquals(3, updatedList.first { it.id == targetPurchase.id }.quantity)

        // 削除テスト (Q-1)
        repository.deletePurchase(targetPurchase.id)
        val afterDelete = repository.getProductPurchasesStream(SampleData.dishSoap.id).first()
        assertEquals(3, afterDelete.size)
        assertNull(afterDelete.firstOrNull { it.id == targetPurchase.id })
    }
}
