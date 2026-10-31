package receipt.parser

import domain.model.ParsedReceipt
import domain.model.ParsedReceiptItem
import domain.receipt.ReceiptParser
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

        val cleanedLines = ocrLines.map { it.trim() }.filter { it.isNotEmpty() }

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
        val ignoreKeywords = listOf(
            "領収", "レシート", "TEL", "電話", "No.", "登録番号", "日付", "日時", "レジ", "担当"
        )
        if (ignoreKeywords.any { line.contains(it, ignoreCase = true) }) return true
        if (line.matches(Regex(".*\\d{1,2}:\\d{2}.*"))) return true
        return false
    }

    private fun isDateLine(line: String): Boolean {
        if (line.contains("令和") && line.contains("年")) return true
        if (line.matches(Regex(".*[20][0-9]{3}[年/\\.\\-][0-9]{1,2}[月/\\.\\-][0-9]{1,2}.*"))) return true
        if (line.matches(Regex(".*(?<!\\d)[2-9][0-9][\\-/\\.][0-9]{1,2}[\\-/\\.][0-9]{1,2}(?!\\d).*"))) return true
        return false
    }

    private fun extractDate(lines: List<String>): Long {
        for (line in lines) {
            // 令和X年X月X日
            val reiwaMatcher = Pattern.compile("令和\\s*([0-9０-９]{1,2})\\s*年\\s*([0-9０-９]{1,2})\\s*月\\s*([0-9０-９]{1,2})\\s*日?").matcher(line)
            if (reiwaMatcher.find()) {
                val g1 = reiwaMatcher.group(1) ?: "1"
                val g2 = reiwaMatcher.group(2) ?: "1"
                val g3 = reiwaMatcher.group(3) ?: "1"
                val rYear = normalizeDigits(g1).toIntOrNull() ?: 1
                val month = normalizeDigits(g2).toIntOrNull() ?: 1
                val day = normalizeDigits(g3).toIntOrNull() ?: 1
                return safeEpochDay(2018 + rYear, month, day)
            }

            // 2026年10月31日 / 2026/10/31 / 2026.10.31 / 2026-10-31
            val fullDateMatcher = Pattern.compile("([20][0-9]{3})[年/\\.\\-]([0-9]{1,2})[月/\\.\\-]([0-9]{1,2})日?").matcher(line)
            if (fullDateMatcher.find()) {
                val g1 = fullDateMatcher.group(1) ?: "2026"
                val g2 = fullDateMatcher.group(2) ?: "1"
                val g3 = fullDateMatcher.group(3) ?: "1"
                val year = g1.toIntOrNull() ?: 2026
                val month = g2.toIntOrNull() ?: 1
                val day = g3.toIntOrNull() ?: 1
                return safeEpochDay(year, month, day)
            }

            // 26-10-31 / 26/10/31
            val shortDateMatcher = Pattern.compile("(?<!\\d)([2-9][0-9])[\\-/\\.]([0-9]{1,2})[\\-/\\.]([0-9]{1,2})(?!\\d)").matcher(line)
            if (shortDateMatcher.find()) {
                val g1 = shortDateMatcher.group(1) ?: "26"
                val g2 = shortDateMatcher.group(2) ?: "1"
                val g3 = shortDateMatcher.group(3) ?: "1"
                val shortYear = g1.toIntOrNull() ?: 26
                val month = g2.toIntOrNull() ?: 1
                val day = g3.toIntOrNull() ?: 1
                return safeEpochDay(2000 + shortYear, month, day)
            }
        }
        return LocalDate.now().toEpochDay()
    }

    private fun safeEpochDay(year: Int, month: Int, day: Int): Long {
        return try {
            LocalDate.of(year, month.coerceIn(1, 12), day.coerceIn(1, 31)).toEpochDay()
        } catch (_: Exception) {
            LocalDate.now().toEpochDay()
        }
    }

    private fun extractTotalAmount(lines: List<String>): Int {
        val totalKeywords = listOf("合計", "お買上計", "税込計", "支払計")
        for (line in lines.reversed()) {
            if (totalKeywords.any { line.contains(it) }) {
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
            if (isExcludedLine(line)) {
                if (isDiscountLine(line) && items.isNotEmpty()) {
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

            // 単独の数量行パターン (例: "  2ｺ X 238   476" や "2点 X 238")
            val quantityMatcher = Pattern.compile("^\\s*([0-9Ool]{1,2})\\s*(?:ｺ|コ|個|点|本|枚)?\\s*[XxX×@]\\s*([0-9,]+)?(?:\\s+([0-9,]+))?").matcher(line)
            if (quantityMatcher.find()) {
                val qtyStr = normalizeDigits(quantityMatcher.group(1) ?: "1")
                val qty = qtyStr.toDoubleOrNull() ?: 1.0
                val totalStr = quantityMatcher.group(3) ?: quantityMatcher.group(2)
                val price = if (totalStr != null) extractAmountFromLine(totalStr) else 0

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

        val amount = extractAmountFromLine(line)
        if (amount <= 0) return null

        val namePart = line.replace(Regex("[\\s￥¥\\$]*[0-9Ool,]+[\\s※*＊軽内円]*$"), "").trim()
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

    private fun isDiscountLine(line: String): Boolean {
        val keywords = listOf("値引", "割引", "値引き", "割引き", "-")
        return keywords.any { line.contains(it) }
    }

    private fun isExcludedLine(line: String): Boolean {
        val excludeKeywords = listOf(
            "合計", "小計", "消費税", "内税", "外税", "税抜", "税込",
            "お預かり", "お預り", "預かり", "預り", "お釣り", "釣銭", "釣り",
            "現金", "クレジット", "ポイント", "電話番号", "TEL", "登録番号",
            "レジ番号", "担当者", "領収", "レシート", "対象額", "税率", "値引", "割引"
        )
        return excludeKeywords.any { line.contains(it) }
    }
}
