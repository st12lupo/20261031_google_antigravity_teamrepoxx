package com.example.receiptreminder.data.bridge

import com.example.receiptreminder.domain.model.ReceiptCandidate
import com.example.receiptreminder.domain.model.ReceiptItemCandidate
import domain.model.Product
import domain.model.Purchase
import domain.model.PurchaseSource
import domain.model.Receipt
import domain.model.ReceiptItemData
import domain.model.Store
import domain.receipt.ReceiptScanner as OcrReceiptScanner
import domain.repository.ReminderRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import receipt.normalize.DefaultProductNormalizer
import receipt.parser.DefaultReceiptParser
import java.time.LocalDate

class RealReceiptReviewServiceTest {

    private lateinit var fakeRepository: FakeTestReminderRepository
    private lateinit var fakeOcrScanner: FakeTestOcrScanner
    private lateinit var normalizer: DefaultProductNormalizer
    private lateinit var parser: DefaultReceiptParser
    private lateinit var reviewService: RealReceiptReviewService
    private lateinit var scanner: RealReceiptScanner

    @Before
    fun setup() {
        fakeRepository = FakeTestReminderRepository()
        fakeOcrScanner = FakeTestOcrScanner()
        normalizer = DefaultProductNormalizer()
        parser = DefaultReceiptParser()

        reviewService = RealReceiptReviewService(
            reminderRepository = fakeRepository,
            ocrScanner = fakeOcrScanner,
            normalizer = normalizer
        )

        scanner = RealReceiptScanner(
            ocrScanner = fakeOcrScanner,
            parser = parser,
            normalizer = normalizer,
            reviewService = reviewService
        )
    }

    @Test
    fun `checkDuplicate returns true when duplicate receipt exists in repository`() = runTest {
        val date = LocalDate.of(2026, 10, 31)
        fakeRepository.saveReceiptAndPurchases(
            storeName = "みどりマート 中央店",
            purchasedOnEpochDay = date.toEpochDay(),
            totalAmount = 1500,
            items = emptyList()
        )

        // 一致する場合
        val isDup = reviewService.checkDuplicate("みどりマート 中央店", "2026-10-31", 1500)
        assertTrue(isDup)

        // スラッシュ区切り日付でも検出できること
        val isDupSlash = reviewService.checkDuplicate("みどりマート 中央店", "2026/10/31", 1500)
        assertTrue(isDupSlash)

        // 店名が異なる場合
        val notDupStore = reviewService.checkDuplicate("別店舗", "2026-10-31", 1500)
        assertFalse(notDupStore)

        // 金額が異なる場合
        val notDupAmount = reviewService.checkDuplicate("みどりマート 中央店", "2026-10-31", 2000)
        assertFalse(notDupAmount)

        // 不正な日付形式
        val invalidDate = reviewService.checkDuplicate("みどりマート 中央店", "invalid-date", 1500)
        assertFalse(invalidDate)
    }

    @Test
    fun `saveReceipt saves tracked and untracked items to repository and clears pending receipt and deletes image (R-6)`() = runTest {
        val date = LocalDate.of(2026, 10, 30)
        val dummyImageUri = "file:///tmp/receipt_image_001.jpg"

        val candidate = ReceiptCandidate(
            id = "test-receipt-1",
            storeName = "テストスーパー",
            purchasedOn = date,
            totalAmount = 880,
            items = listOf(
                ReceiptItemCandidate(
                    id = "item-1",
                    rawOcrText = "ｷﾞｭｳﾆｭｳ 1L",
                    normalizedName = "牛乳",
                    quantity = 2,
                    price = 400,
                    isTracked = true
                ),
                ReceiptItemCandidate(
                    id = "item-2",
                    rawOcrText = "雑誌",
                    normalizedName = "雑誌",
                    quantity = 1,
                    price = 480,
                    isTracked = false // 追跡対象外
                )
            )
        )

        reviewService.setPendingReceipt(candidate, dummyImageUri)
        assertEquals(candidate, reviewService.getPendingReceipt())
        assertEquals(dummyImageUri, reviewService.getPendingImageUri())

        val saveResult = reviewService.saveReceipt(candidate)
        assertTrue(saveResult.isSuccess)

        // リポジトリに正しく保存されたことを確認
        assertEquals(1, fakeRepository.savedReceipts.size)
        val savedReceipt = fakeRepository.savedReceipts.first()
        assertEquals("テストスーパー", savedReceipt.storeName)
        assertEquals(date.toEpochDay(), savedReceipt.purchasedOnEpochDay)
        assertEquals(880, savedReceipt.totalAmount)

        // 予測対象外の商品も購入履歴として保存され、isTracked の指定が引き継がれること(P-2, P-4)
        assertEquals(2, fakeRepository.savedItems.size)
        val savedItem = fakeRepository.savedItems.first { it.normalizedName == "牛乳" }
        assertEquals(2.0, savedItem.quantity, 0.0)
        assertEquals(400, savedItem.price)
        assertTrue(savedItem.isTracked)
        val untrackedItem = fakeRepository.savedItems.first { it.normalizedName == "雑誌" }
        assertEquals(480, untrackedItem.price)
        assertFalse(untrackedItem.isTracked)

        // R-6: 元画像が削除されたこと
        assertTrue(fakeOcrScanner.deletedImageUris.contains(dummyImageUri))

        // pending状態がクリアされたこと
        assertNull(reviewService.getPendingReceipt())
        assertNull(reviewService.getPendingImageUri())
    }

