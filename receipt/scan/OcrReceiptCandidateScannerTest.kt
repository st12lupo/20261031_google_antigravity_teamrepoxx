package receipt.scan

import domain.receipt.ReceiptScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import receipt.normalize.DefaultProductNormalizer
import receipt.parser.DefaultReceiptParser

class OcrReceiptCandidateScannerTest {

    private class StubOcr(
        private val pages: Map<String, List<String>> = emptyMap(),
        private val error: Exception? = null
    ) : ReceiptScanner {
        val deleted = mutableListOf<String>()
        override suspend fun recognizeText(imageUriString: String): List<String> {
            error?.let { throw it }
            return pages.getValue(imageUriString)
        }

        override fun deleteImage(imageUriString: String) {
            deleted += imageUriString
        }
    }

    private fun scanner(ocr: StubOcr) = OcrReceiptCandidateScanner(
        ocr = ocr,
        parser = DefaultReceiptParser(),
        normalizer = DefaultProductNormalizer(),
        ioDispatcher = Dispatchers.Unconfined
    )

    @Test
    fun pagesAreJoinedIntoOneCandidate() = runTest {
        val ocr = StubOcr(
            mapOf(
                "page1" to listOf("テスト商店", "2026/10/31", "ｼｮｯｷｾﾝｻﾞｲ    198"),
                "page2" to listOf("ｷﾞｭｳﾆｭｳ 1L 2ｺ X238   476", "合計    674")
            )
        )
        val candidate = scanner(ocr).scanReceipt(listOf("page1", "page2")).getOrThrow()

        assertEquals("テスト商店", candidate.storeName)
        assertEquals("2026-10-31", candidate.purchasedOn.toString())
        assertEquals(674, candidate.totalAmount)
        assertEquals(listOf(198, 476), candidate.items.map { it.price })
        assertEquals(listOf(1, 2), candidate.items.map { it.quantity })
        assertEquals("ｼｮｯｷｾﾝｻﾞｲ", candidate.items[0].rawOcrText.trim())
    }

    @Test
    fun noItemsIsFailure() = runTest {
        val result = scanner(StubOcr(mapOf("page1" to listOf("読めない文字")))).scanReceipt(listOf("page1"))
        assertTrue(result.isFailure)
    }

    @Test
    fun ocrErrorIsFailure() = runTest {
        val result = scanner(StubOcr(error = RuntimeException("x"))).scanReceipt(listOf("page1"))
        assertFalse(result.isSuccess)
    }

    @Test
    fun deleteImagesDeletesEveryPage() {
        val ocr = StubOcr()
        scanner(ocr).deleteImages(listOf("page1", "page2"))
        assertEquals(listOf("page1", "page2"), ocr.deleted)
    }
}
