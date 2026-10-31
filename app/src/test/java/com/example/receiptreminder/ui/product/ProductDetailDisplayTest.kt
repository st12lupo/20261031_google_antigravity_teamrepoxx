package com.example.receiptreminder.ui.product

import com.example.receiptreminder.domain.model.Product
import com.example.receiptreminder.domain.model.StockStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 商品詳細の表示の判断。履歴のない商品は「対象外」と対象ボックスが矛盾せず、予測が出ない */
class ProductDetailDisplayTest {

    @Test
    fun `product without history shows no prediction and no snooze and is not called untracked`() {
        val product = Product(id = "1", name = "履歴なし", isTracked = true, hasPurchaseHistory = false)

        val display = productDetailDisplay(product)

        assertTrue(display.noHistory)
        assertFalse(display.showPrediction)
        assertFalse(display.showSnooze)
        assertFalse(display.showUntrackedBadge)
        assertEquals(StockStatus.NOT_TRACKED, product.status)
    }

    @Test
    fun `tracked product with history shows the prediction and snooze`() {
        val display = productDetailDisplay(Product(id = "1", name = "牛乳"))

        assertFalse(display.noHistory)
        assertTrue(display.showPrediction)
        assertTrue(display.showSnooze)
        assertFalse(display.showUntrackedBadge)
    }

    @Test
    fun `untracked product with history hides the prediction but keeps the untracked badge`() {
        val display = productDetailDisplay(Product(id = "1", name = "牛乳", isTracked = false))

        assertFalse(display.noHistory)
        assertFalse(display.showPrediction)
        assertTrue(display.showUntrackedBadge)
    }

    @Test
    fun `untracked product without history is both untracked and without history`() {
        val display = productDetailDisplay(
            Product(id = "1", name = "x", isTracked = false, hasPurchaseHistory = false)
        )

        assertTrue(display.noHistory)
        assertFalse(display.showPrediction)
        assertFalse(display.showSnooze)
        assertTrue(display.showUntrackedBadge)
    }
}