    @Test
    fun `saveReceipt registers alias when rawOcrText is edited by user (P-2)`() = runTest {
        val date = LocalDate.of(2026, 10, 31)
        val candidate = ReceiptCandidate(
            id = "test-receipt-alias",
            storeName = "ドラッグストア",
            purchasedOn = date,
            totalAmount = 500,
            items = listOf(
                ReceiptItemCandidate(
                    id = "item-edit",
                    rawOcrText = "ﾄｸｿｳｾﾝｻﾞｲ",
                    normalizedName = "洗濯洗剤",
                    quantity = 1,
                    price = 500,
                    isTracked = true
                )
            )
        )

        reviewService.setPendingReceipt(candidate, null)
        val result = reviewService.saveReceipt(candidate)
        assertTrue(result.isSuccess)

        // 次回以降同じ文字列が来たら別名として自動適用されること(P-2)
        val normalized = normalizer.normalize("ﾄｸｿｳｾﾝｻﾞｲ")
        assertEquals("洗濯洗剤", normalized.normalizedName)
    }

    @Test
    fun `discardReceipt clears pending receipt and deletes image (R-6)`() = runTest {
        val dummyUri = "file:///tmp/receipt_image_002.jpg"
        val candidate = ReceiptCandidate(
            id = "discard-test",
            storeName = "破棄テスト店",
            purchasedOn = LocalDate.now(),
            totalAmount = 300,
            items = emptyList()
        )

        reviewService.setPendingReceipt(candidate, dummyUri)
        assertEquals(candidate, reviewService.getPendingReceipt())

        reviewService.discardReceipt()

        // 画像が削除され、pendingがnullになっていること
        assertTrue(fakeOcrScanner.deletedImageUris.contains(dummyUri))
        assertNull(reviewService.getPendingReceipt())
        assertNull(reviewService.getPendingImageUri())
    }

    @Test
    fun `RealReceiptScanner scanLines successfully creates candidate and sets pending receipt`() = runTest {
        val ocrLines = listOf(
            "みどりスーパー 本店",
            "2026年10月31日 12:34",
            "牛乳 1L 220",
            "洗濯用洗剤 500ml 350",
            "合計 570"
        )

        val result = scanner.scanLines(ocrLines)
        assertTrue(result.isSuccess)

        val candidate = result.getOrNull()
        assertNotNull(candidate)
        assertEquals("みどりスーパー 本店", candidate?.storeName)
        assertEquals(LocalDate.of(2026, 10, 31), candidate?.purchasedOn)
        assertEquals(570, candidate?.totalAmount)
        assertEquals(2, candidate?.items?.size)

        // 牛乳と洗剤は辞書にマッチするため isTracked = true
        val item1 = candidate?.items?.find { it.normalizedName == "牛乳" }
        assertNotNull(item1)
        assertTrue(item1?.isTracked ?: false)
        assertEquals(220, item1?.price)

        val item2 = candidate?.items?.find { it.normalizedName == "洗濯用洗剤" }
        assertNotNull(item2)
        assertTrue(item2?.isTracked ?: false)
        assertEquals(350, item2?.price)

        // reviewService 側に自動で pendingReceipt が登録されていること
        assertEquals(candidate, reviewService.getPendingReceipt())
    }

