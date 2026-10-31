package com.example.receiptreminder

/**
 * 通知タップで買い物リストを開く(S-7)ための取り決め。
 * タスクを作り直さず(CLEAR_TASK を使わず)、既存の画面があれば onNewIntent で買い物リストへ移る。
 */
object OpenShoppingList {
    const val ACTION = "com.example.receiptreminder.action.OPEN_SHOPPING_LIST"

    fun matches(action: String?): Boolean = action == ACTION
}
