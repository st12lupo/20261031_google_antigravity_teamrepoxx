package receipt.scan

import com.example.receiptreminder.domain.model.ReceiptCandidate
import com.example.receiptreminder.domain.model.ReceiptItemCandidate
import com.example.receiptreminder.domain.repository.ReceiptScanner
import domain.receipt.ProductNormalizer
import domain.receipt.ReceiptParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.UUID
import kotlin.math.ceil
import domain.receipt.ReceiptScanner as OcrEngine

/**
 * 画像の文字認識、解析、名寄せをつなぎ、確認画面に渡す候補を作る。
 * 複数の画像は 1 枚のレシートのページとして順に連結する(R-7)。
 */
class OcrReceiptCandidateScanner(
    private val ocr: OcrEngine,
    private val parser: ReceiptParser,
    private val normalizer: ProductNormalizer,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ReceiptScanner {

    override suspend fun scanReceipt(imageUris: List<String>): Result<ReceiptCandidate> =
        try {
            val lines = withContext(ioDispatcher) { imageUris.flatMap { ocr.recognizeText(it) } }
            val parsed = parser.parse(lines)
            if (parsed.items.isEmpty()) {
                Result.failure(IllegalStateException("商品を読み取れませんでした"))
            } else {
                Result.success(
                    ReceiptCandidate(
                        id = UUID.randomUUID().toString(),
                        storeName = parsed.storeName,
                        purchasedOn = LocalDate.ofEpochDay(parsed.purchasedOnEpochDay),
                        totalAmount = parsed.totalAmount,
                        items = parsed.items.map { item ->
                            val normalized = normalizer.normalize(item.rawName)
                            ReceiptItemCandidate(
                                id = UUID.randomUUID().toString(),
                                rawOcrText = item.rawName,
                                normalizedName = normalized.normalizedName,
                                quantity = ceil(item.quantity).toInt().coerceAtLeast(1),
                                price = item.price,
                                isTracked = normalized.isMatchedInDictionary
                            )
                        }
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }

    override fun deleteImages(imageUris: List<String>) {
        imageUris.forEach(ocr::deleteImage)
    }
}
