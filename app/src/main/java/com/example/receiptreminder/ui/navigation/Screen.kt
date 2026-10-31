package com.example.receiptreminder.ui.navigation

sealed class Screen(val route: String, val title: String) {
    object ShoppingList : Screen("shopping_list", "リスト")
    object ProductDetail : Screen("product_detail/{productId}", "商品詳細") {
        fun createRoute(productId: String = "sample-id") = "product_detail/$productId"
    }
    object Stores : Screen("stores", "店舗")
    object ReceiptScanReview : Screen("receipt_review", "レシート確認")
    object NotificationExplainer : Screen("notification_explainer", "通知と権限")
}

sealed class BottomNavItem(val screen: Screen, val iconName: String) {
    object ShoppingList : BottomNavItem(Screen.ShoppingList, "List")
    object ProductList : BottomNavItem(Screen.ProductDetail, "Detail")
    object Stores : BottomNavItem(Screen.Stores, "Store")
}
