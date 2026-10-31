package receipt.normalize

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import prediction.DefaultConsumptionPredictor
import java.io.File

/**
 * 実際の assets/products.json を読み込んで名寄せ(P-1, P-3)と既定周期(F-3)を確かめる。
 */
class ProductDictionaryNormalizerTest {

    private val json: String = listOf(
        "src/main/assets/products.json",
        "app/src/main/assets/products.json"
    ).map { File(it) }.first { it.exists() }.readText(Charsets.UTF_8)

    private val dictionary = ProductDictionaryLoader.parse(json)
    private val normalizer = DefaultProductNormalizer(initialDictionary = dictionary)

    private fun assertMatches(expectedName: String, raw: String) {
        val result = normalizer.normalize(raw)
        assertEquals("raw=$raw", expectedName, result.normalizedName)
        assertTrue("raw=$raw should be in dictionary", result.isMatchedInDictionary)
    }

    private fun assertNotMatched(raw: String) {
        val result = normalizer.normalize(raw)
        assertFalse("raw=$raw wrongly matched ${result.normalizedName}", result.isMatchedInDictionary)
    }

    @Test
    fun `products json has about 50 entries each with a name category and cycle`() {
        assertTrue("entries=${dictionary.size}", dictionary.size >= 50)
        dictionary.forEach {
            assertTrue(it.normalizedName, it.normalizedName.isNotBlank())
            assertTrue(it.normalizedName, it.category.isNotBlank())
            assertTrue(it.normalizedName, (it.defaultCycleDays ?: 0) > 0)
        }
        assertEquals(dictionary.size, dictionary.map { it.normalizedName }.distinct().size)
    }

    @Test
    fun `half width keywords in products json match half width and full width input (P-1)`() {
        assertMatches("牛乳", "ｷﾞｭｳﾆｭｳ")
        assertMatches("牛乳", "ギュウニュウ 1L")
        assertMatches("食器用洗剤", "ｼｮｯｷｾﾝｻﾞｲ")
        assertMatches("卵", "ﾀﾏｺﾞ")
        assertMatches("醤油", "ｼｮｳﾕ")
        assertMatches("醤油", "ｼｮｳﾕ 1L")
        assertMatches("トイレットペーパー", "ﾄｲﾚｯﾄﾍﾟｰﾊﾟｰ 12R")
    }

    @Test
    fun `every keyword of products json matches its own entry after normalization`() {
        dictionary.forEach { entry ->
            (listOf(entry.normalizedName) + entry.keywords).forEach { key ->
                val cleaned = normalizer.cleanText(key)
                // 容量や入数の表記が混ざるキーワード(12ロール など)は正規化で変わるので対象外
                if (cleaned.isNotBlank() &&
                    DefaultProductNormalizer.foldForMatch(cleaned) == DefaultProductNormalizer.foldForMatch(key)
                ) {
                    val result = normalizer.normalize(key)
                    assertEquals("key=$key", entry.normalizedName, result.normalizedName)
                }
            }
        }
    }

    @Test
    fun `hiragana and katakana are treated as the same (P-1)`() {
        assertMatches("醤油", "しょうゆ")
        assertMatches("醤油", "ショウユ")
        assertMatches("醤油", "ｼｮｳﾕ")
        assertMatches("卵", "たまご")
        assertMatches("卵", "タマゴ")
        assertMatches("味噌", "みそ")
        assertMatches("味噌", "ミソ")
        assertMatches("米", "こめ")
    }

    @Test
    fun `latin letters are compared without case`() {
        assertMatches("ボディーソープ", "ビオレU")
        assertMatches("ボディーソープ", "ビオレｕ")
    }

    @Test
    fun `short keywords do not match inside unrelated words`() {
        assertNotMatched("フライパン")
        assertNotMatched("パンツ")
        assertNotMatched("塩サバ")
        assertNotMatched("バタークッキー")
        assertNotMatched("ﾊﾞﾀｰｸｯｷｰ")
        assertNotMatched("ピーナッツバター")
        assertNotMatched("チーズケーキ")
        assertNotMatched("味噌ラーメン")
        assertNotMatched("卵焼きフライパン")
    }

    @Test
    fun `genuine products with short keywords still match`() {
        assertMatches("食パン", "パン")
        assertMatches("食パン", "ﾊﾟﾝ")
        assertMatches("食パン", "山型食パン 6枚切")
        assertMatches("塩", "塩")
        assertMatches("塩", "精製塩 1kg")
        assertMatches("塩", "伯方の塩")
        assertMatches("バター", "有塩バター")
        assertMatches("バター", "ﾊﾞﾀｰ 450g")
        assertMatches("米", "国産米 5kg")
        assertMatches("卵", "ゆで卵")
        assertMatches("牛乳", "ミニ牛乳")
    }

