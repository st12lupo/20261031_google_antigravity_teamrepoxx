package com.example.receiptreminder.data.bridge

import com.example.receiptreminder.domain.model.ReceiptCandidate
import com.example.receiptreminder.domain.model.ReceiptItemCandidate
import data.DefaultReminderRepository
import data.FakeProductDao
import data.FakePurchaseDao
import data.FakeReceiptDao
import data.FakeStoreDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import receipt.normalize.DefaultProductNormalizer
import receipt.normalize.ProductDictionaryLoader
import java.io.File
import java.time.LocalDate

/**
 * 予測対象の既定(P-3, P-4, F-6)、別名の取り消し(P-2)、入力名を削らない保存(R-4)を、
 * スキャナー・確認サービス・リポジトリ(Room 層は Fake DAO)を通して確かめる。データはすべて架空。
 */
class TrackingDefaultFlowTest {

    private val dictionary = ProductDictionaryLoader.parse(
        listOf("src/main/assets/products.json", "app/src/main/assets/products.json")
            .map { File(it) }.first { it.exists() }.readText(Charsets.UTF_8)
    )

    private lateinit var repository: DefaultReminderRepository
    private val date = LocalDate.of(2026, 10, 31)

    @Before
    fun setup() {
        repository = DefaultReminderRepository(FakeProductDao(), FakePurchaseDao(), FakeReceiptDao(), FakeStoreDao())
    }

    /** アプリの起動ごとに作り直される、名寄せ器と各サービスの組 */
    private inner class App {
        val normalizer = DefaultProductNormalizer(initialDictionary = dictionary)
        val aliasStore = LearnedAliasStore(repository, normalizer)
        val reviewService = RealReceiptReviewService(
            reminderRepository = repository,
            normalizer = normalizer,
            aliasStore = aliasStore
        )
        val scanner = RealReceiptScanner(normalizer = normalizer, reviewService = reviewService)
        val shoppingList = RealShoppingListRepository(
            reminderRepository = repository,
            todayProvider = { date },
            normalizer = normalizer,
            aliasStore = aliasStore
        )
    }

    private fun lines(vararg items: String) =
        listOf("テストマート", "2026年10月31日 12:34") + items + listOf("合計 1000")

    private fun candidate(raw: String, name: String, tracked: Boolean = true) = ReceiptCandidate(
        id = "r", storeName = "テストマート", purchasedOn = date, totalAmount = 300,
        items = listOf(ReceiptItemCandidate("i1", raw, name, 1, 300, isTracked = tracked))
    )

    private suspend fun savedProduct(name: String) = repository.getAllProducts().single { it.name == name }

    // ---- P-3 / P-4 ----

    @Test
    fun `P-3 - unknown product saved as OFF stays OFF on the second receipt and is not predicted`() = runTest {
        val app = App()
        val first = app.scanner.scanLines(lines("ヨガマット 880")).getOrThrow()
        assertFalse(first.items.single().isTracked)
        assertTrue(app.reviewService.saveReceipt(first).isSuccess)

        // 2 回目: 登録済みだが予測対象ではないので、既定は OFF のまま
        val second = app.scanner.scanLines(lines("ヨガマット 880")).getOrThrow()
        assertFalse(second.items.single().isTracked)
        assertEquals(false, app.reviewService.suggestTracked("ヨガマット"))
        assertTrue(app.reviewService.saveReceipt(second).isSuccess)

        val product = savedProduct("ヨガマット")
        assertFalse(product.isTracked)
        assertFalse(product.stoppedByUser)
        assertEquals(2, repository.getPurchasesForProduct(product.id).size)
        assertTrue(app.shoppingList.getShoppingItemsStream().first().isEmpty())
    }

    @Test
    fun `P-4 - turning OFF on the detail screen is kept on the next receipt and sets no stopped mark`() = runTest {
        val app = App()
        app.shoppingList.addManualPurchase("ヨガマット", date.minusDays(30), 1, 880, null)
        val id = savedProduct("ヨガマット").id
        RealProductRepository(repository, todayProvider = { date }).updateTrackedStatus(id.toString(), false)
        assertFalse(repository.getProductById(id)!!.stoppedByUser)

        val scanned = app.scanner.scanLines(lines("ヨガマット 880")).getOrThrow()
        assertFalse(scanned.items.single().isTracked)
        assertEquals(false, app.reviewService.suggestTracked("ヨガマット"))
        assertTrue(app.reviewService.saveReceipt(scanned).isSuccess)
        assertFalse(repository.getProductById(id)!!.isTracked)
    }

