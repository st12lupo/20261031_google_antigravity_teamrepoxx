package com.example.receiptreminder.ui.product

import com.example.receiptreminder.domain.model.Product
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 商品一覧の行の状態表示(B-4, P-5)。詳細画面と同じ言い方にそろえ、予測のない行に目盛りを出さない */
class ProductListDisplayTest {

    @Test
    fun `tracked product without history says it cannot be predicted and shows no gauge`() {
        val product = Product(id = "1", name = "履歴なし", isTracked = true, hasPurchaseHistory = false)

        assertEquals(ProductListStatusLabel.NO_HISTORY, productListStatusLabel(product))
        assertFalse(productDetailDisplay(product).showPrediction)
    }

    @Test
    fun `untracked product without history also says no history, not untracked`() {
        val product = Product(id = "1", name = "x", isTracked = false, hasPurchaseHistory = false)

        assertEquals(ProductListStatusLabel.NO_HISTORY, productListStatusLabel(product))
        assertFalse(productDetailDisplay(product).showPrediction)
    }

    @Test
    fun `untracked product with history says untracked and shows no gauge`() {
        val product = Product(id = "1", name = "x", isTracked = false)

        assertEquals(ProductListStatusLabel.UNTRACKED, productListStatusLabel(product))
        assertFalse(productDetailDisplay(product).showPrediction)
    }

    @Test
    fun `tracked product with history shows the prediction and the gauge`() {
        val product = Product(id = "1", name = "牛乳")

        assertEquals(ProductListStatusLabel.PREDICTION, productListStatusLabel(product))
        assertTrue(productDetailDisplay(product).showPrediction)
    }
}
