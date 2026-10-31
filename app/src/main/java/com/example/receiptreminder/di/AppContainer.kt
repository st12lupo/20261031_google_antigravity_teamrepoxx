package com.example.receiptreminder.di

import com.example.receiptreminder.domain.repository.*
import com.example.receiptreminder.fake.*

/**
 * アプリ全体の依存性注入（DI）コンテナ
 * 外部DIライブラリを入れず、手動で依存を組み立てる（TASKS.md T-01d 設計方針）
 */
interface AppContainer {
    val shoppingListRepository: ShoppingListRepository
    val productRepository: ProductRepository
    val purchaseRepository: PurchaseRepository
    val receiptScanner: ReceiptScanner
    val receiptReviewService: ReceiptReviewService
    val storeRepository: StoreRepository
    val notificationSettingsRepository: NotificationSettingsRepository
    val geofenceManager: GeofenceManager
}

/**
 * 仮実装（Fake）で配線された既定の AppContainer
 * 画面開発（第2波 T-09〜T-12）やエミュレータ・ViewModel でそのまま動作可能
 */
class DefaultAppContainer : AppContainer {

    override val shoppingListRepository: FakeShoppingListRepository by lazy {
        FakeShoppingListRepository()
    }

    override val productRepository: FakeProductRepository by lazy {
        FakeProductRepository()
    }

    override val purchaseRepository: FakePurchaseRepository by lazy {
        FakePurchaseRepository()
    }

    override val receiptScanner: FakeReceiptScanner by lazy {
        FakeReceiptScanner()
    }

    override val receiptReviewService: FakeReceiptReviewService by lazy {
        FakeReceiptReviewService(
            shoppingListRepository = shoppingListRepository,
            productRepository = productRepository
        )
    }

    override val storeRepository: FakeStoreRepository by lazy {
        FakeStoreRepository(
            onClearAllData = {
                shoppingListRepository.resetData()
                productRepository.resetData()
                purchaseRepository.resetData()
            }
        )
    }

    override val notificationSettingsRepository: FakeNotificationSettingsRepository by lazy {
        FakeNotificationSettingsRepository()
    }

    override val geofenceManager: FakeGeofenceManager by lazy {
        FakeGeofenceManager(
            storeRepository = storeRepository,
            shoppingListRepository = shoppingListRepository,
            notificationSettingsRepository = notificationSettingsRepository
        )
    }
}