    @Test
    fun `RealReceiptScanner sets isDuplicateWarning when duplicate exists in repository`() = runTest {
        // 事前にDBにレシートを登録
        val date = LocalDate.of(2026, 10, 31)
        fakeRepository.saveReceiptAndPurchases(
            storeName = "みどりスーパー 本店",
            purchasedOnEpochDay = date.toEpochDay(),
            totalAmount = 570,
            items = emptyList()
        )

        val ocrLines = listOf(
            "みどりスーパー 本店",
            "2026年10月31日",
            "牛乳 220",
            "合計 570"
        )

        val result = scanner.scanLines(ocrLines)
        assertTrue(result.isSuccess)
        val candidate = result.getOrNull()
        assertNotNull(candidate)
        assertTrue("Duplicate warning should be true", candidate!!.isDuplicateWarning)
    }

    @Test
    fun `RealReceiptScanner scanImage uses ocrScanner and registers pendingImageUri`() = runTest {
        val testUri = "file:///tmp/camera_receipt.jpg"
        fakeOcrScanner.textMap[testUri] = listOf(
            "スキャナーテスト店",
            "2026/10/25",
            "シャンプー 450",
            "合計 450"
        )

        val result = scanner.scanImage(testUri)
        assertTrue(result.isSuccess)
        val candidate = result.getOrNull()
        assertNotNull(candidate)
        assertEquals("スキャナーテスト店", candidate?.storeName)

        // reviewService に imageUri が保持されていること
        assertEquals(testUri, reviewService.getPendingImageUri())

        // 保存後に画像が削除されること
        reviewService.saveReceipt(candidate!!)
        assertTrue(fakeOcrScanner.deletedImageUris.contains(testUri))
    }

    @Test
    fun `RealReceiptScanner returns failure when no image or lines provided`() = runTest {
        val emptyScanner = RealReceiptScanner()
        val result = emptyScanner.scanReceipt(emptyList())
        assertTrue(result.isFailure)
    }

    @Test
    fun `RealReceiptScanner joins pages and fails when no items are read`() = runTest {
        fakeOcrScanner.textMap["page1"] = listOf("スキャナーテスト店", "2026/10/25", "シャンプー 450")
        fakeOcrScanner.textMap["page2"] = listOf("牛乳 220", "合計 670")
        fakeOcrScanner.textMap["unreadable"] = listOf("読めない文字")

        val candidate = scanner.scanReceipt(listOf("page1", "page2")).getOrThrow()
        assertEquals(2, candidate.items.size)
        assertEquals(670, candidate.totalAmount)

        assertTrue(scanner.scanReceipt(listOf("unreadable")).isFailure)
    }

    @Test
    fun `selected images are not deleted when the receipt is saved or discarded`() = runTest {
        val pickedUri = "content://media/picked/1"
        fakeOcrScanner.textMap[pickedUri] = listOf("スキャナーテスト店", "2026/10/25", "シャンプー 450", "合計 450")

        val candidate = scanner.scanReceipt(listOf(pickedUri)).getOrThrow()
        assertNull(reviewService.getPendingImageUri())
        reviewService.saveReceipt(candidate)
        assertTrue(fakeOcrScanner.deletedImageUris.isEmpty())

        scanner.scanReceipt(listOf(pickedUri)).getOrThrow()
        reviewService.discardReceipt()
        assertTrue(fakeOcrScanner.deletedImageUris.isEmpty())
    }

    @Test
    fun `saveReceipt notifies the list so it shows the saved items`() = runTest {
        val trigger = DataUpdateTrigger()
        val service = RealReceiptReviewService(reminderRepository = fakeRepository, trigger = trigger)
        val before = trigger.version.value

        service.saveReceipt(
            ReceiptCandidate(
                id = "r1",
                storeName = "スキャナーテスト店",
                purchasedOn = LocalDate.of(2026, 10, 25),
                totalAmount = 450,
                items = listOf(ReceiptItemCandidate("i1", "シャンプー", "シャンプー", 1, 450))
            )
        )

        assertTrue(trigger.version.value > before)
    }

