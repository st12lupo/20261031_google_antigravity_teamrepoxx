package receipt.parser

import domain.model.ParsedReceipt
import domain.model.ParsedReceiptItem
import domain.receipt.ReceiptParser
import java.text.Normalizer
import java.time.LocalDate
import java.util.regex.Pattern

class DefaultReceiptParser : ReceiptParser {

    override fun parse(ocrLines: List<String>): ParsedReceipt {
        if (ocrLines.isEmpty()) {
            return ParsedReceipt(
                storeName = "不明な店舗",
                purchasedOnEpochDay = LocalDate.now().toEpochDay(),
                totalAmount = 0,
                items = emptyList()
            )
        }

        val cleanedLines = ocrLines.map { normalizeYenMark(it.trim()) }.filter { it.isNotEmpty() }

        val storeName = extractStoreName(cleanedLines)
        val purchasedOnEpochDay = extractDate(cleanedLines)
        val totalAmount = extractTotalAmount(cleanedLines)
        val items = extractItems(cleanedLines)

        val finalTotal = if (totalAmount > 0) totalAmount else items.sumOf { it.price }

        return ParsedReceipt(
            storeName = storeName,
            purchasedOnEpochDay = purchasedOnEpochDay,
            totalAmount = finalTotal,
            items = items
        )
    }

    /** OCR が行末の金額の「¥」を「\」や「Y」と読み違えたものを戻す */
    private fun normalizeYenMark(line: String): String {
        return line.replace(Regex("(?<![A-Za-z0-9])[Yy\\\\＼]\\s*(?=[0-9Ool][0-9Ool,]*\\s*[※*＊軽内円)]?\\s*$)"), "¥")
    }

    private fun extractStoreName(lines: List<String>): String {
        for (line in lines.take(5)) {
            if (isHeaderOrMetaLine(line)) continue
            val stripped = line.replace(Regex("[0-9\\s\\-TELtel:：#*#]+"), "")
            if (stripped.length >= 2) {
                return line.replace(Regex("^(No\\.?\\d+|レシート|領収証?)\\s*"), "").trim()
            }
        }
        return "不明な店舗"
    }

    private fun isHeaderOrMetaLine(line: String): Boolean {
        if (isDateLine(line)) return true
        if (isAddressLine(line)) return true
        val compact = removeSpaces(line)
        if (metaKeywords.any { hasLabel(compact, it.first, anchored = it.second) }) return true
        if (line.matches(Regex(".*\\d{1,2}:\\d{2}.*"))) return true
        return false
    }

    /** 住所や郵便番号の行か。「東京都架空区1-2-3」のような番地を金額と誤読しないために除く */
    private fun isAddressLine(line: String): Boolean {
        val compact = toHalfWidthDigits(removeSpaces(line))
        if (compact.startsWith("〒")) return true
        // 都道府県の直後に市区町村郡が続く行か、都道府県で始まり番地(1-2-3)を含む行だけを住所とする。
        // 「北海道牛乳 198」「青森県産りんご 298」のような商品は住所にしない
        if (prefectureMunicipality.containsMatchIn(compact)) return true
        if (prefectureStart.containsMatchIn(compact) && streetNumber.containsMatchIn(compact)) return true
        return addressNumber.containsMatchIn(compact)
    }

    private fun isDateLine(line: String): Boolean {
        if (line.contains("令和") && line.contains("年")) return true
        return parseDate(line) != null
    }

    private fun toHalfWidthDigits(str: String): String =
        str.map { if (it in '０'..'９') '0' + (it - '０') else it }.joinToString("")

    private fun tryDate(year: Int, month: Int, day: Int): LocalDate? =
        try {
            LocalDate.of(year, month, day)
        } catch (_: Exception) {
            null
        }

    /** 1 行から日付を読む。存在しない日付(13 月など)は日付とみなさない */
    private fun parseDate(rawLine: String): LocalDate? {
        val line = toHalfWidthDigits(rawLine)

        // 令和X年X月X日
        reiwaDate.find(line)?.let { m ->
            val y = if (m.groupValues[1] == "元") 1 else m.groupValues[1].toInt()
            tryDate(2018 + y, m.groupValues[2].toInt(), m.groupValues[3].toInt())?.let { return it }
        }

        // 2026年10月31日 / 2026/10/31 / 2026.10.31 / 2026-10-31(区切りの前後の空白を許す)
        fullDate.find(line)?.let { m ->
            tryDate(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())?.let { return it }
        }

        // 26-10-31 / 26/10/31
        shortDate.find(line)?.let { m ->
            tryDate(2000 + m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())?.let { return it }
        }
        return null
    }

