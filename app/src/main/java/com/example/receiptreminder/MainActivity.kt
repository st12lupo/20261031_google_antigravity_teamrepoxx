package com.example.receiptreminder

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import com.example.receiptreminder.di.AppContainerHolder
import com.example.receiptreminder.di.LocalAppContainer
import com.example.receiptreminder.ui.navigation.AppNavigation
import com.example.receiptreminder.ui.theme.ReceiptReminderTheme

class MainActivity : ComponentActivity() {
    /** 通知タップで既存の画面に届いた回数。増えたら買い物リストへ移る(S-7) */
    private var openShoppingListRequests by mutableIntStateOf(0)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (OpenShoppingList.matches(intent.action)) openShoppingListRequests++
    }

    override fun onStart() {
        super.onStart()
        // 権限や店舗が変わった場合に備えて監視を同期する。変化がなければ登録し直さない(N-6)
        lifecycleScope.launch {
            try {
                AppContainerHolder.container.geofenceManager.syncIfChanged()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // 権限がないときなど。通知以外の機能は使えるので何もしない(N-5)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CompositionLocalProvider(LocalAppContainer provides AppContainerHolder.container) {
                ReceiptReminderTheme {
                    AppNavigation(openShoppingListRequests = openShoppingListRequests)
                }
            }
        }
    }
}
