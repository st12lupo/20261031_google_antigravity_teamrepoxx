package com.example.receiptreminder.di

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AppContainerTest {

    private lateinit var appContainer: AppContainer

    @Before
    fun setUp() {
        appContainer = DefaultAppContainer()
    }

    @Test
    fun appContainer_providesAllRequiredDependencies() = runTest {
        assertNotNull(appContainer.shoppingListRepository)
        assertNotNull(appContainer.productRepository)
        assertNotNull(appContainer.purchaseRepository)
        assertNotNull(appContainer.receiptScanner)
        assertNotNull(appContainer.receiptReviewService)
        assertNotNull(appContainer.storeRepository)
        assertNotNull(appContainer.notificationSettingsRepository)
        assertNotNull(appContainer.geofenceManager)

        // リポジトリ間の結合確認（買い物リストの初期データ取得）
        val items = appContainer.shoppingListRepository.getShoppingItemsStream().first()
        assertEquals(5, items.size)

        // スキャン機能の動作確認
        val scanResult = appContainer.receiptScanner.scanReceipt(listOf("content://receipt/1"))
        assertTrue(scanResult.isSuccess)
        assertEquals("みどりマート 中央店", scanResult.getOrNull()?.storeName)

        // 店舗一覧の動作確認
        val stores = appContainer.storeRepository.getStoresStream().first()
        assertEquals(3, stores.size)
    }

    @Test
    fun appContainer_clearAllData_wipesDataAcrossRepositories() = runTest {
        // 店舗リポジトリから全件削除を実行（D-1）
        appContainer.storeRepository.clearAllData()

        val stores = appContainer.storeRepository.getStoresStream().first()
        assertTrue(stores.isEmpty())

        val shoppingItems = appContainer.shoppingListRepository.getShoppingItemsStream().first()
        assertTrue(shoppingItems.isEmpty())

        val products = appContainer.productRepository.getProductsStream().first()
        assertTrue(products.isEmpty())
    }
}
