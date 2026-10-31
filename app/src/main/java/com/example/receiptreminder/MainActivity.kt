package com.example.receiptreminder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import com.example.receiptreminder.di.AppContainerHolder
import com.example.receiptreminder.di.LocalAppContainer
import com.example.receiptreminder.ui.navigation.AppNavigation
import com.example.receiptreminder.ui.theme.ReceiptReminderTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CompositionLocalProvider(LocalAppContainer provides AppContainerHolder.get(this)) {
                ReceiptReminderTheme {
                    AppNavigation()
                }
            }
        }
    }
}