    @Test
    fun `longest keyword wins when several entries match partially`() {
        // 「みりん風調味料」はみりん、「料理酒」は料理酒
        assertMatches("みりん", "みりん風調味料")
        assertMatches("料理酒", "料理の酒 900ml")
    }

    @Test
    fun `learned alias maps raw text regardless of width kana and size notation (P-2)`() {
        assertNotMatched("ﾏｲｵﾘｼﾞﾅﾙｸﾘｰﾅｰ")
        normalizer.registerAlias("ﾏｲｵﾘｼﾞﾅﾙｸﾘｰﾅｰ 400ml", "食器用洗剤")

        val result = normalizer.normalize("マイオリジナルクリーナー")
        assertEquals("食器用洗剤", result.normalizedName)
        assertEquals("日用品", result.category)
        assertTrue(result.isMatchedInDictionary)

        normalizer.clearAliases()
        assertNotMatched("マイオリジナルクリーナー")
    }

    @Test
    fun `alias to a name outside the dictionary is applied but not reported as dictionary match`() {
        normalizer.registerAlias("ﾖｶﾞﾏｯﾄ", "ヨガマット")
        val result = normalizer.normalize("ヨガマット")
        assertEquals("ヨガマット", result.normalizedName)
        assertFalse(result.isMatchedInDictionary)
        val viaAlias = normalizer.normalize("ﾖｶﾞﾏｯﾄ")
        assertEquals("ヨガマット", viaAlias.normalizedName)
    }

    @Test
    fun `category default cycles come from products json medians (F-3)`() {
        val cycles = ProductDictionaryLoader.categoryCycleDays(dictionary)
        listOf("日用品", "定番食品", "調味料", "飲料", "生鮮品").forEach {
            assertTrue("missing $it in $cycles", cycles.containsKey(it))
        }
        // 生鮮品は数日、日用品は数十日の目安になる
        assertTrue(cycles.getValue("生鮮品") < 7.0)
        assertTrue(cycles.getValue("日用品") in 20.0..40.0)
        assertTrue(cycles.getValue("定番食品") < cycles.getValue("調味料"))

        val manual = ProductDictionaryLoader.categoryCycleDays(
            listOf(
                DictionaryEntry("a", "X", defaultCycleDays = 10),
                DictionaryEntry("b", "X", defaultCycleDays = 20),
                DictionaryEntry("c", "X", defaultCycleDays = 60),
                DictionaryEntry("d", "Y", defaultCycleDays = 4),
                DictionaryEntry("e", "Z", defaultCycleDays = null)
            )
        )
        assertEquals(20.0, manual.getValue("X"), 0.0)
        assertEquals(4.0, manual.getValue("Y"), 0.0)
        assertFalse(manual.containsKey("Z"))
    }

    @Test
    fun `predictor uses dictionary category cycles for a single purchase (F-3)`() {
        val cycles = DefaultConsumptionPredictor.DEFAULT_CATEGORY_CYCLES +
            ProductDictionaryLoader.categoryCycleDays(dictionary)
        val predictor = DefaultConsumptionPredictor(cycles)
        val fresh = domain.model.Product(id = 1, name = "鶏むね肉", category = "生鮮品")
        val purchase = domain.model.Purchase(
            id = 1, productId = 1, purchasedOnEpochDay = 100L, quantity = 1.0
        )
        val prediction = predictor.predict(fresh, listOf(purchase), 101L)
        assertTrue(prediction.isEstimatedCycle)
        assertEquals(cycles.getValue("生鮮品"), prediction.cycleDaysPerUnit, 0.0)
        // 辞書を反映しない既定の予測器では 30 日に落ちてしまう
        assertEquals(30.0, DefaultConsumptionPredictor().predict(fresh, listOf(purchase), 101L).cycleDaysPerUnit, 0.0)
    }

    @Test
    fun `loader parses escapes and rejects malformed json`() {
        val parsed = ProductDictionaryLoader.parse(
            """[{"name":"Aあ","category":"C","defaultCycleDays":7,"keywords":["k\"1","ｋ"]},{"name":""},3]"""
        )
        assertEquals(1, parsed.size)
        assertEquals("Aあ", parsed[0].normalizedName)
        assertEquals(7, parsed[0].defaultCycleDays)
        assertEquals(listOf("k\"1", "ｋ"), parsed[0].keywords)

        assertThrows(IllegalArgumentException::class.java) { ProductDictionaryLoader.parse("[{") }
        assertThrows(IllegalArgumentException::class.java) { ProductDictionaryLoader.parse("{}") }
    }

