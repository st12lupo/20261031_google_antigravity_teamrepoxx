package com.example.receiptreminder.data.bridge

import android.content.SharedPreferences
import com.example.receiptreminder.domain.model.NotificationTimeRange
import com.example.receiptreminder.domain.repository.NotificationSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class RealNotificationSettingsRepository(
    private val sharedPreferences: SharedPreferences? = null,
    initialRange: NotificationTimeRange = NotificationTimeRange(8, 0, 21, 0)
) : NotificationSettingsRepository {

    private val _timeRangeFlow: MutableStateFlow<NotificationTimeRange>

    init {
        val range = if (sharedPreferences != null) {
            val startHour = sharedPreferences.getInt(KEY_START_HOUR, initialRange.startHour)
            val startMinute = sharedPreferences.getInt(KEY_START_MINUTE, initialRange.startMinute)
            val endHour = sharedPreferences.getInt(KEY_END_HOUR, initialRange.endHour)
            val endMinute = sharedPreferences.getInt(KEY_END_MINUTE, initialRange.endMinute)
            NotificationTimeRange(startHour, startMinute, endHour, endMinute)
        } else {
            initialRange
        }
        _timeRangeFlow = MutableStateFlow(range)
    }

    override fun getNotificationTimeRangeStream(): Flow<NotificationTimeRange> {
        return _timeRangeFlow.asStateFlow()
    }

    override suspend fun setNotificationTimeRange(range: NotificationTimeRange) {
        sharedPreferences?.edit()?.apply {
            putInt(KEY_START_HOUR, range.startHour)
            putInt(KEY_START_MINUTE, range.startMinute)
            putInt(KEY_END_HOUR, range.endHour)
            putInt(KEY_END_MINUTE, range.endMinute)
            apply()
        }
        _timeRangeFlow.value = range
    }

    companion object {
        const val KEY_START_HOUR = "pref_notification_start_hour"
        const val KEY_START_MINUTE = "pref_notification_start_minute"
        const val KEY_END_HOUR = "pref_notification_end_hour"
        const val KEY_END_MINUTE = "pref_notification_end_minute"
    }
}
