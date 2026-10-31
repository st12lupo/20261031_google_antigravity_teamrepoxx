package com.example.receiptreminder.fake

import com.example.receiptreminder.domain.model.NotificationTimeRange
import com.example.receiptreminder.domain.repository.NotificationSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeNotificationSettingsRepository(
    initialRange: NotificationTimeRange = NotificationTimeRange(8, 0, 21, 0)
) : NotificationSettingsRepository {

    private val _timeRangeFlow = MutableStateFlow(initialRange)

    override fun getNotificationTimeRangeStream(): Flow<NotificationTimeRange> {
        return _timeRangeFlow.asStateFlow()
    }

    override suspend fun setNotificationTimeRange(range: NotificationTimeRange) {
        _timeRangeFlow.value = range
    }
}