    @Test
    fun `short han keywords match as a separate word after a space (item 2)`() {
        assertMatches("牛乳", "架空乳業 牛乳")
        assertMatches("牛乳", "架空乳業 牛乳 1000ml")
        assertMatches("米", "国産 米")
        assertMatches("米", "国産 米 5kg")
        assertMatches("卵", "架空養鶏 卵")
        assertMatches("牛乳", "ミニ 牛乳")
    }

    @Test
    fun `kana keywords inside other words do not match (item 3)`() {
        listOf(
            "サトウノ架空ごはん", "サトウの架空ごはん 2個", "ｻﾄｳﾉｶｸｳｺﾞﾊﾝ",
            "プリンス架空", "ゴキブリトラップ", "ストラップ", "エンジョイ架空",
            "チーズバーガー", "バターロール", "ウェットティッシュ", "ｳｪｯﾄﾃｨｯｼｭ",
            "コーヒー牛乳", "いちご牛乳", "苺牛乳", "麻婆豆腐", "白玉", "純米清酒",
            "コーヒー 牛乳", "ラップトップ", "ソースカツ"
        ).forEach { assertNotMatched(it) }
    }

    @Test
    fun `kana keywords still match as a whole word or a word head before a digit or letter`() {
        assertMatches("砂糖", "サトウ")
        assertMatches("砂糖", "ｻﾄｳ 1kg")
        assertMatches("ラップ", "ラップ")
        assertMatches("ラップ", "ﾗｯﾌﾟ 30cm")
        assertMatches("コンディショナー", "リンス")
        assertMatches("チーズ", "チーズ")
        assertMatches("バター", "ﾊﾞﾀｰ")
        assertMatches("インスタントコーヒー", "コーヒー")
        assertMatches("ティッシュペーパー", "ﾃｨｯｼｭ 5箱")
        assertMatches("ティッシュペーパー", "架空ブランド ティッシュ")
        assertMatches("食器用洗剤", "ジョイ")
    }

    @Test
    fun `existing correct matches survive the stricter rules (item 3)`() {
        assertMatches("食パン", "山型食パン")
        assertMatches("食パン", "ﾔﾏｶﾞﾀ食ﾊﾟﾝ")
        assertMatches("米", "国産米")
        assertMatches("バター", "有塩バター")
        assertMatches("牛乳", "ミニ牛乳")
        assertMatches("卵", "ゆで卵")
        assertMatches("豆腐", "木綿豆腐")
        assertMatches("豆腐", "ミニ豆腐")
        assertMatches("醤油", "丸大豆醤油")
        assertMatches("ティッシュペーパー", "ﾎﾞｯｸｽﾃｨｯｼｭ")
        assertMatches("トイレットペーパー", "ﾄｲﾚｯﾄﾍﾟｰﾊﾟｰ 12ﾛｰﾙ")
    }

    @Test
    fun `shiratama is no longer an egg keyword`() {
        assertFalse(dictionary.any { e -> e.keywords.any { it == "白玉" } })
    }

    @Test
    fun `ambiguous words in one name give no dictionary match`() {
        assertNotMatched("牛乳 食パン")
        assertMatches("牛乳", "牛乳 1L")
    }

    @Test
    fun `resolveExact uses aliases and exact names only (items 1 and 3)`() {
        assertEquals("牛乳", normalizer.resolveExact("ぎゅうにゅう")?.normalizedName)
        assertEquals("牛乳", normalizer.resolveExact("ｷﾞｭｳﾆｭｳ 1L")?.normalizedName)
        assertEquals("定番食品", normalizer.resolveExact("牛乳")?.category)
        assertEquals(null, normalizer.resolveExact("ミニ牛乳"))
        assertEquals(null, normalizer.resolveExact("架空の菓子 2個"))
        assertEquals(null, normalizer.resolveExact("   "))
        normalizer.registerAlias("架空の菓子", "ヨガマット")
        assertEquals("ヨガマット", normalizer.resolveExact("架空の菓子 2個")?.normalizedName)
    }

    @Test
    fun `fullwidth hyphen after kana is a long vowel mark (item 4)`() {
        assertMatches("シャンプー", "シャンプ－")
        assertMatches("シャンプー", "シャンプ―")
        assertMatches("シャンプー", "ｼｬﾝﾌﾟｰ")
        assertMatches("ボディーソープ", "ボディ－ソープ")
        assertEquals("シャンプー", normalizer.cleanText("シャンプ－ 400ml"))
        assertEquals("ティシュー", normalizer.cleanText("ティシュ－"))
        // かなの直後でないハイフンは記号として消える
        assertEquals("ABC DEF", normalizer.cleanText("ABC－DEF"))
        assertEquals("ABC DEF", normalizer.cleanText("ABC-DEF"))
    }
}