    @Test
    fun `P-4 - a dictionary product turned OFF on the detail screen stays OFF on the next receipt`() = runTest {
        val app = App()
        app.shoppingList.addManualPurchase("牛乳", date.minusDays(5), 1, 200, null)
        val id = savedProduct("牛乳").id
        repository.setTracked(id, false)

        val scanned = app.scanner.scanLines(lines("牛乳 1L 220")).getOrThrow()
        assertFalse(scanned.items.single().isTracked)
        assertEquals(false, app.reviewService.suggestTracked("牛乳"))
    }

    // ---- F-6 ----

    @Test
    fun `F-6 - only stopTracking sets the mark, and turning the detail switch ON clears it`() = runTest {
        val app = App()
        app.shoppingList.addManualPurchase("ヨガマット", date.minusDays(30), 1, 880, null)
        val id = savedProduct("ヨガマット").id

        app.shoppingList.stopTracking(id.toString())
        assertTrue(repository.getProductById(id)!!.stoppedByUser)
        assertFalse(repository.getProductById(id)!!.isTracked)

        // 詳細画面で ON -> OFF にした結果は P-4 の OFF。印は残らず、次のレシートでも戻らない
        repository.setTracked(id, true)
        assertFalse(repository.getProductById(id)!!.stoppedByUser)
        repository.setTracked(id, false)
        assertFalse(repository.getProductById(id)!!.stoppedByUser)
        val scanned = app.scanner.scanLines(lines("ヨガマット 880")).getOrThrow()
        assertFalse(scanned.items.single().isTracked)
    }

    @Test
    fun `F-6 - the mark is cleared when a purchase returns the product to tracking, so a later detail OFF sticks`() = runTest {
        val app = App()
        app.shoppingList.addManualPurchase("ヨガマット", date.minusDays(30), 1, 880, null)
        val id = savedProduct("ヨガマット").id
        app.shoppingList.stopTracking(id.toString())

        val scanned = app.scanner.scanLines(lines("ヨガマット 880")).getOrThrow()
        assertTrue(scanned.items.single().isTracked)
        assertTrue(app.reviewService.saveReceipt(scanned).isSuccess)
        assertTrue(repository.getProductById(id)!!.isTracked)
        assertFalse(repository.getProductById(id)!!.stoppedByUser)

        // 戻ったあとに詳細画面で OFF にした分は、次のレシートで戻らない
        repository.setTracked(id, false)
        assertFalse(app.scanner.scanLines(lines("ヨガマット 880")).getOrThrow().items.single().isTracked)
    }

    @Test
    fun `F-6 - a manual purchase of a stopped product returns it to tracking and clears the mark`() = runTest {
        val app = App()
        app.shoppingList.addManualPurchase("ヨガマット", date.minusDays(30), 1, 880, null)
        val id = savedProduct("ヨガマット").id
        app.shoppingList.stopTracking(id.toString())

        app.shoppingList.addManualPurchase("ヨガマット", date, 1, 880, null)

        val product = repository.getProductById(id)!!
        assertTrue(product.isTracked)
        assertFalse(product.stoppedByUser)
    }

    @Test
    fun `F-6 - explicit OFF at review for a stopped product stays OFF and clears the mark`() = runTest {
        val app = App()
        app.shoppingList.addManualPurchase("ヨガマット", date.minusDays(30), 1, 880, null)
        val id = savedProduct("ヨガマット").id
        app.shoppingList.stopTracking(id.toString())

        val scanned = app.scanner.scanLines(lines("ヨガマット 880")).getOrThrow()
        val switchedOff = scanned.copy(items = scanned.items.map { it.copy(isTracked = false) })
        assertTrue(app.reviewService.saveReceipt(switchedOff).isSuccess)

        val product = repository.getProductById(id)!!
        assertFalse(product.isTracked)
        assertFalse(product.stoppedByUser)
        // 印が消えているので、次のレシートの既定も対象外
        assertFalse(app.scanner.scanLines(lines("ヨガマット 880")).getOrThrow().items.single().isTracked)
    }

