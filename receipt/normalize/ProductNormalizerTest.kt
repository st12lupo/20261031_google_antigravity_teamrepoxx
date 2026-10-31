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
}
