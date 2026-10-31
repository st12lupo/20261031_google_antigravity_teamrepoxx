package com.example.receiptreminder.di

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 画面の回転などで Activity が作り直されてもデータを失わないよう、プロセスで 1 つだけ保持する
 * 仮実装から実装への置き換えは T-16 で行う
 */
object AppContainerHolder {
    @Volatile
    private var container: AppContainer? = null

    fun get(context: Context): AppContainer =
        container ?: synchronized(this) {
            container ?: DefaultAppContainer(context.applicationContext).also { container = it }
        }
}

/** 画面の ViewModel へ依存を渡すための CompositionLocal */
val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer が提供されていません")
}