    @Test
    fun `F-6 - a stopped dictionary product returns to tracking on the next scan`() = runTest {
        val app = App()
        app.shoppingList.addManualPurchase("牛乳", date.minusDays(5), 1, 200, null)
        val id = savedProduct("牛乳").id
        app.shoppingList.stopTracking(id.toString())

        val scanned = app.scanner.scanLines(lines("牛乳 1L 220")).getOrThrow()
        assertTrue(scanned.items.single().isTracked)
        assertTrue(app.reviewService.saveReceipt(scanned).isSuccess)
        assertTrue(repository.getProductById(id)!!.isTracked)
    }

    @Test
    fun `F-6 - renaming at review gives the same default as the scan for a stopped product`() = runTest {
        val app = App()
        app.shoppingList.addManualPurchase("ヨガマット", date.minusDays(30), 1, 880, null)
        val id = savedProduct("ヨガマット").id
        app.shoppingList.stopTracking(id.toString())

        assertEquals(true, app.reviewService.suggestTracked("ヨガマット"))
        assertEquals(true, app.reviewService.suggestTracked("ヨガマット 1個"))
        // 登録のない名前は辞書にもないので対象外
        assertEquals(false, app.reviewService.suggestTracked("別のマット"))
        // スキャンの既定と同じ答え
        assertEquals(
            app.scanner.scanLines(lines("ヨガマット 880")).getOrThrow().items.single().isTracked,
            app.reviewService.suggestTracked("ヨガマット")
        )
    }

    @Test
    fun `F-6 - renaming at review to a name whose alias target was stopped suggests tracking`() = runTest {
        val first = App()
        val corrected = ReceiptCandidate(
            "r1", "テストマート", date.minusDays(10), 880,
            items = listOf(ReceiptItemCandidate("i1", "ﾏｯﾄXYZ", "ヨガマット", 1, 880, isTracked = true))
        )
        assertTrue(first.reviewService.saveReceipt(corrected).isSuccess)
        val id = savedProduct("ヨガマット").id
        first.shoppingList.stopTracking(id.toString())

        val second = App()
        // 別名の元の表記を入力しても、別名の先(止めた商品)として扱う
        assertEquals(true, second.reviewService.suggestTracked("ﾏｯﾄXYZ"))
        // 同じ別名先でも P-4 で OFF にした商品は対象外
        repository.setTracked(id, true)
        repository.setTracked(id, false)
        assertEquals(false, second.reviewService.suggestTracked("ﾏｯﾄXYZ"))
    }

    // ---- P-2: 別名の取り消し ----

    @Test
    fun `P-2 - a learned alias can be cancelled by typing the raw name back, in memory and in the database`() = runTest {
        val first = App()
        assertTrue(first.reviewService.saveReceipt(candidate("特売牛乳", "牛乳")).isSuccess)
        assertEquals(1, repository.getAllAliases().size)

        // 次回、別名で「牛乳」と表示される
        val second = App()
        val rescanned = second.scanner.scanLines(lines("特売牛乳 300")).getOrThrow()
        assertEquals("牛乳", rescanned.items.single().normalizedName)

        // 表示された「牛乳」を「特売牛乳」に直して保存 -> 取り消し。入力名で保存される
        val undone = rescanned.copy(items = rescanned.items.map { it.copy(normalizedName = "特売牛乳") })
        assertTrue(second.reviewService.saveReceipt(undone).isSuccess)

        assertEquals(1, repository.getPurchasesForProduct(savedProduct("特売牛乳").id).size)
        assertTrue(repository.getAllAliases().isEmpty())
        assertEquals("特売牛乳", second.normalizer.normalize("特売牛乳").normalizedName)

        // 再起動しても別名は復活しない
        val third = App()
        val again = third.scanner.scanLines(lines("特売牛乳 300")).getOrThrow()
        assertEquals("特売牛乳", again.items.single().normalizedName)
    }

