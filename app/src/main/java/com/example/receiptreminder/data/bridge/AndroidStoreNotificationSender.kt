package com.example.receiptreminder.data.bridge

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.receiptreminder.MainActivity
import com.example.receiptreminder.OpenShoppingList
import com.example.receiptreminder.domain.notify.StoreNotificationSender

/**
 * 店舗の通知を端末に出す。店舗ごとに通知 ID を分け、タップで買い物リストを開く(S-7)。
 * 通知が許可されていないときは出さずに false を返す。
 */
class AndroidStoreNotificationSender(private val context: Context) : StoreNotificationSender {

    override fun send(storeId: String, title: String, message: String): Boolean {
        return try {
            if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return false

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "買い物リマインダー",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = "店舗の近くでお買い忘れを防ぐ通知" }
                manager.createNotificationChannel(channel)
            }

            val id = notificationIdFor(storeId)
            // タスクは作り直さない(CLEAR_TASK だと編集中の入力が失われる)。
            // 起動中なら onNewIntent に届き、AppNavigation が買い物リストへ移る。
            // レシート確認画面の編集中は閉じずに上へ積むので、戻れば入力が残っている。
            // それ以外の画面は買い物リストまで戻す。起動していなければ開始画面(買い物リスト)で開く
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                action = OpenShoppingList.ACTION
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val contentIntent = PendingIntent.getActivity(
                context,
                id,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                
                .setContentIntent(contentIntent)
                .build()
            manager.notify(id, notification)
            true
        } catch (_: SecurityException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        const val CHANNEL_ID = "shopping_reminder_channel"
        private const val NOTIFICATION_ID_BASE = 2001

        /** 店舗ごとに異なる通知 ID(同じ店舗なら同じ ID) */
        fun notificationIdFor(storeId: String): Int =
            NOTIFICATION_ID_BASE + (storeId.toLongOrNull()?.rem(1_000_000)?.toInt()
                ?: (storeId.hashCode() and 0xFFFFF))
    }
}