    private fun extractDate(lines: List<String>): Long {
        for (line in lines) {
            parseDate(line)?.let { return it.toEpochDay() }
        }
        return LocalDate.now().toEpochDay()
    }

    private fun extractTotalAmount(lines: List<String>): Int {
        val totalKeywords = listOf("合計", "お買上計", "お買上げ計", "税込計", "支払計")
        for (line in lines.reversed()) {
            val compact = matchForm(line)
            if (isDiscountLabel(compact)) continue
            // 税額や対象額の行(消費税等合計、8%対象合計 など)は合計ではない
            if (nonTotalKeywords.any { compact.contains(it) }) continue
            if (totalKeywords.any { compact.contains(it) }) {
                val amount = extractAmountFromLine(line)
                if (amount > 0) return amount
            }
        }
        return 0
    }

    private fun extractItems(lines: List<String>): List<ParsedReceiptItem> {
        val items = mutableListOf<ParsedReceiptItem>()
        var pendingItemName: String? = null

        for (line in lines) {
            if (isHeaderOrMetaLine(line)) continue
            if (isDiscountLine(line)) {
                if (items.isNotEmpty()) {
                    val discount = extractAmountFromLine(line)
                    if (discount > 0) {
                        val last = items.removeAt(items.size - 1)
                        val newPrice = (last.price - discount).coerceAtLeast(0)
                        items.add(last.copy(price = newPrice))
                    }
                }
                pendingItemName = null
                continue
            }
            if (isExcludedLine(line)) {
                pendingItemName = null
                continue
            }

            // 単独の数量行パターン (例: "  2ｺ X 238   476" や "2点 X 238")
            val quantityMatcher = Pattern.compile("^\\s*([0-9Ool]{1,2})\\s*(?:ｺ|コ|個|点|本|枚)?\\s*[XxX×@]\\s*([0-9,]+)?(?:\\s+([0-9,]+))?").matcher(line)
            if (quantityMatcher.find()) {
                val qtyStr = normalizeDigits(quantityMatcher.group(1) ?: "1")
                val qty = qtyStr.toDoubleOrNull() ?: 1.0
                val unitStr = quantityMatcher.group(2)
                val totalStr = quantityMatcher.group(3)
                // 合計の金額があればそれを使い、単価だけなら単価 × 数量にする
                val price = when {
                    totalStr != null -> extractAmountFromLine(totalStr)
                    unitStr != null -> extractAmountFromLine(unitStr) * (qtyStr.toIntOrNull() ?: 1)
                    else -> 0
                }

                if (pendingItemName != null) {
                    items.add(ParsedReceiptItem(rawName = pendingItemName, quantity = qty, price = price))
                    pendingItemName = null
                    continue
                } else if (items.isNotEmpty()) {
                    val last = items.removeAt(items.size - 1)
                    val finalPrice = if (price > 0) price else last.price
                    items.add(last.copy(quantity = qty, price = finalPrice))
                    continue
                }
            }

            val parsed = parseItemLine(line)
            if (parsed != null) {
                pendingItemName = null
                items.add(parsed)
            } else {
                val candidateName = line.replace(Regex("[※*＊軽内]\\s*$"), "").trim()
                if (candidateName.length >= 2 && !isHeaderOrMetaLine(candidateName) && !isExcludedLine(candidateName)) {
                    pendingItemName = candidateName
                }
            }
        }

        return items
    }

    private fun parseItemLine(rawLine: String): ParsedReceiptItem? {
        var line = rawLine.trim()

        // 軽減税率の記号を除去 (※, *, 軽, 内)
        line = line.replace(Regex("[※*＊軽内]\\s*$"), "").trim()

        // 合計のない数量つきの行 (例: "ｷﾞｭｳﾆｭｳ 1L 2ｺ X238") は単価 × 数量にする
        inlineUnitOnly.find(line)?.let { m ->
            val qty = normalizeDigits(m.groupValues[2]).toIntOrNull() ?: 1
            val unit = extractAmountFromLine(m.groupValues[3])
            val name = m.groupValues[1].trim()
            if (unit > 0 && name.isNotEmpty()) {
                return ParsedReceiptItem(rawName = name, quantity = qty.toDouble(), price = unit * qty)
            }
        }

        val amount = extractAmountFromLine(line)
        if (amount <= 0) return null

        val namePart = line.replace(Regex("[\\s￥¥\\$]*[0-9Ool,]+[\\s※*＊軽内円]*$"), "").trim()
            .replace(trailingPercentOff, "").trim()
        if (namePart.isEmpty()) return null

        // 商品名に数量が含まれるかチェック (例: "ｷﾞｭｳﾆｭｳ 1L 2ｺ X238" または "商品名 2点")
        val qtyInlineMatcher = Pattern.compile("^(.*?)\\s+([0-9Ool]{1,2})\\s*(?:ｺ|コ|個|点|本|枚)\\s*(?:[XxX×@]\\s*[0-9,]+)?$").matcher(namePart)
        if (qtyInlineMatcher.find()) {
            val actualName = (qtyInlineMatcher.group(1) ?: "").trim()
            val qty = normalizeDigits(qtyInlineMatcher.group(2) ?: "1").toDoubleOrNull() ?: 1.0
            return ParsedReceiptItem(
                rawName = actualName,
                quantity = qty,
                price = amount
            )
        }

        return ParsedReceiptItem(
            rawName = namePart,
            quantity = 1.0,
            price = amount
        )
    }

