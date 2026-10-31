package com.example.receiptreminder.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.receiptreminder.ui.list.ShoppingListScreen
import com.example.receiptreminder.ui.product.ProductDetailScreen
import com.example.receiptreminder.ui.product.ProductListScreen
import com.example.receiptreminder.ui.review.ReceiptReviewScreen
import com.example.receiptreminder.ui.store.NotificationExplainerScreen
import com.example.receiptreminder.ui.store.StoresScreen
import com.example.receiptreminder.ui.theme.PrimaryGreen

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController()
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val bottomNavItems = listOf(
        Screen.ShoppingList,
        Screen.ProductList,
        Screen.Stores
    )

    // レシート確認画面や権限説明画面などではボトムバーを非表示にする
    val showBottomBar = currentRoute in listOf(
        Screen.ShoppingList.route,
        Screen.ProductList.route,
        Screen.ProductDetail.route,
        Screen.Stores.route
    )

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp
                ) {
                    bottomNavItems.forEach { screen ->
                        val selected = when (screen) {
                            Screen.ShoppingList -> currentRoute == Screen.ShoppingList.route
                            Screen.ProductList -> currentRoute == Screen.ProductList.route ||
                                currentRoute?.startsWith("product_detail") == true
                            Screen.Stores -> currentRoute == Screen.Stores.route
                            else -> false
                        }
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { },
                            label = {
                                Text(
                                    text = screen.title,
                                    fontSize = 14.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selected) PrimaryGreen else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = Color.Transparent
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.ShoppingList.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.ShoppingList.route) {
                ShoppingListScreen(
                    onNavigateToDetail = { productId ->
                        navController.navigate(Screen.ProductDetail.createRoute(productId))
                    },
                    onNavigateToScan = {
                        navController.navigate(Screen.ReceiptScanReview.route)
                    }
                )
            }

            composable(Screen.ProductList.route) {
                ProductListScreen(
                    onNavigateToDetail = { productId ->
                        navController.navigate(Screen.ProductDetail.createRoute(productId))
                    }
                )
            }

            composable(Screen.ProductDetail.route) { backStackEntry ->
                val productId = backStackEntry.arguments?.getString("productId")
                ProductDetailScreen(
                    productId = productId,
                    onBack = {
                        navController.popBackStack()
                    }
                )
            }

            composable(Screen.Stores.route) {
                StoresScreen(
                    onNavigateToExplainer = {
                        navController.navigate(Screen.NotificationExplainer.route)
                    }
                )
            }

            composable(Screen.ReceiptScanReview.route) {
                ReceiptReviewScreen(
                    onCancel = {
                        navController.popBackStack()
                    },
                    onSave = {
                        navController.popBackStack()
                    }
                )
            }

            composable(Screen.NotificationExplainer.route) {
                NotificationExplainerScreen(
                    onDismiss = {
                        navController.popBackStack()
                    },
                    onProceed = {
                        navController.popBackStack()
                    }
                )
            }
        }
    }
}
