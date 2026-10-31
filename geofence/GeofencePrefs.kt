package geofence

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.LocationManager
import android.provider.Settings
import androidx.core.location.LocationManagerCompat

/** SharedPreferences に同期状態を保存する(バックアップ対象外にしてある) */
class PrefsSyncStateStore(private val prefs: SharedPreferences) : SyncStateStore {
    override fun get(): String? = prefs.getString(KEY, null)
    override fun set(value: String?) {
        // commit: 直後にプロセスが終わっても残す
        prefs.edit().apply { if (value == null) remove(KEY) else putString(KEY, value) }.commit()
    }

    companion object {
        private const val KEY = "synced_signature"
        fun of(context: Context) = PrefsSyncStateStore(geofencePrefs(context))
    }
}

class PrefsRegisteredIdStore(private val prefs: SharedPreferences) : RegisteredIdStore {
    override fun get(): Set<String>? = prefs.getStringSet(KEY, null)?.toSet()
    override fun set(ids: Set<String>) {
        prefs.edit().putStringSet(KEY, ids).commit()
    }

    companion object {
        private const val KEY = "registered_ids"
        fun of(context: Context) = PrefsRegisteredIdStore(geofencePrefs(context))
    }
}

private fun geofencePrefs(context: Context): SharedPreferences =
    context.applicationContext.getSharedPreferences("geofence_sync_state", Context.MODE_PRIVATE)

/** 権限・位置情報のオン/オフ・端末の起動回数・アプリの更新時刻から、再登録が必要になる変化を検知する印 */
fun geofenceEnvironment(context: Context): String {
    fun granted(permission: String) =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    val boot = runCatching {
        Settings.Global.getInt(context.contentResolver, "boot_count", -1)
    }.getOrDefault(-1)
    val updated = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
    }.getOrDefault(-1L)
    return buildGeofenceEnvironment(
        fine = granted(android.Manifest.permission.ACCESS_FINE_LOCATION),
        coarse = granted(android.Manifest.permission.ACCESS_COARSE_LOCATION),
        background = granted(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION),
        locationEnabled = isDeviceLocationEnabled(context),
        boot = boot,
        updated = updated
    )
}

/**
 * 端末の位置情報(設定のオン/オフ)が有効か。取得に失敗したときは true とみなす
 * (無効と誤判定して再登録を止めるより、登録を試みて失敗させるほうが安全)。
 */
fun isDeviceLocationEnabled(context: Context): Boolean = runCatching {
    val manager = context.applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    LocationManagerCompat.isLocationEnabled(manager)
}.getOrDefault(true)

/** 印の文字列を作る。位置情報のオン/オフが変わると印も変わるので、復帰後の onStart で再登録される */
fun buildGeofenceEnvironment(
    fine: Boolean,
    coarse: Boolean,
    background: Boolean,
    locationEnabled: Boolean,
    boot: Int,
    updated: Long
): String = "fine=$fine,coarse=$coarse,bg=$background,loc=$locationEnabled,boot=$boot,upd=$updated"