    private fun extractAmountFromLine(line: String): Int {
        val matcher = Pattern.compile("(?<![A-Za-z0-9])[￥¥\\$\\-]?\\s*([0-9Ool][0-9Ool,]*)(?:\\s*[※*＊軽内円])?\\s*$").matcher(line)
        if (matcher.find()) {
            val group = matcher.group(1) ?: return 0
            val numStr = normalizeDigits(group.replace(",", ""))
            return numStr.toIntOrNull() ?: 0
        }
        return 0
    }

    private fun normalizeDigits(str: String): String {
        return str
            .replace('O', '0')
            .replace('o', '0')
            .replace('l', '1')
            .replace('I', '1')
            .replace('０', '0')
            .replace('１', '1')
            .replace('２', '2')
            .replace('３', '3')
            .replace('４', '4')
            .replace('５', '5')
            .replace('６', '6')
            .replace('７', '7')
            .replace('８', '8')
            .replace('９', '9')
    }

    /**
     * 直前の商品に適用する値引の行か。行頭が N%引、行頭の値引・割引・クーポンのラベル、
     * または金額が負の行だけを値引にする。品名付きの「お弁当 30%引 350」は商品。
     * 「値引合計」は各商品の値引の集計なので含めない
     */
    private fun isDiscountLine(line: String): Boolean {
        val compact = matchForm(line)
        if (isDiscountLabel(compact)) return !compact.contains("計")
        return negativeAmount.containsMatchIn(Normalizer.normalize(line.trim(), Normalizer.Form.NFKC)) &&
            !isNonDiscountLabel(compact)
    }

    private fun isDiscountLabel(compact: String): Boolean {
        if (percentOffAtStart.containsMatchIn(compact)) return true
        return discountKeywords.any { hasLabel(compact, it, anchored = true) }
    }

    private fun isNonDiscountLabel(compact: String): Boolean {
        if (excludeKeywords.any { hasLabel(compact, it, anchored = false) }) return true
        return anchoredExcludeKeywords.any { hasLabel(compact, it, anchored = true) }
    }

    /**
     * 合計、ポイントなどのラベルの行か。語の一部が商品名に入っているだけの行
     * (ポイントメイクリムーバー、釣り糸など)は除かない
     */
    private fun isExcludedLine(line: String): Boolean {
        val compact = matchForm(line)
        return isDiscountLabel(compact) || isNonDiscountLabel(compact)
    }

    /** 空白を除き、半角カナ・全角英数を揃えた照合用の文字列(ｵﾂﾘ は オツリ になる) */
    private fun matchForm(line: String): String =
        Normalizer.normalize(removeSpaces(line), Normalizer.Form.NFKC)

    /**
     * [keyword] がラベルとして現れるか。語の直後が行末、数字や記号、または「額」「残高」などの
     * ラベルの続きであるときだけ一致とし、後ろに別のかなや漢字が続く商品名は除く。
     * [anchored] が true のときは行頭(記号を除く)の語だけを対象にする
     */
    private fun hasLabel(compact: String, keyword: String, anchored: Boolean): Boolean {
        var idx = compact.indexOf(keyword, ignoreCase = true)
        while (idx >= 0) {
            val end = idx + keyword.length
            val before = compact.substring(0, idx)
            val latinJoined = keyword[0].code < 128 && keyword[0].isLetter() &&
                before.isNotEmpty() && before.last().code < 128 && before.last().isLetter()
            val positionOk = !latinJoined && (!anchored || before.none { it.isLetterOrDigit() })
            if (positionOk && continuesAsLabel(compact.substring(end))) return true
            idx = compact.indexOf(keyword, idx + 1, ignoreCase = true)
        }
        return false
    }

