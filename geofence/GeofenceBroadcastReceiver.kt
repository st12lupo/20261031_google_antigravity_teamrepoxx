package geofence

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import java.time.LocalTime

class GeofenceBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val geofencingEvent = GeofencingEvent.fromIntent(intent) ?: return
        if (geofencingEvent.hasError()) {
            return
        }

        val transition = geofencingEvent.geofenceTransition
        if (transition == Geofence.GEOFENCE_TRANSITION_ENTER) {
            // PLAN.md: 通知する時間帯 (既定 8:00〜21:00、時間外に進入しても通知しない)
            val now = LocalTime.now()
            if (!DefaultGeofenceManager.isNotificationTimeAllowed(now)) {
                return
            }

            val triggeringGeofences = geofencingEvent.triggeringGeofences ?: emptyList()
            if (triggeringGeofences.isEmpty()) return

            sendStoreNotification(context)
        }
    }

    private fun sendStoreNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 通知チャンネルの登録 (Android 8.0+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "買い物リマインダー",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "店舗近くに進入した際のお買い忘れ防止通知"
            }
            notificationManager.createNotificationChannel(channel)
        }

        // 通知タップでアプリ(買い物リスト)を開く PendingIntent (要件 G-4)
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = launchIntent?.let {
            PendingIntent.getActivity(
                context,
                NOTIFICATION_PENDING_INTENT_REQUEST_CODE,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("近くに店舗があります")
            .setContentText("買い忘れの品がないか確認しましょう")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .apply {
                if (contentPendingIntent != null) {
                    setContentIntent(contentPendingIntent)
                }
            }
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val CHANNEL_ID = "shopping_reminder_channel"
        const val NOTIFICATION_ID = 2001
        const val NOTIFICATION_PENDING_INTENT_REQUEST_CODE = 2002
    }
}
