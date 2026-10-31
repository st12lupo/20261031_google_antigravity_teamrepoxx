package receipt.scan

import org.junit.Assert.assertEquals
import org.junit.Test

class OcrLineLayoutTest {

    @Test
    fun nameAndPriceInSeparateBlocksAreJoined() {
        val lines = listOf(
            OcrLine("198", 700, 102, 142),
            OcrLine("476", 700, 202, 242),
            OcrLine("ｼｮｯｷｾﾝｻﾞｲ", 40, 100, 140),
            OcrLine("ｷﾞｭｳﾆｭｳ", 40, 200, 240)
        )
        assertEquals(listOf("ｼｮｯｷｾﾝｻﾞｲ  198", "ｷﾞｭｳﾆｭｳ  476"), joinLinesByRow(lines))
    }

    @Test
    fun slightTiltStaysOnSameRowButNextRowDoesNot() {
        val lines = listOf(
            OcrLine("A", 40, 100, 140),
            OcrLine("100", 700, 112, 152),
            OcrLine("B", 40, 150, 190)
        )
        assertEquals(listOf("A  100", "B"), joinLinesByRow(lines))
    }

    @Test
    fun emptyInputGivesEmptyOutput() {
        assertEquals(emptyList<String>(), joinLinesByRow(emptyList()))
    }
}
