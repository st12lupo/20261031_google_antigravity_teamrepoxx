package com.example.receiptreminder.ui.store

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/** 店舗の通知に必要な権限の状態 */
data class StorePermissionStatus(
    val foregroundLocation: Boolean,
    val backgroundLocation: Boolean,
    val notification: Boolean
) {
    /** 店舗の通知を使える状態か */
    val storeNotificationsAvailable: Boolean
        get() = foregroundLocation && backgroundLocation && notification
}

object StorePermissionPlanner {

    /** 最初に要求する権限(前景位置情報。Android 13 以上では通知権限も同時に) */
    fun firstRequest(status: StorePermissionStatus, sdkInt: Int): List<String> = buildList {
        if (!status.foregroundLocation) {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (sdkInt >= Build.VERSION_CODES.TIRAMISU && !status.notification) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /** 前景位置情報の許可後に、バックグラウンド位置情報を要求するか(Android 10 以上) */
    fun needsBackgroundRequest(status: StorePermissionStatus, sdkInt: Int): Boolean =
        status.foregroundLocation && !status.backgroundLocation &&
            sdkInt >= Build.VERSION_CODES.Q

    /** 登録の前に、理由の説明を出すか */
    fun shouldExplain(status: StorePermissionStatus, alreadyAsked: Boolean): Boolean =
        !status.foregroundLocation || (!status.storeNotificationsAvailable && !alreadyAsked)

    fun read(context: Context, sdkInt: Int = Build.VERSION.SDK_INT): StorePermissionStatus {
        fun granted(permission: String) =
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        return StorePermissionStatus(
            foregroundLocation = granted(Manifest.permission.ACCESS_FINE_LOCATION),
            backgroundLocation = sdkInt < Build.VERSION_CODES.Q ||
                granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
            notification = sdkInt < Build.VERSION_CODES.TIRAMISU ||
                granted(Manifest.permission.POST_NOTIFICATIONS)
        )
    }
}
