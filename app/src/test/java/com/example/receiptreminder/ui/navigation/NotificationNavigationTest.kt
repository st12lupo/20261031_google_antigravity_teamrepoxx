package com.example.receiptreminder.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationNavigationTest {
    @Test
    fun reviewScreen_stacksListOnTop_insteadOfPopping() {
        assertEquals(ShoppingListNavMode.PushOnTop, shoppingListNavMode(Screen.ReceiptScanReview.route))
    }

    @Test
    fun listScreen_doesNothing() {
        assertEquals(ShoppingListNavMode.AlreadyThere, shoppingListNavMode(Screen.ShoppingList.route))
    }

    @Test
    fun otherScreens_popToList_asBefore() {
        for (route in listOf(
            Screen.ProductList.route,
            Screen.ProductDetail.route,
            Screen.Stores.route,
            Screen.NotificationExplainer.route,
            null
        )) {
            assertEquals(route, ShoppingListNavMode.PopToList, shoppingListNavMode(route))
        }
    }

    @Test
    fun bottomBar_hiddenOnListStackedOverReview() {
        assertFalse(shouldShowBottomBar(Screen.ShoppingList.route, Screen.ReceiptScanReview.route))
        assertFalse(shouldShowBottomBar(Screen.ReceiptScanReview.route, Screen.ShoppingList.route))
    }

    @Test
    fun bottomBar_unchangedOtherwise() {
        assertTrue(shouldShowBottomBar(Screen.ShoppingList.route, null))
        assertTrue(shouldShowBottomBar(Screen.Stores.route, Screen.ShoppingList.route))
        assertTrue(shouldShowBottomBar(Screen.ProductDetail.route, Screen.ProductList.route))
        assertFalse(shouldShowBottomBar(Screen.NotificationExplainer.route, Screen.Stores.route))
    }
}
