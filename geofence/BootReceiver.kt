package geofence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.receiptreminder.ReceiptReminderApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 端末の再起動後とアプリの更新後に、店舗の監視を再開する(N-6)。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!shouldRestore(intent.action)) return
        val container = (context.applicationContext as? ReceiptReminderApplication)?.container ?: return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                // goAsync の猶予内で数回やり直す。直らなければ WorkManager に引き継ぐ
                val restored = withTimeoutOrNull(8_000L) {
                    retryWithBackoff(RETRY_DELAYS_MS) { container.geofenceManager.recheckAllGeofences() }
                }
                // 権限がないときも false になるが、Worker は権限なしなら何もせず終わる
                if (restored != true) GeofenceResyncWorker.enqueue(context)
            } catch (_: Exception) {
                // 次のアプリ起動時(onStart)にも再登録される
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        /** 失敗時の待ち時間。合計で goAsync の猶予(約 10 秒)に収まる */
        private val RETRY_DELAYS_MS = listOf(500L, 1_500L, 3_000L)

        fun shouldRestore(action: String?): Boolean =
            action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED
    }
}
