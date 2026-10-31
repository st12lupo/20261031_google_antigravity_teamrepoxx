package geofence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {

    /**
     * 端末の再起動後 (ACTION_BOOT_COMPLETED) にジオフェンスを再登録する (要件 G-3)。
     * 実際の店舗データ取得と再登録は、Repository または WorkManager を介して実行する。
     */
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // 再起動後の初期化処理トリガー
        }
    }
}
