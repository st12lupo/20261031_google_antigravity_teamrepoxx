package receipt.normalize

import domain.receipt.NormalizationResult
import domain.receipt.ProductNormalizer

data class DictionaryEntry(
    val normalizedName: String,
    val category: String,
    val keywords: List<String> = emptyList()
)

class DefaultProductNormalizer(
    initialDictionary: List<DictionaryEntry> = defaultProductDictionary(),
    initialAliases: Map<String, String> = emptyMap()
) : ProductNormalizer {

    private val dictionary: MutableList<DictionaryEntry> = initialDictionary.toMutableList()
    private val aliasMap: MutableMap<String, String> = initialAliases.toMutableMap()

    fun registerAlias(alias: String, normalizedName: String) {
        val cleaned = cleanText(alias)
        aliasMap[cleaned] = normalizedName
        aliasMap[alias.trim()] = normalizedName
    }

    override fun cleanText(rawText: String): String {
        if (rawText.isBlank()) return ""

        // 1. 半角カナを全角カタカナに変換（濁点・半濁点結合）
        var text = halfWidthKanaToFullWidth(rawText)

        // 2. 全角英数字記号を半角に変換
        text = fullWidthAsciiToHalfWidth(text)

        // 3. 似た文字の混同を正規化（漢数字「一」と長音符「ー」など）
        text = normalizeConfusableChars(text)

        // 4. 括弧内の文字列・記号の除去
        text = text.replace(Regex("[\\(（\\[【].*?[\\)）\\]】]"), " ")

        // 5. 容量・入数表記の除去 (例: 12R, 1L, 500ml, 200g, 5個入, 3P, etc.)
        text = text.replace(Regex("(?i)\\d+(?:\\.\\d+)?\\s*(?:ml|l|g|kg|r|p|ロール|個入?|コ入?|パック|本|枚|組|袋|缶|箱|カット)"), " ")

        // 6. 不要な記号の除去
        text = text.replace(Regex("[※*＊★◆●■▲▼#￥¥\\$@:：\\-_/／~〜]"), " ")

        // 7. 連続する空白を1つにまとめトリム
        return text.replace(Regex("\\s+"), " ").trim()
    }

    override fun normalize(rawText: String): NormalizationResult {
        val cleaned = cleanText(rawText)
        if (cleaned.isBlank()) {
            return NormalizationResult(
                normalizedName = rawText.trim(),
                category = "日用品",
                isMatchedInDictionary = false
            )
        }

        // 別名辞書のチェック (生テキストまたはクリーンテキスト)
        val aliasTarget = aliasMap[rawText.trim()] ?: aliasMap[cleaned]
        if (aliasTarget != null) {
            val dictEntry = dictionary.firstOrNull { it.normalizedName == aliasTarget }
            return NormalizationResult(
                normalizedName = aliasTarget,
                category = dictEntry?.category ?: "日用品",
                isMatchedInDictionary = true
            )
        }

        // 商品辞書の完全一致・キーワード一致
        val exactMatch = dictionary.firstOrNull {
            it.normalizedName.equals(cleaned, ignoreCase = true) ||
                    it.keywords.any { kw -> kw.equals(cleaned, ignoreCase = true) }
        }
        if (exactMatch != null) {
            return NormalizationResult(
                normalizedName = exactMatch.normalizedName,
                category = exactMatch.category,
                isMatchedInDictionary = true
            )
        }

        // 商品辞書の部分一致（キーワードが含まれるか）
        val partialMatch = dictionary
            .filter { entry ->
                cleaned.contains(entry.normalizedName) || entry.keywords.any { kw -> cleaned.contains(kw) }
            }
            .maxByOrNull { entry ->
                val longestKw = entry.keywords.filter { cleaned.contains(it) }.maxByOrNull { it.length }?.length ?: 0
                maxOf(if (cleaned.contains(entry.normalizedName)) entry.normalizedName.length else 0, longestKw)
            }

        if (partialMatch != null) {
            return NormalizationResult(
                normalizedName = partialMatch.normalizedName,
                category = partialMatch.category,
                isMatchedInDictionary = true
            )
        }

        // 辞書不一致の場合
        return NormalizationResult(
            normalizedName = cleaned,
            category = "日用品",
            isMatchedInDictionary = false
        )
    }

    private fun normalizeConfusableChars(str: String): String {
        var res = str
        // カタカナに続く「一」(漢数字のいち) を長音符「ー」に変換 (例: ペーパー、ティシュー、ペーパ一)
        res = res.replace(Regex("(?<=[\\u30A0-\\u30FF])一"), "ー")
        res = res.replace("―", "ー")
        res = res.replace("‐", "ー")
        res = res.replace("－", "ー")
        return res
    }

    private fun fullWidthAsciiToHalfWidth(str: String): String {
        val sb = java.lang.StringBuilder()
        for (c in str) {
            if (c in '！'..'～') {
                sb.append((c - 0xFEE0))
            } else if (c == '　') {
                sb.append(' ')
            } else {
                sb.append(c)
            }
        }
        return sb.toString()
    }

    private fun halfWidthKanaToFullWidth(str: String): String {
        // 半角カタカナ変換マップ
        val dakutenMap = mapOf(
            "ｶﾞ" to "ガ", "ｷﾞ" to "ギ", "ｸﾞ" to "グ", "ｹﾞ" to "ゲ", "ｺﾞ" to "ゴ",
            "ｻﾞ" to "ザ", "ｼﾞ" to "ジ", "ｽﾞ" to "ズ", "ｾﾞ" to "ゼ", "ｿﾞ" to "ゾ",
            "ﾀﾞ" to "ダ", "ﾁﾞ" to "ヂ", "ﾂﾞ" to "ヅ", "ﾃﾞ" to "デ", "ﾄﾞ" to "ド",
            "ﾊﾞ" to "バ", "ﾋﾞ" to "ビ", "ﾌﾞ" to "ブ", "ﾍﾞ" to "ベ", "ﾎﾞ" to "ボ",
            "ｳﾞ" to "ヴ"
        )
        val handakutenMap = mapOf(
            "ﾊﾟ" to "パ", "ﾋﾟ" to "ピ", "ﾌﾟ" to "プ", "ﾍﾟ" to "ペ", "ﾎﾟ" to "ポ"
        )
        val singleMap = mapOf(
            'ｱ' to 'ア', 'ｲ' to 'イ', 'ｳ' to 'ウ', 'ｴ' to 'エ', 'ｵ' to 'オ',
            'ｶ' to 'カ', 'ｷ' to 'キ', 'ｸ' to 'ク', 'ｹ' to 'ケ', 'ｺ' to 'コ',
            'ｻ' to 'サ', 'ｼ' to 'シ', 'ｽ' to 'ス', 'ｾ' to 'セ', 'ｿ' to 'ソ',
            'ﾀ' to 'タ', 'ﾁ' to 'チ', 'ﾂ' to 'ツ', 'ﾃ' to 'テ', 'ﾄ' to 'ト',
            'ﾅ' to 'ナ', 'ﾆ' to 'ニ', 'ﾇ' to 'ヌ', 'ﾈ' to 'ネ', 'ﾉ' to 'ノ',
            'ﾊ' to 'ハ', 'ﾋ' to 'ヒ', 'ﾌ' to 'フ', 'ﾍ' to 'ヘ', 'ﾎ' to 'ホ',
            'ﾏ' to 'マ', 'ﾐ' to 'ミ', 'ﾑ' to 'ム', 'ﾒ' to 'メ', 'ﾓ' to 'モ',
            'ﾔ' to 'ヤ', 'ﾕ' to 'ユ', 'ﾖ' to 'ヨ',
            'ﾗ' to 'ラ', 'ﾘ' to 'リ', 'ﾙ' to 'ル', 'ﾚ' to 'レ', 'ﾛ' to 'ロ',
            'ﾜ' to 'ワ', 'ｦ' to 'ヲ', 'ﾝ' to 'ン',
            'ｧ' to 'ァ', 'ｨ' to 'ィ', 'ｩ' to 'ゥ', 'ｪ' to 'ェ', 'ｫ' to 'ォ',
            'ｯ' to 'ッ', 'ｬ' to 'ャ', 'ｭ' to 'ュ', 'ｮ' to 'ョ',
            'ｰ' to 'ー', '･' to '・'
        )

        var res = str
        for ((k, v) in dakutenMap) {
            res = res.replace(k, v)
        }
        for ((k, v) in handakutenMap) {
            res = res.replace(k, v)
        }

        val sb = java.lang.StringBuilder()
        for (c in res) {
            val mapped = singleMap[c]
            if (mapped != null) {
                sb.append(mapped)
            } else {
                sb.append(c)
            }
        }
        return sb.toString()
    }

    companion object {
        fun defaultProductDictionary(): List<DictionaryEntry> = listOf(
            DictionaryEntry("食器用洗剤", "日用品", listOf("ショッキセンザイ", "食器洗剤", "台所用洗剤")),
            DictionaryEntry("トイレットペーパー", "日用品", listOf("トイレロール", "TP")),
            DictionaryEntry("ティッシュペーパー", "日用品", listOf("ティッシュ", "ティシュー", "ボックスティッシュ")),
            DictionaryEntry("洗濯用洗剤", "日用品", listOf("センタクセンザイ", "衣料用洗剤", "アタック", "ボールド")),
            DictionaryEntry("柔軟剤", "日用品", listOf("ジュウナンザイ", "ソフラン", "レノア")),
            DictionaryEntry("シャンプー", "日用品", listOf("ヘアシャンプー")),
            DictionaryEntry("コンディショナー", "日用品", listOf("リンス", "トリートメント")),
            DictionaryEntry("ボディソープ", "日用品", listOf("ボディーソープ", "石鹸", "せっけん")),
            DictionaryEntry("歯磨き粉", "日用品", listOf("ハミガキ", "歯みがき粉", "デンタルペースト")),
            DictionaryEntry("ハンドソープ", "日用品", listOf("手洗いソープ", "泡ハンドソープ")),
            DictionaryEntry("ラップ", "日用品", listOf("サランラップ", "クレラップ", "食品用ラップ")),
            DictionaryEntry("アルミホイル", "日用品", listOf("ホイル")),
            DictionaryEntry("ゴミ袋", "日用品", listOf("ポリ袋", "ごみ袋")),
            DictionaryEntry("牛乳", "定番食品", listOf("ギュウニュウ", "成分無調整牛乳", "低脂肪乳")),
            DictionaryEntry("卵", "定番食品", listOf("タマゴ", "たまご", "鶏卵")),
            DictionaryEntry("食パン", "定番食品", listOf("パン", "トースト")),
            DictionaryEntry("米", "定番食品", listOf("コメ", "お米", "精米", "白米")),
            DictionaryEntry("醤油", "定番食品", listOf("しょうゆ", "ショウユ")),
            DictionaryEntry("味噌", "定番食品", listOf("みそ", "ミソ")),
            DictionaryEntry("食用油", "定番食品", listOf("サラダ油", "キャノーラ油", "オリーブオイル")),
            DictionaryEntry("鶏むね肉", "肉・魚", listOf("トリムネニク", "若鶏むね", "鶏胸肉"))
        )
    }
}
