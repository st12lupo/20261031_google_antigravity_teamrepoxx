package receipt.normalize

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductNormalizerTest {

    private val normalizer = DefaultProductNormalizer()

    @Test
    fun `cleanText removes volume packaging and symbols`() {
        // 12R, 1L, 500ml などの容量表記と記号の除去
        assertEquals("トイレットペーパー", normalizer.cleanText("ﾄｲﾚｯﾄﾍﾟｰﾊﾟｰ 12R"))
        assertEquals("ギュウニュウ", normalizer.cleanText("ｷﾞｭｳﾆｭｳ 1L"))
        assertEquals("ショッキセンザイ", normalizer.cleanText("【特売】 ｼｮｯｷｾﾝｻﾞｲ 500ml ※"))
        assertEquals("ティッシュ", normalizer.cleanText("ティッシュ (5個入)"))
    }

    @Test
    fun `cleanText normalizes half width kana to full width`() {
        assertEquals("ショッキセンザイ", normalizer.cleanText("ｼｮｯｷｾﾝｻﾞｲ"))
        assertEquals("トリムネニク", normalizer.cleanText("ﾄﾘﾑﾈﾆｸ"))
        assertEquals("ラップ", normalizer.cleanText("ﾗｯﾌﾟ"))
    }

    @Test
    fun `cleanText fixes confusable characters such as kanji one to prolonged sound mark`() {
        assertEquals("トイレットペーパー", normalizer.cleanText("トイレットペーパ一"))
    }

    @Test
    fun `cleanText keeps a fullwidth hyphen after kana as a long vowel mark`() {
        assertEquals("シャンプー", normalizer.cleanText("シャンプ－"))
        assertEquals("シャンプー", normalizer.cleanText("ｼｬﾝﾌﾟｰ"))
        assertEquals("シャンプー", normalizer.cleanText("シャンプ―"))
    }

    @Test
    fun `normalize matches half width kana abbreviation to dictionary entry`() {
        val result = normalizer.normalize("ｼｮｯｷｾﾝｻﾞｲ")
        assertEquals("食器用洗剤", result.normalizedName)
        assertEquals("日用品", result.category)
        assertTrue(result.isMatchedInDictionary)
    }

    @Test
    fun `normalize removes volume and matches dictionary entry`() {
        val tpResult = normalizer.normalize("ﾄｲﾚｯﾄﾍﾟｰﾊﾟｰ 12R")
        assertEquals("トイレットペーパー", tpResult.normalizedName)
        assertEquals("日用品", tpResult.category)
        assertTrue(tpResult.isMatchedInDictionary)

        val milkResult = normalizer.normalize("ｷﾞｭｳﾆｭｳ 1L")
        assertEquals("牛乳", milkResult.normalizedName)
        assertEquals("定番食品", milkResult.category)
        assertTrue(milkResult.isMatchedInDictionary)
    }

    @Test
    fun `normalize handles learned alias correctly`() {
        // 未登録時は辞書に一致しない
        val unknownResult = normalizer.normalize("マイオリジナルクリーナー")
        assertFalse(unknownResult.isMatchedInDictionary)

        // 別名として登録 (確認画面で修正した想定)
        normalizer.registerAlias("マイオリジナルクリーナー", "食器用洗剤")

        // 登録後は自動適用される
        val learnedResult = normalizer.normalize("マイオリジナルクリーナー 400ml")
        assertEquals("食器用洗剤", learnedResult.normalizedName)
        assertEquals("日用品", learnedResult.category)
        assertTrue(learnedResult.isMatchedInDictionary)
    }

    @Test
    fun `normalize returns cleaned text and false when not in dictionary`() {
        val result = normalizer.normalize("未知のアイテムXYZ 100g")
        assertEquals("未知のアイテムXYZ", result.normalizedName)
        assertFalse(result.isMatchedInDictionary)
    }

    @Test
    fun `cleanText keeps a count that is followed by more of the name (R-4)`() {
        assertEquals("1本満足バー", normalizer.cleanText("1本満足バー"))
        assertEquals("5本指ソックス", normalizer.cleanText("5本指ソックス"))
        assertEquals("3枚切り食パン", normalizer.cleanText("3枚切り食パン"))
    }

    @Test
    fun `cleanText removes unit with iri, iri, kumi, pack suffix at the end of a word (cleanText fix 4)`() {
        assertEquals("マスク", normalizer.cleanText("マスク 50枚入"))
        assertEquals("マスク", normalizer.cleanText("マスク 30枚入"))
        assertEquals(normalizer.cleanText("マスク 50枚入"), normalizer.cleanText("マスク 30枚入"))
        assertEquals("サンプル", normalizer.cleanText("サンプル 3個入り"))
        assertEquals("サンプル", normalizer.cleanText("サンプル 3本入"))
        assertEquals("サンプル", normalizer.cleanText("サンプル 3本組"))
        assertEquals("サンプル", normalizer.cleanText("サンプル 3本入り"))
        assertEquals("サンプル", normalizer.cleanText("サンプル 2枚パック"))
        assertEquals("マスク", normalizer.cleanText("マスク50枚入"))
        assertEquals("マスク 箱", normalizer.cleanText("マスク 50枚入 箱"))
        // 単位の直後に名前が続くものは残す
        assertEquals("1本満足バー", normalizer.cleanText("1本満足バー"))
        assertEquals("5本指ソックス", normalizer.cleanText("5本指ソックス"))
        assertEquals("3本組みひも", normalizer.cleanText("3本組みひも"))
        assertEquals("ヨガマット", normalizer.cleanText("ヨガマット 3個入"))
        assertEquals("架空コーラゼロ", normalizer.cleanText("架空コーラゼロ500P"))
    }

    @Test
    fun `cleanText still removes a trailing or space-separated unit (R-4)`() {
        assertEquals("トイレットペーパー", normalizer.cleanText("ﾄｲﾚｯﾄﾍﾟｰﾊﾟｰ 12R"))
        assertEquals("ヨガマット", normalizer.cleanText("ヨガマット 3個入"))
        assertEquals("架空コーラゼロ", normalizer.cleanText("架空コーラゼロ500P"))
        assertEquals("架空コーラ ゼロ", normalizer.cleanText("架空コーラ 500ml ゼロ"))
        assertEquals("架空ジュース", normalizer.cleanText("架空ジュース 500ml"))
        assertEquals("トイレットペーパー", normalizer.cleanText("トイレットペーパー12ロール"))
    }

    @Test
    fun `removeAlias returns the target and forgets the alias`() {
        val n = DefaultProductNormalizer()
        n.registerAlias("特売牛乳", "牛乳")
        assertEquals("牛乳", n.normalize("特売牛乳").normalizedName)

        assertEquals("牛乳", n.removeAlias("特売牛乳"))
        assertEquals("特売牛乳", n.normalize("特売牛乳").normalizedName)
        assertEquals(null, n.removeAlias("特売牛乳"))
    }
}
