package receipt.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ReceiptParserTest {

    private val parser = DefaultReceiptParser()

    @Test
    fun `parse standard receipt with store date and items`() {
        val ocrLines = listOf(
            "架空マート 桜町店",
            "TEL 00-0000-0000",
            "2026/10/31 14:23 レジ01 担当:001",
            "ｼｮｯｷｾﾝｻﾞｲ    198",
            "ﾄｲﾚｯﾄﾍﾟｰﾊﾟｰ 12R   458",
            "小計    656",
            "消費税等   65",
            "合計   721",
            "お預かり   1000",
            "お釣り   279"
        )

        val result = parser.parse(ocrLines)

        assertEquals("架空マート 桜町店", result.storeName)
        assertEquals(LocalDate.of(2026, 10, 31).toEpochDay(), result.purchasedOnEpochDay)
        assertEquals(721, result.totalAmount)
        assertEquals(2, result.items.size)

        assertEquals("ｼｮｯｷｾﾝｻﾞｲ", result.items[0].rawName)
        assertEquals(198, result.items[0].price)
        assertEquals(1.0, result.items[0].quantity, 0.001)

        assertEquals("ﾄｲﾚｯﾄﾍﾟｰﾊﾟｰ 12R", result.items[1].rawName)
        assertEquals(458, result.items[1].price)
        assertEquals(1.0, result.items[1].quantity, 0.001)
    }

    @Test
    fun `parse quantity on same line`() {
        val ocrLines = listOf(
            "テストスーパー ひばり店",
            "2026年10月31日",
            "ｷﾞｭｳﾆｭｳ 1L 2ｺ X238   476",
            "合計   476"
        )

        val result = parser.parse(ocrLines)

        assertEquals(1, result.items.size)
        assertEquals("ｷﾞｭｳﾆｭｳ 1L", result.items[0].rawName)
        assertEquals(2.0, result.items[0].quantity, 0.001)
        assertEquals(476, result.items[0].price)
    }

    @Test
    fun `parse quantity on next line`() {
        val ocrLines = listOf(
            "テストマート",
            "2026.10.31",
            "ｷﾞｭｳﾆｭｳ 1L",
            "  2ｺ X 238   476",
            "合計   476"
        )

        val result = parser.parse(ocrLines)

        assertEquals(1, result.items.size)
        assertEquals("ｷﾞｭｳﾆｭｳ 1L", result.items[0].rawName)
        assertEquals(2.0, result.items[0].quantity, 0.001)
        assertEquals(476, result.items[0].price)
    }

    @Test
    fun `parse reduced tax mark trimming`() {
        val ocrLines = listOf(
            "テストフーズ",
            "2026-10-31",
            "ﾄﾘﾑﾈﾆｸ    412 ※",
            "合計   412"
        )

        val result = parser.parse(ocrLines)

        assertEquals(1, result.items.size)
        assertEquals("ﾄﾘﾑﾈﾆｸ", result.items[0].rawName)
        assertEquals(412, result.items[0].price)
    }

    @Test
    fun `parse discount line does not create new item and reduces price`() {
        val ocrLines = listOf(
            "テストドラッグ",
            "2026/10/31",
            "ｼｬﾝﾌﾟｰ    598",
            "  値引   -100",
            "合計   498"
        )

        val result = parser.parse(ocrLines)

        assertEquals(1, result.items.size)
        assertEquals("ｼｬﾝﾌﾟｰ", result.items[0].rawName)
        assertEquals(498, result.items[0].price)
    }

    @Test
    fun `exclude non product lines such as tax change credit points`() {
        val ocrLines = listOf(
            "架空ストア",
            "2026/10/31",
            "ﾃｨｯｼｭﾍﾟｰﾊﾟｰ   298",
            "小計   298",
            "外税   29",
            "合計   327",
            "現金   1000",
            "釣銭   673",
            "ポイント   12P",
            "登録番号 T1234567890123"
        )

        val result = parser.parse(ocrLines)

        assertEquals(1, result.items.size)
        assertEquals("ﾃｨｯｼｭﾍﾟｰﾊﾟｰ", result.items[0].rawName)
        assertEquals(298, result.items[0].price)
        assertEquals(327, result.totalAmount)
    }

    @Test
    fun `parse receipt with spaced total and electronic money payment lines`() {
        val ocrLines = listOf(
            "架空Pマート",
            "架空プラザ店",
            "架空県架空市架空町",
            "X-X-X",
            "電話:000-XXX-XXXX レジ#1",
            "2026年01月12日(日) 11:30 責222",
            "領収書",
            "架空コーラゼロ500P ¥155",
            "ﾋﾞｯｸﾞｱﾒﾘｶﾝﾄﾞｯｸﾞ ¥105",
            "値引額 -11",
            "ツナ＆コーンサラダ ¥199",
            "架空焙煎ｺﾞﾏﾄﾞﾚｯｼﾝｸﾞ ¥164",
            "(商品代金 ¥623",
            "(値引合計 -11",
            "合 計 ¥612",
            "kakuupay支払 ¥612",
            "お買上明細は上記のとおりです。",
            "商品価格には消費税等を含みます。",
            "kakuupay番号 ************1840",
            "kakuupay残高 ¥4,305",
            "今回ﾎﾟｲﾝﾄ 5P",
            "ﾎﾟｲﾝﾄ残高 51P",
            "伝票番号 111-111-222-2222",
            "お得なおしらせ！",
            "新規登録でもれなく",
            "詳しくはレジカウンターまで"
        )

        val result = parser.parse(ocrLines)

        assertEquals("架空Pマート", result.storeName)
        assertEquals(LocalDate.of(2026, 1, 12).toEpochDay(), result.purchasedOnEpochDay)
        assertEquals(612, result.totalAmount)
        assertEquals(
            listOf(
                "架空コーラゼロ500P" to 155,
                "ﾋﾞｯｸﾞｱﾒﾘｶﾝﾄﾞｯｸﾞ" to 94,
                "ツナ＆コーンサラダ" to 199,
                "架空焙煎ｺﾞﾏﾄﾞﾚｯｼﾝｸﾞ" to 164
            ),
            result.items.map { it.rawName to it.price }
        )
    }

    @Test
    fun `yen mark misread by OCR as backslash or Y is treated as yen`() {
        val ocrLines = listOf(
            "架空Pマート",
            "架空プラザ店",
            "電話: 000-XXX-XXXX レジ#1",
            "2026年01月12日 (日) 11:30責222",
            "領収書",
            "架空コーラゼロ500P  \\155",
            "ビッグアメリカンドッグ  Y105",
            "値引額  -11",
            "ツナ&コーンサラダ  ¥199",
            "架空培煎ゴマドレッシング  ¥164",
            "(商品代金  ¥623)",
            "(値引合計  -11)",
            "合 計  Y612",
            "kakuupay支払  ¥612",
            "お買上明細は上記のとおりです。",
            "kakuupay番号  ***:  c*****1840",
            "kakuupay残高  ¥4, 305",
            "今回ポイント  5P",
            "ポイント残高  51P",
            "伝票番号  111-111-222-2222"
        )

        val result = parser.parse(ocrLines)

        assertEquals(612, result.totalAmount)
        assertEquals(
            listOf(
                "架空コーラゼロ500P" to 155,
                "ビッグアメリカンドッグ" to 94,
                "ツナ&コーンサラダ" to 199,
                "架空培煎ゴマドレッシング" to 164
            ),
            result.items.map { it.rawName to it.price }
        )
    }

    @Test
    fun `Y inside a product name is not treated as yen`() {
        val result = parser.parse(listOf("架空ストア", "2026/01/12", "BABY100 ¥300", "TOY 50", "合計 ¥350"))

        assertEquals(
            listOf("BABY100" to 300, "TOY" to 50),
            result.items.map { it.rawName to it.price }
        )
    }

    @Test
    fun `discount total and summary lines with closing parenthesis do not change items`() {
        val ocrLines = listOf(
            "架空ストア",
            "2026/01/12",
            "ｼｬﾝﾌﾟｰ 598",
            "(商品代金 ¥598)",
            "(値引合計 -100)",
            "合　計 ¥498"
        )

        val result = parser.parse(ocrLines)

        assertEquals(listOf("ｼｬﾝﾌﾟｰ" to 598), result.items.map { it.rawName to it.price })
        assertEquals(498, result.totalAmount)
    }

    @Test
    fun `parse various date formats including reiwa and short year`() {
        val reiwaLines = listOf("店名A", "令和8年10月31日", "商品A 100", "合計 100")
        val shortLines = listOf("店名B", "26-10-31", "商品B 200", "合計 200")

        val resultReiwa = parser.parse(reiwaLines)
        val resultShort = parser.parse(shortLines)

        assertEquals(LocalDate.of(2026, 10, 31).toEpochDay(), resultReiwa.purchasedOnEpochDay)
        assertEquals(LocalDate.of(2026, 10, 31).toEpochDay(), resultShort.purchasedOnEpochDay)
    }

    @Test
    fun `handle OCR character confusion for numbers O and l`() {
        val ocrLines = listOf(
            "架空マート",
            "2026/10/31",
            "ﾗｯﾌﾟ 3Om   l98",
            "合計   l98"
        )

        val result = parser.parse(ocrLines)

        assertEquals(1, result.items.size)
        assertEquals(198, result.items[0].price)
        assertEquals(198, result.totalAmount)
    }

    @Test
    fun `handle empty or unreadable lines gracefully`() {
        val emptyResult = parser.parse(emptyList())
        assertEquals("不明な店舗", emptyResult.storeName)
        assertEquals(0, emptyResult.totalAmount)
        assertTrue(emptyResult.items.isEmpty())

        val unreadableLines = listOf("??!#@$", "---***---")
        val unreadableResult = parser.parse(unreadableLines)
        assertNotNull(unreadableResult)
        assertTrue(unreadableResult.items.isEmpty())
    }

    private fun nameAndPrice(lines: List<String>) =
        parser.parse(lines).items.map { it.rawName to it.price }

    @Test
    fun `address line is not an item`() {
        val result = parser.parse(
            listOf("架空マート", "東京都架空区1-2-3", "2026/10/31", "ｼｮｯｷｾﾝｻﾞｲ 198", "合計 198")
        )
        assertEquals(listOf("ｼｮｯｷｾﾝｻﾞｲ" to 198), result.items.map { it.rawName to it.price })
        assertEquals("架空マート", result.storeName)
    }

    @Test
    fun `address line without prefecture is not an item or store name`() {
        val result = parser.parse(listOf("架空市架空町4-5-6", "架空ストア", "2026/10/31", "ﾄﾘﾑﾈﾆｸ 412", "合計 412"))
        assertEquals(listOf("ﾄﾘﾑﾈﾆｸ" to 412), result.items.map { it.rawName to it.price })
        assertEquals("架空ストア", result.storeName)
    }

    @Test
    fun `total ignores tax and target lines after the total`() {
        val result = parser.parse(
            listOf(
                "架空ストア", "2026/10/31",
                "商品A 500", "商品B 500",
                "合計 1000",
                "消費税等合計 74",
                "8%対象合計 600",
                "10%対象合計 400"
            )
        )
        assertEquals(1000, result.totalAmount)
        assertEquals(2, result.items.size)
    }

    @Test
    fun `products containing exclusion words are kept`() {
        val lines = listOf(
            "架空ストア", "2026/10/31",
            "ポイントメイクリムーバー 880",
            "釣り糸 500",
            "レジャーシート 500",
            "コーヒーNo.1 120",
            "合計 2000"
        )
        assertEquals(
            listOf("ポイントメイクリムーバー" to 880, "釣り糸" to 500, "レジャーシート" to 500, "コーヒーNo.1" to 120),
            nameAndPrice(lines)
        )
    }

    @Test
    fun `label lines are still excluded`() {
        val lines = listOf(
            "架空ストア", "2026/10/31",
            "商品A 300",
            "ポイント残高 51P", "お釣り 279", "レジ#1", "No.123",
            "kakuupay支払 ¥612", "伝票番号 111-111", "(商品代金 ¥623)",
            "合計 300"
        )
        assertEquals(listOf("商品A" to 300), nameAndPrice(lines))
    }

    @Test
    fun `quantity line without total multiplies unit price`() {
        val result = parser.parse(
            listOf("架空ストア", "2026/10/31", "ｷﾞｭｳﾆｭｳ 1L", "  2ｺ X 238", "合計 476")
        )
        assertEquals(1, result.items.size)
        assertEquals(2.0, result.items[0].quantity, 0.001)
        assertEquals(476, result.items[0].price)
    }

    @Test
    fun `quantity on same line without total multiplies unit price`() {
        val result = parser.parse(listOf("架空ストア", "2026/10/31", "ｷﾞｭｳﾆｭｳ 1L 2ｺ X238", "合計 476"))
        assertEquals(1, result.items.size)
        assertEquals("ｷﾞｭｳﾆｭｳ 1L", result.items[0].rawName)
        assertEquals(2.0, result.items[0].quantity, 0.001)
        assertEquals(476, result.items[0].price)
    }

    @Test
    fun `date with spaces is read`() {
        val result = parser.parse(listOf("架空ストア", "2026年 10月31日", "商品A 100", "合計 100"))
        assertEquals(LocalDate.of(2026, 10, 31).toEpochDay(), result.purchasedOnEpochDay)
        val spaced = parser.parse(listOf("架空ストア", "2026 / 10 / 31 14:00", "商品A 100", "合計 100"))
        assertEquals(LocalDate.of(2026, 10, 31).toEpochDay(), spaced.purchasedOnEpochDay)
    }

    @Test
    fun `phone numbers and invalid years are not dates`() {
        val today = LocalDate.now().toEpochDay()
        for (line in listOf("0120-12-34", "2999-12-31", "1999/12/31", "2026/13/45")) {
            val r = parser.parse(listOf("架空ストア", line, "商品A 100", "合計 100"))
            assertEquals(line, today, r.purchasedOnEpochDay)
        }
        val real = parser.parse(listOf("架空ストア", "0120-12-34", "2026/10/31", "商品A 100", "合計 100"))
        assertEquals(LocalDate.of(2026, 10, 31).toEpochDay(), real.purchasedOnEpochDay)
    }

    @Test
    fun `labels with okurigana are excluded and discount applies to previous item`() {
        val lines = listOf(
            "架空ストア", "2026/10/31",
            "商品A 500",
            "値引き -100",
            "お支払い ¥1,000", "釣り銭 ¥120", "お預り金 ¥1,000", "税込み 1000", "税抜き 900",
            "合計 400"
        )
        assertEquals(listOf("商品A" to 400), nameAndPrice(lines))
    }

    @Test
    fun `summary and change lines are excluded and percent discount applies`() {
        val lines = listOf(
            "架空ストア", "2026/10/31",
            "商品A 500", "商品B 300",
            "20%引 -50",
            "お買上計 1,000",
            "8%対象 1000", "税額 80", "点数 3",
            "お釣 120", "おつり 120", "つり銭 120"
        )
        val result = parser.parse(lines)
        assertEquals(listOf("商品A" to 500, "商品B" to 250), result.items.map { it.rawName to it.price })
        assertEquals(1000, result.totalAmount)
    }

    @Test
    fun `products starting with prefecture names are kept but addresses are not`() {
        val lines = listOf(
            "架空ストア", "東京都架空区1-2-3", "架空県架空市架空町1丁目", "2026/10/31",
            "北海道牛乳 198", "青森県産りんご 298", "静岡県産みかん 398",
            "合計 894"
        )
        assertEquals(
            listOf("北海道牛乳" to 198, "青森県産りんご" to 298, "静岡県産みかん" to 398),
            nameAndPrice(lines)
        )
        assertEquals("架空ストア", parser.parse(lines).storeName)
    }

    @Test
    fun `item with discount word in the name is a product and does not discount the previous item`() {
        val lines = listOf(
            "架空ストア", "2026/10/31",
            "牛乳 200",
            "お弁当 30%引 350",
            "合計 550"
        )
        assertEquals(listOf("牛乳" to 200, "お弁当" to 350), nameAndPrice(lines))
    }

    @Test
    fun `negative amount line discounts the previous item and leading percent off applies`() {
        val lines = listOf(
            "架空ストア", "2026/10/31",
            "牛乳 200", "お弁当 350", "クーポン -100",
            "パン 300", "30%引 -90",
            "ポイント利用 -50",
            "合計 660"
        )
        assertEquals(listOf("牛乳" to 200, "お弁当" to 250, "パン" to 210), nameAndPrice(lines))
    }

    @Test
    fun `payment method and half width labels are excluded`() {
        val lines = listOf(
            "架空ストア", "2026/10/31",
            "牛乳 200",
            "電子マネー 1,000", "ｵﾂﾘ 800", "ｵｱｽﾞｶﾘ 1,000", "カード 1,000", "QR決済 1,000",
            "バーコード決済 1,000", "QRコード決済 1,000", "カード決済 1,000", "ポイント対象 100",
            "合計 200"
        )
        assertEquals(listOf("牛乳" to 200), nameAndPrice(lines))
    }

    @Test
    fun `buy total label with okurigana is excluded from items and read as total`() {
        val lines = listOf("架空ストア", "2026/10/31", "商品A 400", "商品B 600", "お買上げ計 1,000")
        val result = parser.parse(lines)
        assertEquals(listOf("商品A" to 400, "商品B" to 600), result.items.map { it.rawName to it.price })
        assertEquals(1000, result.totalAmount)
        val tax = parser.parse(listOf("架空ストア", "2026/10/31", "商品A 400", "税込合計 400", "お買上計 400"))
        assertEquals(400, tax.totalAmount)
        assertEquals(1, tax.items.size)
    }

    @Test
    fun `total is not taken from point or deposit totals`() {
        val result = parser.parse(
            listOf(
                "架空ストア", "2026/10/31",
                "商品A 1000",
                "合計 1,000",
                "ポイント合計 5",
                "お預り合計 2,000",
                "お釣合計 1,000",
                "現金合計 2,000"
            )
        )
        assertEquals(1000, result.totalAmount)
        assertEquals(listOf("商品A" to 1000), result.items.map { it.rawName to it.price })
    }

    @Test
    fun `payment words inside product names are kept`() {
        val lines = listOf(
            "架空ストア", "2026/10/31",
            "図書カード 500", "カードケース 300", "ポイントメイクリムーバー 880", "釣り糸 500", "レジャーシート 500",
            "合計 2680"
        )
        assertEquals(
            listOf("図書カード" to 500, "カードケース" to 300, "ポイントメイクリムーバー" to 880, "釣り糸" to 500, "レジャーシート" to 500),
            nameAndPrice(lines)
        )
    }
}
