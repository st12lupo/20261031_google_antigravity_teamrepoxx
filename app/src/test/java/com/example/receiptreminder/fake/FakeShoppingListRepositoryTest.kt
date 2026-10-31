package com.example.receiptreminder.fake

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class FakeShoppingListRepositoryTest {

    private lateinit var repository: FakeShoppingListRepository

    @Before
    fun setUp() {
        repository = FakeShoppingListRepository()
    }

    @Test
    fun getShoppingItemsStream_containsInitialMockupItemsSortedByRemainingDays() = runTest {
        val items = repository.getShoppingItemsStream().first()

        assertEquals(5, items.size)
        // なくなりそう順（残り日数の昇順）
        assertEquals("牛乳", items[0].product.name)
        assertEquals(-1, items[0].product.remainingDays)

        assertEquals("食器用洗剤", items[1].product.name)
        assertEquals(2, items[1].product.remainingDays)

        assertEquals("トイレットペーパー", items[2].product.name)
        assertEquals(4, items[2].product.remainingDays)

        assertEquals("シャンプー", items[3].product.name)
        assertEquals(18, items[3].product.remainingDays)

        assertEquals("米", items[4].product.name)
        assertEquals(26, items[4].product.remainingDays)
        assertTrue(items[4].product.isEstimated)
    }

    @Test
    fun markAsPurchased_updatesRemainingDaysBasedOnCycle() = runTest {
        repository.markAsPurchased(SampleData.dishSoap.id, quantity = 1)
        val items = repository.getShoppingItemsStream().first()
        val soap = items.first { it.product.id == SampleData.dishSoap.id }

        // 周期30日 × 1個 = 30日
        assertEquals(30, soap.product.remainingDays)
    }

    @Test
    fun snooze_delaysExpirationByTwentyPercent() = runTest {
        val initialItems = repository.getShoppingItemsStream().first()
        val soapBefore = initialItems.first { it.product.id == SampleData.dishSoap.id }
        val remainingBefore = soapBefore.product.remainingDays

        repository.snooze(SampleData.dishSoap.id)

        val updatedItems = repository.getShoppingItemsStream().first()
        val soapAfter = updatedItems.first { it.product.id == SampleData.dishSoap.id }

        // 30日の20% = 6日後ろにずれる
        assertEquals(remainingBefore + 6, soapAfter.product.remainingDays)
    }

    @Test
    fun stopTracking_removesProductFromShoppingListStream() = runTest {
        repository.stopTracking(SampleData.milk.id)
        val items = repository.getShoppingItemsStream().first()

        assertNull(items.firstOrNull { it.product.id == SampleData.milk.id })
    }

    @Test
    fun addManualPurchase_addsNewItemToShoppingList() = runTest {
        repository.addManualPurchase(
            productName = "ハンドソープ",
            purchasedOn = LocalDate.now(),
            quantity = 1
        )
        val items = repository.getShoppingItemsStream().first()

        assertNotNull(items.firstOrNull { it.product.name == "ハンドソープ" })
    }
}
