package com.example.receiptreminder.ui.navigation

/** 通知タップで買い物リストを開くときの、バックスタックの扱い(S-7) */
enum class ShoppingListNavMode {
    /** すでに買い物リストが表示されている。何もしない */
    AlreadyThere,

    /** 買い物リストまで戻す(上の画面は閉じる)。通常の画面向け */
    PopToList,

    /** 今の画面を残したまま、その上に買い物リストを積む。戻れば元の画面に戻れる */
    PushOnTop
}

/**
 * 現在の画面から、通知タップ時の遷移方法を決める。
 * レシート確認画面は入力や撮影画像を持つので、popUpTo で閉じると確認なしに消える。
 * そのため閉じずに上へ積む。それ以外の画面は従来どおり買い物リストまで戻す。
 */
fun shoppingListNavMode(currentRoute: String?): ShoppingListNavMode = when (currentRoute) {
    Screen.ShoppingList.route -> ShoppingListNavMode.AlreadyThere
    Screen.ReceiptScanReview.route -> ShoppingListNavMode.PushOnTop
    else -> ShoppingListNavMode.PopToList
}

/**
 * ボトムバーを出すか。確認画面の上に積んだ買い物リストでは隠す。
 * タブを押すと popUpTo で下の確認画面が閉じられ、編集中の内容が消えてしまうため。
 */
fun shouldShowBottomBar(currentRoute: String?, previousRoute: String?): Boolean {
    if (previousRoute == Screen.ReceiptScanReview.route) return false
    return currentRoute in listOf(
        Screen.ShoppingList.route,
        Screen.ProductList.route,
        Screen.ProductDetail.route,
        Screen.Stores.route
    )
}
