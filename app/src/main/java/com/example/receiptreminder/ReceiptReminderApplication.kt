package com.example.receiptreminder

import android.app.Application
import com.example.receiptreminder.data.bridge.CapturedImageSweeper
import com.example.receiptreminder.di.AppContainer
import com.example.receiptreminder.di.AppContainerHolder
import com.example.receiptreminder.di.RealAppContainer
import data.AppDatabase

/**
 * レシート消耗品リマインダー アプリケーションクラス。
 * プロセス起動時にデータベースおよび実 DI コンテナ (RealAppContainer) を初期化する。
 */
class ReceiptReminderApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()

        val database = AppDatabase.getInstance(this)
        val realContainer = RealAppContainer(
            context = this,
            database = database
        )
        container = realContainer
        AppContainerHolder.container = realContainer

        // 前回のプロセス終了や CLEAR_TASK で残ったレシート画像を消す(R-6)。取り込み中の画像は消さない
        val cacheDir = cacheDir
        Thread({ CapturedImageSweeper.forCacheDir(cacheDir).sweep() }, "captured-image-sweep").start()
    }
}
