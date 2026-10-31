package com.example.receiptreminder.domain.model

import java.time.LocalDate
import java.time.LocalTime

/**
 * 登録店舗モデル
 */
data class Store(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Int = 200,
    val notifyEnabled: Boolean = true,
    val lastNotifiedOn: LocalDate? = null
)

/**
 * アプリ全体の通知時間帯設定（既定: 8:00〜21:00）
 */
data class NotificationTimeRange(
    val startHour: Int = 8,
    val startMinute: Int = 0,
    val endHour: Int = 21,
    val endMinute: Int = 0
) {
    val displayText: String
        get() = "%d:%02d〜%d:%02d".format(startHour, startMinute, endHour, endMinute)

    fun isWithinRange(time: LocalTime): Boolean {
        val start = LocalTime.of(startHour, startMinute)
        val end = LocalTime.of(endHour, endMinute)
        return !time.isBefore(start) && !time.isAfter(end)
    }
}