    @Test
    fun `deleteImages removes every captured page`() {
        scanner.deleteImages(listOf("file:///scan/1.jpg", "file:///scan/2.jpg"))
        assertEquals(listOf("file:///scan/1.jpg", "file:///scan/2.jpg"), fakeOcrScanner.deletedImageUris)
    }
}

/**
 * テスト用の ReminderRepository 実装
 */
private class FakeTestReminderRepository : ReminderRepository {

    val savedReceipts = mutableListOf<Receipt>()
    val savedItems = mutableListOf<ReceiptItemData>()
    private var nextReceiptId = 1L
    private var nextProductId = 1L
    private val products = mutableListOf<Product>()

    override suspend fun getAllProducts(): List<Product> = products

    override suspend fun getProductById(id: Long): Product? = products.find { it.id == id }

    override suspend fun getOrCreateProduct(name: String, category: String): Product {
        return products.find { it.name == name } ?: run {
            val p = Product(id = nextProductId++, name = name, category = category, isTracked = true)
            products.add(p)
            p
        }
    }

    override suspend fun setTracked(productId: Long, isTracked: Boolean) {
        val idx = products.indexOfFirst { it.id == productId }
        if (idx >= 0) {
            products[idx] = products[idx].copy(isTracked = isTracked)
        }
    }

    override suspend fun stopTracking(productId: Long) {
        val idx = products.indexOfFirst { it.id == productId }
        if (idx >= 0) products[idx] = products[idx].copy(isTracked = false, stoppedByUser = true)
    }

    override suspend fun snoozeProduct(productId: Long, extraDays: Long) {}

    override suspend fun deleteProduct(id: Long) {
        products.removeAll { it.id == id }
    }

    override suspend fun getAllPurchases(): List<Purchase> = emptyList()

    override suspend fun getPurchasesForProduct(productId: Long): List<Purchase> = emptyList()

    override suspend fun recordManualPurchase(
        productId: Long,
        purchasedOnEpochDay: Long,
        quantity: Double,
        price: Int,
        source: PurchaseSource
    ): Long = 1L

    override suspend fun updatePurchase(purchase: Purchase) {}

    override suspend fun deletePurchase(purchaseId: Long) {}

    override suspend fun deleteAllPurchases() {}

    override suspend fun getAllReceipts(): List<Receipt> = savedReceipts

    override suspend fun findDuplicateReceipt(
        storeName: String,
        purchasedOnEpochDay: Long,
        totalAmount: Int
    ): Receipt? {
        return savedReceipts.find {
            it.storeName == storeName &&
                    it.purchasedOnEpochDay == purchasedOnEpochDay &&
                    it.totalAmount == totalAmount
        }
    }

    override suspend fun saveReceiptAndPurchases(
        storeName: String,
        purchasedOnEpochDay: Long,
        totalAmount: Int,
        items: List<ReceiptItemData>
    ): Long {
        val receiptId = nextReceiptId++
        savedReceipts.add(
            Receipt(
                id = receiptId,
                storeName = storeName,
                purchasedOnEpochDay = purchasedOnEpochDay,
                totalAmount = totalAmount
            )
        )
        savedItems.addAll(items)
        return receiptId
    }

    override suspend fun deleteReceipt(receiptId: Long) {
        savedReceipts.removeAll { it.id == receiptId }
    }

    override suspend fun getAllStores(): List<Store> = emptyList()

    override suspend fun getActiveStores(): List<Store> = emptyList()

    override suspend fun insertStore(store: Store): Long = 1L

    override suspend fun updateStore(store: Store) {}

    override suspend fun deleteStore(storeId: Long) {}

    override suspend fun updateLastNotified(storeId: Long, epochDay: Long) {}

    override suspend fun deleteAllStores() {}
}

/**
 * テスト用の OCRスキャナー 実装
 */
private class FakeTestOcrScanner : OcrReceiptScanner {

    val textMap = mutableMapOf<String, List<String>>()
    val deletedImageUris = mutableListOf<String>()

    override suspend fun recognizeText(imageUriString: String): List<String> {
        return textMap[imageUriString] ?: emptyList()
    }

    override fun deleteImage(imageUriString: String) {
        deletedImageUris.add(imageUriString)
    }
}
