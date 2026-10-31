package com.example.receiptreminder.fake

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class FakeReceiptReviewServiceTest {

    private lateinit var shoppingListRepository: FakeShoppingListRepository
    private lateinit var reviewService: FakeReceiptReviewService

    @Before
    fun setUp() {
        shoppingListRepository = FakeShoppingListRepository()
        reviewService = FakeReceiptReviewService(
            shoppingListRepository = shoppingListRepository
        )
    }

    @Test
    fun getPendingReceipt_returnsReceiptMatchingReviewMockup() = runTest {
        val pending = reviewService.getPendingReceipt()

        assertNotNull(pending)
        assertEquals("みどりマート 中央店", pending?.storeName)
        assertEquals(1544, pending?.totalAmount)
        assertEquals(4, pending?.items?.size)
        assertTrue(pending?.isDuplicateWarning ?: false)
    }

    @Test
    fun checkDuplicate_detectsDuplicateReceipts() = runTest {
        val pending = reviewService.getPendingReceipt()!!
        val isDup = reviewService.checkDuplicate(
            storeName = pending.storeName,
            dateString = pending.purchasedOn.toString(),
            totalAmount = pending.totalAmount
        )

        assertTrue(isDup)

        val notDup = reviewService.checkDuplicate(
            storeName = "別の店",
            dateString = pending.purchasedOn.toString(),
            totalAmount = pending.totalAmount
        )

        assertFalse(notDup)
    }

    @Test
    fun saveReceipt_savesItemsAndClearsPendingReceipt() = runTest {
        val pending = reviewService.getPendingReceipt()!!
        val result = reviewService.saveReceipt(pending)

        assertTrue(result.isSuccess)
        assertNull(reviewService.getPendingReceipt())
    }

    @Test
    fun discardReceipt_clearsPendingReceipt() = runTest {
        reviewService.discardReceipt()

        assertNull(reviewService.getPendingReceipt())
    }
}
