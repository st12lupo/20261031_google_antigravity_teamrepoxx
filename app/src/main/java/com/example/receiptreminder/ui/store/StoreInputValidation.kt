package com.example.receiptreminder.ui.store

import java.time.LocalTime

/** 店舗の上限など、画面で使う定数 */
object StoreLimits {
    const val MAX_STORES = 100
    const val DEFAULT_RADIUS_METERS = 200
    const val MIN_RADIUS_METERS = 100
    const val MAX_RADIUS_METERS = 1000
    const val MAX_NAME_LENGTH = 50
}

/** 入力の検証エラー */
enum class InputError {
    NameEmpty,
    NameTooLong,
    RadiusNotNumber,
    RadiusOutOfRange,
    TimeFormat,
    TimeOrder
}

/** 入力の検証。画面と ViewModel の両方で同じ規則を使う */
object StoreInputValidator {

    private val TIME_PATTERN = Regex("(\\d{1,2}):(\\d{2})")

    fun validateName(text: String): InputError? {
        val name = text.trim()
        return when {
            name.isEmpty() -> InputError.NameEmpty
            name.length > StoreLimits.MAX_NAME_LENGTH -> InputError.NameTooLong
            else -> null
        }
    }

    fun validateRadius(text: String): InputError? {
        val value = text.trim().toIntOrNull() ?: return InputError.RadiusNotNumber
        return if (value in StoreLimits.MIN_RADIUS_METERS..StoreLimits.MAX_RADIUS_METERS) {
            null
        } else {
            InputError.RadiusOutOfRange
        }
    }

    /** 8:00 や 08:30 の形式だけを受け付ける */
    fun parseTime(text: String): LocalTime? {
        val match = TIME_PATTERN.matchEntire(text.trim()) ?: return null
        val hour = match.groupValues[1].toInt()
        val minute = match.groupValues[2].toInt()
        if (hour !in 0..23 || minute !in 0..59) return null
        return LocalTime.of(hour, minute)
    }

    fun validateTimeRange(startText: String, endText: String): InputError? {
        val start = parseTime(startText) ?: return InputError.TimeFormat
        val end = parseTime(endText) ?: return InputError.TimeFormat
        return if (start.isBefore(end)) null else InputError.TimeOrder
    }
}