    @Test
    fun `P-2 - a different correction after a cancel learns a new alias`() = runTest {
        val app = App()
        assertTrue(app.reviewService.saveReceipt(candidate("特売牛乳", "牛乳")).isSuccess)
        assertTrue(app.reviewService.saveReceipt(candidate("特売牛乳", "特売牛乳")).isSuccess)
        assertTrue(repository.getAllAliases().isEmpty())

        assertTrue(app.reviewService.saveReceipt(candidate("特売牛乳", "卵")).isSuccess)
        assertEquals(1, repository.getAllAliases().size)
        val restarted = App()
        restarted.aliasStore.ensureLoaded()
        assertEquals("卵", restarted.normalizer.normalize("特売牛乳").normalizedName)
    }

    @Test
    fun `P-2 - cancelling removes legacy alias rows saved with the raw text`() = runTest {
        val milk = repository.getOrCreateProduct("牛乳", "定番食品")
        repository.saveAlias("特売牛乳", milk.id)
        repository.saveAlias("トクバイギュウニュウ", milk.id)

        val app = App()
        assertTrue(app.reviewService.saveReceipt(candidate("特売牛乳", "特売牛乳")).isSuccess)

        assertEquals(listOf("トクバイギュウニュウ"), repository.getAllAliases().map { it.rawText })
    }

    // ---- R-4: 入力名の一部を削らない ----

    @Test
    fun `R-4 - names typed at review keep a count that is part of the name`() = runTest {
        val app = App()
        assertTrue(app.reviewService.saveReceipt(candidate("ｿｯｸｽ", "5本指ソックス")).isSuccess)
        assertTrue(app.reviewService.saveReceipt(candidate("ﾊﾞｰ", "1本満足バー")).isSuccess)

        assertEquals(setOf("5本指ソックス", "1本満足バー"), repository.getAllProducts().map { it.name }.toSet())
    }

    @Test
    fun `R-4 - unit counts at the end of a name are still removed when saving`() = runTest {
        val app = App()
        assertTrue(app.reviewService.saveReceipt(candidate("ﾖｶﾞ", "ヨガマット 3個入")).isSuccess)
        assertTrue(app.reviewService.saveReceipt(candidate("ｺｰﾗ", "架空コーラゼロ500P")).isSuccess)

        assertEquals(setOf("ヨガマット", "架空コーラゼロ"), repository.getAllProducts().map { it.name }.toSet())
    }

    // ---- P-2: 読み取った表記そのものへの修正 ----

    @Test
    fun `P-2 - correcting to the raw text itself is learned when auto matching picked another product`() = runTest {
        val first = App()
        // 「液体洗剤」の部分一致で「洗濯洗剤」と出る表記
        val scanned = first.scanner.scanLines(lines("液体洗剤ホワイト 300")).getOrThrow()
        assertEquals("洗濯洗剤", scanned.items.single().normalizedName)

        val fixed = scanned.copy(items = scanned.items.map { it.copy(normalizedName = "液体洗剤ホワイト") })
        assertTrue(first.reviewService.saveReceipt(fixed).isSuccess)
        assertEquals(1, repository.getPurchasesForProduct(savedProduct("液体洗剤ホワイト").id).size)
        assertEquals(1, repository.getAllAliases().size)

        // 再起動しても、次回は入力した名前になる
        val second = App()
        val again = second.scanner.scanLines(lines("液体洗剤ホワイト 300")).getOrThrow()
        assertEquals("液体洗剤ホワイト", again.items.single().normalizedName)
    }

    @Test
    fun `P-2 - cancelling an alias on a raw text that also matches the dictionary learns the typed name`() = runTest {
        val first = App()
        assertTrue(first.reviewService.saveReceipt(candidate("液体洗剤ホワイト", "ホワイト洗剤")).isSuccess)
        assertEquals("ホワイト洗剤", App().scanner.scanLines(lines("液体洗剤ホワイト 300")).getOrThrow().items.single().normalizedName)

        // 別名で出た名前を、読み取った表記に直す(取り消し)。自動の結果(洗濯洗剤)とは違うので入力した名前を覚える
        val second = App()
        assertTrue(second.reviewService.saveReceipt(candidate("液体洗剤ホワイト", "液体洗剤ホワイト")).isSuccess)
        assertEquals(1, repository.getAllAliases().size)
        val product = savedProduct("液体洗剤ホワイト")
        assertEquals(product.id, repository.getAllAliases().single().productId)

        val third = App()
        assertEquals("液体洗剤ホワイト", third.scanner.scanLines(lines("液体洗剤ホワイト 300")).getOrThrow().items.single().normalizedName)
    }

