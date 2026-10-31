package com.example.receiptreminder

import android.app.Application
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
    }
}
