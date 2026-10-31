package com.example.receiptreminder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.receiptreminder.ui.navigation.AppNavigation
import com.example.receiptreminder.ui.theme.ReceiptReminderTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ReceiptReminderTheme {
                AppNavigation()
            }
        }
    }
}