    @Test
    fun `P-2 - cancelling an alias on a raw text that matches the dictionary by suffix makes the typed name stick`() = runTest {
        val rice = repository.getOrCreateProduct("米", "定番食品")
        repository.saveAlias("国産米", rice.id)

        val app = App()
        assertEquals("米", app.scanner.scanLines(lines("国産米 300")).getOrThrow().items.single().normalizedName)
        assertTrue(app.reviewService.saveReceipt(candidate("国産米", "国産米")).isSuccess)

        // 「米」で終わる語は辞書に部分一致するので、取り消したままでは次回また「米」になる。入力した名前を覚える
        val saved = savedProduct("国産米")
        assertEquals(listOf(saved.id), repository.getAllAliases().map { it.productId })
        val next = App()
        assertEquals("国産米", next.scanner.scanLines(lines("国産米 300")).getOrThrow().items.single().normalizedName)
    }

    @Test
    fun `P-2 - no meaningless alias is stored when the name is what auto matching already gives`() = runTest {
        val app = App()
        assertTrue(app.reviewService.saveReceipt(candidate("特売牛乳", "特売牛乳")).isSuccess)
        assertTrue(app.reviewService.saveReceipt(candidate("ﾏｯﾄ", "ﾏｯﾄ")).isSuccess)
        assertTrue(repository.getAllAliases().isEmpty())
    }

    // ---- 詳細画面の購入追加(B-1)と P-4 ----

    @Test
    fun `B-1 - adding a purchase by product id does not follow an alias to another product`() = runTest {
        val yoga = repository.getOrCreateProduct("ヨガマット")
        val yogaL = repository.getOrCreateProduct("ヨガマットL")
        repository.saveAlias("ヨガマット", yogaL.id)

        val app = App()
        app.shoppingList.addPurchaseForProduct(yoga.id.toString(), date, 2)

        assertEquals(1, repository.getPurchasesForProduct(yoga.id).size)
        assertEquals(2.0, repository.getPurchasesForProduct(yoga.id).single().quantity, 0.0)
        assertTrue(repository.getPurchasesForProduct(yogaL.id).isEmpty())

        // 買い物リストの手動追加(名前)は今までどおり別名で解決する
        app.shoppingList.addManualPurchase("ヨガマット", date, 1)
        assertEquals(1, repository.getPurchasesForProduct(yogaL.id).size)
        assertEquals(1, repository.getPurchasesForProduct(yoga.id).size)
    }

    @Test
    fun `P-4 - a manual purchase keeps a product that the user turned OFF, off`() = runTest {
        val product = repository.getOrCreateProduct("ヨガマット")
        repository.setTracked(product.id, false)
        val app = App()

        app.shoppingList.addPurchaseForProduct(product.id.toString(), date, 1)
        assertFalse(repository.getProductById(product.id)!!.isTracked)

        app.shoppingList.addManualPurchase("ヨガマット", date, 1)
        assertFalse(repository.getProductById(product.id)!!.isTracked)

        app.shoppingList.markAsPurchased(product.id.toString(), 1)
        assertFalse(repository.getProductById(product.id)!!.isTracked)
        assertEquals(3, repository.getPurchasesForProduct(product.id).size)
    }

    @Test
    fun `P-4 - a manual purchase brings back a product stopped with the stop button`() = runTest {
        val a = repository.getOrCreateProduct("ヨガマット")
        val b = repository.getOrCreateProduct("タオル")
        val app = App()
        app.shoppingList.stopTracking(a.id.toString())
        app.shoppingList.stopTracking(b.id.toString())

        app.shoppingList.addManualPurchase("ヨガマット", date, 1)
        app.shoppingList.addPurchaseForProduct(b.id.toString(), date, 1)

        assertTrue(repository.getProductById(a.id)!!.isTracked)
        assertFalse(repository.getProductById(a.id)!!.stoppedByUser)
        assertTrue(repository.getProductById(b.id)!!.isTracked)
        assertFalse(repository.getProductById(b.id)!!.stoppedByUser)
    }
}
