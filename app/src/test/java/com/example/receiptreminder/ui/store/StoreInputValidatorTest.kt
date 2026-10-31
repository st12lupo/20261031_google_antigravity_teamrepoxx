package com.example.receiptreminder.ui.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalTime

class StoreInputValidatorTest {

    @Test
    fun name_rules() {
        assertNull(StoreInputValidator.validateName("テスト店"))
        assertEquals(InputError.NameEmpty, StoreInputValidator.validateName(""))
        assertEquals(InputError.NameEmpty, StoreInputValidator.validateName("  　".trim()))
        assertNull(StoreInputValidator.validateName("あ".repeat(StoreLimits.MAX_NAME_LENGTH)))
        assertEquals(
            InputError.NameTooLong,
            StoreInputValidator.validateName("あ".repeat(StoreLimits.MAX_NAME_LENGTH + 1))
        )
    }

    @Test
    fun radius_rules() {
        assertNull(StoreInputValidator.validateRadius("200"))
        assertNull(StoreInputValidator.validateRadius(" 100 "))
        assertNull(StoreInputValidator.validateRadius("1000"))
        assertEquals(InputError.RadiusOutOfRange, StoreInputValidator.validateRadius("99"))
        assertEquals(InputError.RadiusOutOfRange, StoreInputValidator.validateRadius("1001"))
        assertEquals(InputError.RadiusOutOfRange, StoreInputValidator.validateRadius("-200"))
        assertEquals(InputError.RadiusNotNumber, StoreInputValidator.validateRadius(""))
        assertEquals(InputError.RadiusNotNumber, StoreInputValidator.validateRadius("2百"))
        assertEquals(InputError.RadiusNotNumber, StoreInputValidator.validateRadius("20.5"))
        assertEquals(InputError.RadiusNotNumber, StoreInputValidator.validateRadius("99999999999"))
    }

    @Test
    fun parseTime_acceptsOnlyHourMinuteFormat() {
        assertEquals(LocalTime.of(8, 0), StoreInputValidator.parseTime("8:00"))
        assertEquals(LocalTime.of(21, 5), StoreInputValidator.parseTime(" 21:05 "))
        assertEquals(LocalTime.of(0, 0), StoreInputValidator.parseTime("00:00"))
        assertNull(StoreInputValidator.parseTime("24:00"))
        assertNull(StoreInputValidator.parseTime("8:60"))
        assertNull(StoreInputValidator.parseTime("8"))
        assertNull(StoreInputValidator.parseTime("8:5"))
        assertNull(StoreInputValidator.parseTime("abc"))
        assertNull(StoreInputValidator.parseTime(""))
    }

    @Test
    fun timeRange_requiresStartBeforeEnd() {
        assertNull(StoreInputValidator.validateTimeRange("8:00", "21:00"))
        assertNull(StoreInputValidator.validateTimeRange("8:00", "8:01"))
        assertEquals(InputError.TimeOrder, StoreInputValidator.validateTimeRange("8:00", "8:00"))
        assertEquals(InputError.TimeOrder, StoreInputValidator.validateTimeRange("21:00", "8:00"))
        assertEquals(InputError.TimeFormat, StoreInputValidator.validateTimeRange("x", "8:00"))
        assertEquals(InputError.TimeFormat, StoreInputValidator.validateTimeRange("8:00", "25:00"))
    }
}