    private fun continuesAsLabel(rest: String): Boolean {
        if (rest.isEmpty()) return true
        if (!isJapaneseLetter(rest[0])) return true
        if (labelSuffixes.any { rest.startsWith(it) }) return true
        // 送り仮名(値引き、お支払い、税込み、お預り金)を許し、その先が行末や金額ならラベルとする
        return rest[0] in okurigana && continuesAsLabel(rest.substring(1))
    }

    private fun isJapaneseLetter(c: Char): Boolean {
        if (c == 'ー' || c in 'ｦ'..'ﾟ') return true
        return when (Character.UnicodeScript.of(c.code)) {
            Character.UnicodeScript.HIRAGANA,
            Character.UnicodeScript.KATAKANA,
            Character.UnicodeScript.HAN -> true
            else -> false
        }
    }

    /** OCR が「合 計」のように文字の間に入れる半角・全角の空白を除く */
    private fun removeSpaces(line: String): String {
        return line.replace(Regex("[\\s　]"), "")
    }

    private companion object {
        val reiwaDate = Regex("令和\\s*([0-9]{1,2}|元)\\s*年\\s*([0-9]{1,2})\\s*月\\s*([0-9]{1,2})")
        val fullDate = Regex("(?<![0-9])(20[0-9]{2})\\s*[年/.\\-]\\s*([0-9]{1,2})\\s*[月/.\\-]\\s*([0-9]{1,2})(?![0-9])")
        val shortDate = Regex("(?<![0-9])([2-9][0-9])[\\-/.]([0-9]{1,2})[\\-/.]([0-9]{1,2})(?![0-9])")

        val prefectureStart = Regex("^(北海道|東京都|京都府|大阪府|[一-龥]{2,3}県)")
        val prefectureMunicipality = Regex("^(北海道|東京都|京都府|大阪府|[一-龥]{2,3}県)(?!産)[^0-9]{1,5}?[市区町村郡]")
        val streetNumber = Regex("[0-9]+-[0-9]+|[0-9]+丁目")
        val percentOffAtStart = Regex("(?i)^[^\\p{L}\\p{N}]*[0-9]+[%％](引|off)")
        val trailingPercentOff = Regex("(?i)\\s*[0-9]+[%％](引|off)\\s*$")
        /** 金額が負の行(符号の前は行頭・空白・円記号) */
        val negativeAmount = Regex("(?:^|[\\s¥])[-−▲△]\\s*¥?\\s*[0-9Ool][0-9Ool,]*\\s*[※*＊軽内円]?\\s*$")
        val discountKeywords = listOf("値引", "割引", "クーポン")
        val okurigana = "きいみ金"
        val addressNumber = Regex("([市区町村郡][^\\s]{0,12}[0-9]+-[0-9]+)|([0-9]+丁目)")

        val inlineUnitOnly = Regex("^(.+?)\\s+([0-9Ool]{1,2})\\s*(?:ｺ|コ|個|点|本|枚)?\\s*[XxX×@]\\s*([0-9Ool,]+)$")

        val nonTotalKeywords = listOf("消費税", "内税", "外税", "税率", "税抜", "対象", "点数", "内消費",
            "ポイント", "預", "釣", "つり", "オツリ", "現金", "クレジット", "電子マネー", "決済")

        /** 語と、行頭だけを対象にするか */
        val metaKeywords = listOf(
            "領収" to false, "レシート" to false, "TEL" to false, "電話" to false,
            "No." to true, "登録番号" to false, "日付" to false, "日時" to false,
            "レジ" to false, "担当" to false
        )

        val excludeKeywords = listOf(
            "合計", "小計", "消費税", "内税", "外税", "税抜", "税込",
            "お預かり", "お預り", "預かり", "預り", "お釣り", "お釣", "おつり", "オツリ", "オアズカリ",
            "つり銭", "釣り銭", "釣銭", "釣り",
            "現金", "クレジット", "ポイント", "番号", "TEL",
            "担当者", "領収", "レシート", "対象額", "税率",
            "商品代金", "支払", "残高", "お買上計", "お買上げ計", "対象", "税額", "点数",
            "電子マネー", "決済", "コード決済"
        )

        /** 行頭のときだけラベルとみなす支払手段の語(図書カードのような商品名を残すため) */
        val anchoredExcludeKeywords = listOf("カード", "QR", "バーコード", "商品券", "ギフト券", "ギフトカード")

        /** ラベルの語の後ろに続いてよい語 */
        val labelSuffixes = listOf(
            "額", "金額", "等", "計", "合計", "小計", "残高", "残", "利用", "付与", "還元", "加算",
            "番号", "明細", "点数", "数", "済", "分", "カウンター", "カード", "払い", "払", "方法",
            "書", "証", "控", "者", "券", "価格", "詳細", "対象", "累計", "情報"
        )
    }
}
