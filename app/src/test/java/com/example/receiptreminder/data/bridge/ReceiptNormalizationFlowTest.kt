package com.example.receiptreminder.data.bridge

import com.example.receiptreminder.domain.model.ReceiptCandidate
import com.example.receiptreminder.domain.model.ReceiptItemCandidate
import data.DefaultReminderRepository
import data.FakeProductDao
import data.FakePurchaseDao
import data.FakeReceiptDao
import data.FakeStoreDao
import domain.model.PurchaseSource
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
 * 名寄せ、予測対象の切り替え、別名の永続化、手動追加の名寄せを、
 * 実際の products.json と DefaultReminderRepository(Room 層は Fake DAO)でつないで確かめる。
 */
class ReceiptNormalizationFlowTest {

    private val dictionary = ProductDictionaryLoader.parse(
        listOf("src/main/assets/products.json", "app/src/main/assets/products.json")
            .map { File(it) }.first { it.exists() }.readText(Charsets.UTF_8)
    )

    private lateinit var productDao: FakeProductDao
    private lateinit var repository: DefaultReminderRepository

    private val date = LocalDate.of(2026, 10, 31)

    @Before
    fun setup() {
        productDao = FakeProductDao()
        repository = DefaultReminderRepository(productDao, FakePurchaseDao(), FakeReceiptDao(), FakeStoreDao())
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

    @Test
    fun `unknown product is kept in history as an untracked product (P-2 P-4 P-5)`() = runTest {
        val app = App()
        val candidate = app.scanner.scanLines(lines("ヨガマット 880")).getOrThrow()
        val item = candidate.items.single()
        assertFalse(item.isTracked)

        assertTrue(app.reviewService.saveReceipt(candidate).isSuccess)

        val product = repository.getAllProducts().single { it.name == item.normalizedName }
        assertFalse(product.isTracked)
        val purchases = repository.getPurchasesForProduct(product.id)
        assertEquals(1, purchases.size)
        assertEquals(880, purchases[0].price)
        // 予測対象外なので買い物リストには出ない
        assertTrue(app.shoppingList.getShoppingItemsStream().first().isEmpty())
    }

    @Test
    fun `user can turn an unknown product into a prediction target at review (R-4 P-4)`() = runTest {
        val app = App()
        val candidate = app.scanner.scanLines(lines("ヨガマット 880")).getOrThrow()
        val toggled = candidate.copy(items = candidate.items.map { it.copy(isTracked = true) })

        assertTrue(app.reviewService.saveReceipt(toggled).isSuccess)

        assertTrue(repository.getAllProducts().single().isTracked)
        assertEquals(1, app.shoppingList.getShoppingItemsStream().first().size)
    }

    @Test
    fun `already tracked product outside the dictionary stays tracked on the next receipt`() = runTest {
        val app = App()
        app.shoppingList.addManualPurchase("ヨガマット", date.minusDays(30), 1, 880, null)
        assertTrue(repository.getAllProducts().single().isTracked)

        val candidate = app.scanner.scanLines(lines("ヨガマット 880")).getOrThrow()
        assertTrue(candidate.items.single().isTracked)
        assertEquals(true, app.reviewService.suggestTracked("ヨガマット"))
    }

    @Test
    fun `suggestTracked is true for dictionary names and false for unknown names`() = runTest {
        val app = App()
        assertEquals(true, app.reviewService.suggestTracked("牛乳"))
        assertEquals(true, app.reviewService.suggestTracked("ｷﾞｭｳﾆｭｳ"))
        assertEquals(false, app.reviewService.suggestTracked("ヨガマット"))
        assertEquals(false, app.reviewService.suggestTracked("  "))
    }

    @Test
    fun `correction is learned even for untracked items and survives an app restart (P-2)`() = runTest {
        val first = App()
        val candidate = ReceiptCandidate(
            id = "r1",
            storeName = "テストマート",
            purchasedOn = date,
            totalAmount = 300,
            items = listOf(
                ReceiptItemCandidate("i1", "ﾄｸｾﾝﾐﾙｸ", "牛乳", 1, 300, isTracked = false)
            )
        )
        assertTrue(first.reviewService.saveReceipt(candidate).isSuccess)

        // Room に保存されている
        val saved = repository.getAllAliases()
        assertEquals(1, saved.size)
        // Room のキーはメモリと同じ照合キー(全角カナに畳み込み済み)で保存される
        assertEquals("トクセンミルク", saved[0].rawText)
        assertEquals("牛乳", repository.getProductById(saved[0].productId)?.name)

        // 再起動(名寄せ器もメモリも新しくなる)しても、最初の取り込みで読み込まれて適用される
        val second = App()
        assertFalse(second.normalizer.normalize("ﾄｸｾﾝﾐﾙｸ").normalizedName == "牛乳")
        val rescanned = second.scanner.scanLines(lines("ﾄｸｾﾝﾐﾙｸ 300")).getOrThrow()
        assertEquals("牛乳", rescanned.items.single().normalizedName)
    }

    @Test
    fun `no alias is learned when the user keeps the automatic name`() = runTest {
        val app = App()
        val candidate = app.scanner.scanLines(lines("ｷﾞｭｳﾆｭｳ 1L 220")).getOrThrow()
        assertEquals("牛乳", candidate.items.single().normalizedName)

        assertTrue(app.reviewService.saveReceipt(candidate).isSuccess)
        assertTrue(repository.getAllAliases().isEmpty())
    }

    @Test
    fun `clearAllData forgets aliases in memory and in the database`() = runTest {
        val app = App()
        val candidate = ReceiptCandidate(
            "r1", "テストマート", date, 300,
            items = listOf(ReceiptItemCandidate("i1", "ﾄｸｾﾝﾐﾙｸ", "牛乳", 1, 300))
        )
        app.reviewService.saveReceipt(candidate)
        assertEquals("牛乳", app.normalizer.normalize("ﾄｸｾﾝﾐﾙｸ").normalizedName)

        val storeRepo = RealStoreRepository(
            reminderRepository = repository,
            onClearAllData = { app.aliasStore.clear() }
        )
        storeRepo.clearAllData()

        assertTrue(repository.getAllAliases().isEmpty())
        assertFalse(app.normalizer.normalize("ﾄｸｾﾝﾐﾙｸ").normalizedName == "牛乳")

        // 再起動後も復活しない
        val restarted = App()
        restarted.aliasStore.ensureLoaded()
        assertFalse(restarted.normalizer.normalize("ﾄｸｾﾝﾐﾙｸ").normalizedName == "牛乳")
    }

    @Test
    fun `manual add goes through normalization and merges with the receipt (B-1 B-3)`() = runTest {
        val app = App()
        app.shoppingList.addManualPurchase("ｷﾞｭｳﾆｭｳ", date.minusDays(1), 1, null, null)

        val product = repository.getAllProducts().single()
        assertEquals("牛乳", product.name)
        assertEquals("定番食品", product.category)

        // 翌日にレシートで「牛乳」を取り込むと、手動の購入と 1 件にまとまる
        val candidate = app.scanner.scanLines(lines("牛乳 1L 220")).getOrThrow()
        assertTrue(app.reviewService.saveReceipt(candidate).isSuccess)

        assertEquals(1, repository.getAllProducts().size)
        val purchases = repository.getPurchasesForProduct(product.id)
        assertEquals(1, purchases.size)
        assertEquals(PurchaseSource.RECEIPT, purchases[0].source)
        assertEquals(date.toEpochDay(), purchases[0].purchasedOnEpochDay)
        assertEquals(220, purchases[0].price)
    }

    @Test
    fun `manual add applies learned aliases and keeps unknown names cleaned`() = runTest {
        val app = App()
        app.reviewService.saveReceipt(
            ReceiptCandidate(
                "r1", "テストマート", date, 300,
                items = listOf(ReceiptItemCandidate("i1", "ﾄｸｾﾝﾐﾙｸ", "牛乳", 1, 300))
            )
        )

        val restarted = App()
        restarted.shoppingList.addManualPurchase("ﾄｸｾﾝﾐﾙｸ", date, 1, null, null)
        restarted.shoppingList.addManualPurchase("ヨガマット 3個入", date, 1, null, null)

        val names = repository.getAllProducts().map { it.name }.sorted()
        assertEquals(listOf("ヨガマット", "牛乳").sorted(), names)
    }

    @Test
    fun `F-6 - product stopped by the user returns to tracking when the same name is scanned and saved`() = runTest {
        val app = App()
        app.shoppingList.addManualPurchase("ヨガマット", date.minusDays(30), 1, 880, null)
        val id = repository.getAllProducts().single().id
        app.shoppingList.stopTracking(id.toString())
        assertFalse(repository.getProductById(id)!!.isTracked)

        val candidate = app.scanner.scanLines(lines("ヨガマット 880")).getOrThrow()
        assertTrue(candidate.items.single().isTracked)
        assertTrue(app.reviewService.saveReceipt(candidate).isSuccess)

        assertTrue(repository.getProductById(id)!!.isTracked)
        assertEquals(1, app.shoppingList.getShoppingItemsStream().first().size)
    }

    @Test
    fun `F-6 - a switch turned off at review is respected for a stopped product`() = runTest {
        val app = App()
        app.shoppingList.addManualPurchase("ヨガマット", date.minusDays(30), 1, 880, null)
        val id = repository.getAllProducts().single().id
        app.shoppingList.stopTracking(id.toString())

        val candidate = app.scanner.scanLines(lines("ヨガマット 880")).getOrThrow()
        val switchedOff = candidate.copy(items = candidate.items.map { it.copy(isTracked = false) })
        assertTrue(app.reviewService.saveReceipt(switchedOff).isSuccess)

        assertFalse(repository.getProductById(id)!!.isTracked)
        // 購入履歴にはレシートの購入が残る
        assertEquals(2, repository.getPurchasesForProduct(id).size)
    }

    @Test
    fun `F-6 - a switch turned off at review is respected for a tracked product`() = runTest {
        val app = App()
        app.shoppingList.addManualPurchase("ヨガマット", date.minusDays(30), 1, 880, null)
        val id = repository.getAllProducts().single().id

        val candidate = app.scanner.scanLines(lines("ヨガマット 880")).getOrThrow()
        assertTrue(candidate.items.single().isTracked)
        val switchedOff = candidate.copy(items = candidate.items.map { it.copy(isTracked = false) })
        assertTrue(app.reviewService.saveReceipt(switchedOff).isSuccess)

        assertFalse(repository.getProductById(id)!!.isTracked)
    }

    @Test
    fun `F-6 - a stopped product reached through a learned alias also returns to tracking`() = runTest {
        val first = App()
        // 確認画面で「ﾏｯﾄXYZ」を「ヨガマット」に直して保存 -> 別名を覚える(予測対象外のまま)
        val corrected = ReceiptCandidate(
            id = "r1",
            storeName = "テストマート",
            purchasedOn = date.minusDays(10),
            totalAmount = 880,
            items = listOf(ReceiptItemCandidate("i1", "ﾏｯﾄXYZ", "ヨガマット", 1, 880, isTracked = true))
        )
        assertTrue(first.reviewService.saveReceipt(corrected).isSuccess)
        val id = repository.getAllProducts().single { it.name == "ヨガマット" }.id
        first.shoppingList.stopTracking(id.toString())
        assertFalse(repository.getProductById(id)!!.isTracked)

        // 再起動後に別名の元の表記で読み取る -> 別名の先が登録済みなので対象
        val second = App()
        val candidate = second.scanner.scanLines(lines("ﾏｯﾄXYZ 880")).getOrThrow()
        assertEquals("ヨガマット", candidate.items.single().normalizedName)
        assertTrue(candidate.items.single().isTracked)
        assertTrue(second.reviewService.saveReceipt(candidate).isSuccess)
        assertTrue(repository.getProductById(id)!!.isTracked)
    }

    @Test
    fun `F-6 - an unknown unregistered name stays untracked by default`() = runTest {
        val app = App()
        val candidate = app.scanner.scanLines(lines("ヨガマット 880")).getOrThrow()
        assertFalse(candidate.items.single().isTracked)
    }

    private fun candidate(raw: String, name: String) = ReceiptCandidate(
        id = "r", storeName = "テストマート", purchasedOn = date, totalAmount = 300,
        items = listOf(ReceiptItemCandidate("i1", raw, name, 1, 300, isTracked = true))
    )

    @Test
    fun `name typed at review is normalized like the scan (item 1)`() = runTest {
        val app = App()
        assertTrue(app.reviewService.saveReceipt(candidate("ｷﾞｭｳﾆｭｳ", "ぎゅうにゅう")).isSuccess)
        assertTrue(app.reviewService.saveReceipt(candidate("ｷﾞｭｳﾆｭｳ", "牛乳 1L")).isSuccess)

        val product = repository.getAllProducts().single()
        assertEquals("牛乳", product.name)
        assertEquals("定番食品", product.category)
        assertEquals(2, repository.getPurchasesForProduct(product.id).size)
        // 表記を直しただけなので、誤った別名(ｷﾞｭｳﾆｭｳ -> ぎゅうにゅう)は覚えない
        assertTrue(repository.getAllAliases().isEmpty())
    }

    @Test
    fun `unknown typed name is cleaned and kept as its own product, and the alias is learned (item 1)`() = runTest {
        val app = App()
        assertTrue(app.reviewService.saveReceipt(candidate("ｶｸｳｶｼ", "架空の菓子 2個")).isSuccess)

        val product = repository.getAllProducts().single()
        assertEquals("架空の菓子", product.name)
        assertEquals("日用品", product.category)
        val aliases = repository.getAllAliases()
        assertEquals(1, aliases.size)
        assertEquals(product.id, aliases[0].productId)

        val restarted = App()
        restarted.aliasStore.ensureLoaded()
        assertEquals("架空の菓子", restarted.normalizer.normalize("ｶｸｳｶｼ").normalizedName)
    }

    @Test
    fun `typed name is not matched partially (item 1 and 3)`() = runTest {
        val app = App()
        // 辞書の部分一致なら牛乳になるが、人が入力した名前は完全一致だけを見る
        assertTrue(app.reviewService.saveReceipt(candidate("ｱｲｽ", "架空いちご牛乳")).isSuccess)
        assertEquals("架空いちご牛乳", repository.getAllProducts().single().name)
        assertEquals(false, app.reviewService.suggestTracked("架空みかん牛乳"))
        assertEquals(true, app.reviewService.suggestTracked("ぎゅうにゅう"))
    }

    @Test
    fun `typed name that matches a learned alias is replaced by the alias target (item 1)`() = runTest {
        val app = App()
        app.reviewService.saveReceipt(candidate("ﾄｸｾﾝﾐﾙｸ", "牛乳"))
        val restarted = App()
        restarted.reviewService.saveReceipt(candidate("ﾏｯﾁｬ", "ﾄｸｾﾝﾐﾙｸ"))
        assertEquals(listOf("牛乳"), repository.getAllProducts().map { it.name })
    }

    @Test
    fun `manual add uses exact names and aliases only (item 3)`() = runTest {
        val app = App()
        app.shoppingList.addManualPurchase("ぎゅうにゅう", date, 1, null, null)
        app.shoppingList.addManualPurchase("架空いちご牛乳", date, 1, null, null)
        app.shoppingList.addManualPurchase("架空の菓子 2個", date, 1, null, null)
        app.shoppingList.addManualPurchase("コーヒー牛乳", date, 1, null, null)
        val names = repository.getAllProducts().map { it.name }.sorted()
        assertEquals(listOf("コーヒー牛乳", "牛乳", "架空いちご牛乳", "架空の菓子").sorted(), names)
    }

    @Test
    fun `alias keys in the database follow the in-memory key so the last correction wins after restart (item 5)`() = runTest {
        // 以前の版が生の文字列で保存した、同じ照合キーの 2 行
        val milk = repository.getOrCreateProduct("牛乳", "定番食品")
        val egg = repository.getOrCreateProduct("卵", "定番食品")
        val tofu = repository.getOrCreateProduct("豆腐", "定番食品")
        repository.saveAlias("ﾄｸｾﾝﾐﾙｸ", milk.id)
        repository.saveAlias("トクセンミルク 200ml", egg.id)

        // 新しい版で、正規化後に同じキーになる入力を直す。最後の修正が勝つ
        val app = App()
        app.aliasStore.ensureLoaded()
        app.aliasStore.learn("トクセンミルク", "豆腐", tofu.id)
        assertEquals("豆腐", app.normalizer.normalize("ﾄｸｾﾝﾐﾙｸ").normalizedName)

        val restarted = App()
        restarted.aliasStore.ensureLoaded()
        assertEquals("豆腐", restarted.normalizer.normalize("ﾄｸｾﾝﾐﾙｸ").normalizedName)
        assertEquals("豆腐", restarted.normalizer.normalize("トクセンミルク 200ml").normalizedName)

        // 同じキーの再学習は 1 行にまとまる
        val before = repository.getAllAliases().size
        app.aliasStore.learn("ﾄｸｾﾝﾐﾙｸ 1L", "卵", egg.id)
        app.aliasStore.learn("とくせんみるく", "牛乳", milk.id)
        assertTrue(repository.getAllAliases().size <= before + 1)
        val third = App()
        third.aliasStore.ensureLoaded()
        assertEquals("牛乳", third.normalizer.normalize("ﾄｸｾﾝﾐﾙｸ").normalizedName)
    }
}
