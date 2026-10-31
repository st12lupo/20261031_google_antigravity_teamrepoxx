package com.example.receiptreminder.di

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import receipt.normalize.DefaultProductNormalizer

class RealAppContainerTest {

    @Test
    fun appContainerHolder_defaultContainerIsNotNull() {
        val container = AppContainerHolder.container
        assertNotNull(container)
        assertNotNull(container.shoppingListRepository)
        assertNotNull(container.productRepository)
        assertNotNull(container.purchaseRepository)
        assertNotNull(container.storeRepository)
        assertNotNull(container.notificationSettingsRepository)
        assertNotNull(container.receiptScanner)
        assertNotNull(container.receiptReviewService)
        assertNotNull(container.geofenceManager)
    }

    @Test
    fun appContainerHolder_canBeAssignedNewContainer() {
        val original = AppContainerHolder.container
        val newContainer = DefaultAppContainer()
        AppContainerHolder.container = newContainer
        assertTrue(AppContainerHolder.container === newContainer)
        // 元に戻す
        AppContainerHolder.container = original
    }

    @Test
    fun defaultProductDictionary_containsEssentialItems() {
        val dictionary = DefaultProductNormalizer.defaultProductDictionary()
        assertTrue(dictionary.isNotEmpty())
        val names = dictionary.map { it.normalizedName }
        assertTrue(names.contains("トイレットペーパー"))
        assertTrue(names.contains("ティッシュペーパー"))
        assertTrue(names.contains("食器用洗剤"))
    }
}
