package domain.receipt

import domain.model.ParsedReceipt

interface ReceiptParser {
    /**
     * OCRで読み取った行文字列から店名、日付、商品名、数量、金額、合計金額を抽出する。
     * 合計、小計、税、お預かり、釣銭などの行は商品から除外する。
     */
    fun parse(ocrLines: List<String>): ParsedReceipt
}
