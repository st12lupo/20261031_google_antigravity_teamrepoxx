package receipt.normalize

import domain.receipt.NormalizationResult
import domain.receipt.ProductNormalizer

data class DictionaryEntry(
    val normalizedName: String,
    val category: String,
    val keywords: List<String> = emptyList(),
    /** products.json の defaultCycleDays。辞書にない場合は null */
    val defaultCycleDays: Int? = null
)

class DefaultProductNormalizer(
    initialDictionary: List<DictionaryEntry> = defaultProductDictionary(),
    initialAliases: Map<String, String> = emptyMap()
) : ProductNormalizer {

    private val dictionary: List<DictionaryEntry> = initialDictionary.toList()
    private val aliasMap: MutableMap<String, String> = mutableMapOf()

    /** 辞書の各項目の照合用キー(全角カナ・ひらがなをカタカナに統一・小文字化したもの) */
    private class IndexedEntry(val entry: DictionaryEntry, val keys: List<String>)

    private val index: List<IndexedEntry> = dictionary.map { entry ->
        val keys = (listOf(entry.normalizedName) + entry.keywords)
            .map { foldForMatch(it) }
            .filter { it.isNotEmpty() }
            .distinct()
        IndexedEntry(entry, keys)
    }

    init {
        initialAliases.forEach { (alias, name) -> registerAlias(alias, name) }
    }

    /**
     * 確認画面での修正を別名として覚える(P-2)。
     * 照合は cleanText と同じ正規化を通したキーで行うので、全半角や容量表記が違っても適用される。
     */
    fun registerAlias(alias: String, normalizedName: String) {
        val key = aliasKey(alias)
        if (key.isEmpty() || normalizedName.isBlank()) return
        aliasMap[key] = normalizedName
    }

    /** 別名を 1 件取り消す。取り消した別名の行き先(商品名)を返し、なければ null */
    fun removeAlias(alias: String): String? = aliasMap.remove(aliasKey(alias))

    /** 覚えた別名をすべて忘れる(全データの削除用) */
    fun clearAliases() {
        aliasMap.clear()
    }

    private fun aliasKey(text: String): String = foldForMatch(cleanText(text))

    /**
     * 別名の照合キー。メモリ上の別名も Room に保存する文字列も、このキーに揃える(P-2)。
     * 全半角、かな・カナ、容量表記が違う入力は同じキーになる。
     */
    fun aliasKeyOf(text: String): String = aliasKey(text)

    /**
     * 完全一致だけの名寄せ。別名と、辞書の名前・キーワードとの完全一致(畳み込み後)だけを見る。
     * 部分一致は使わない。確認画面での修正名や手動追加のように、人が入力した名前に使う。
     * 一致しなければ null(呼び出し側は cleanText した名前をそのまま使う)。
     */
    fun resolveExact(rawText: String): NormalizationResult? {
        val cleaned = cleanText(rawText)
        if (cleaned.isBlank()) return null
        val key = foldForMatch(cleaned)

        val aliasTarget = aliasMap[key]
        if (aliasTarget != null) {
            val dictEntry = dictionary.firstOrNull { it.normalizedName == aliasTarget }
            return NormalizationResult(
                normalizedName = aliasTarget,
                category = dictEntry?.category ?: "日用品",
                isMatchedInDictionary = dictEntry != null
            )
        }
        return index.firstOrNull { key in it.keys }?.toResult()
    }

    override fun cleanText(rawText: String): String {
        if (rawText.isBlank()) return ""

        // 1. 半角カナを全角カタカナに変換（濁点・半濁点結合）
        var text = halfWidthKanaToFullWidth(rawText)

        // 2. 似た文字の混同を正規化（漢数字「一」と長音符「ー」など）。
        //    全角ハイフン「－」は次の半角化で「-」になり記号として消えるので、その前に長音へ直す
        text = normalizeConfusableChars(text)

        // 3. 全角英数字記号を半角に変換
        text = fullWidthAsciiToHalfWidth(text)

        // 4. 括弧内の文字列・記号の除去
        text = text.replace(Regex("[\\(（\\[【].*?[\\)）\\]】]"), " ")

        // 5. 容量・入数表記の除去 (例: 12R, 1L, 500ml, 200g, 5個入, 3P, etc.)
        //    数字+単位が語の終わり(直後が空白・記号・文字列の終わり)のときだけ除く。
        //    「1本満足バー」「5本指ソックス」のように、単位の直後に文字が続く名前の一部は削らない
        text = text.replace(Regex("(?i)\\d+(?:\\.\\d+)?\\s*(?:ml|l|g|kg|r|p|ロール|個入?|コ入?|パック|本|枚|組|袋|缶|箱|カット)(?:入り|入|組|パック)?(?=[\\s\\p{P}\\p{S}]|$)"), " ")

        // 6. 不要な記号の除去
        text = text.replace(Regex("[※*＊★◆●■▲▼#￥¥\\$@:：\\-_/／~〜―‐‑−]"), " ")

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
        val key = foldForMatch(cleaned)

        // 別名 (P-2) と辞書の完全一致
        resolveExact(rawText)?.let { return it }

        // 商品辞書の部分一致。誤判定を避けるため matchesToken の規則に従う。
        // 空白で区切った語ごとに最長のキーワードで項目を決め、語どうしで別の項目になるときは
        // どちらとも決められないので一致なしにする(「コーヒー 牛乳」など)
        val matched = LinkedHashSet<IndexedEntry>()
        for (token in key.split(' ')) {
            if (token.isEmpty()) continue
            var best: IndexedEntry? = null
            var bestLength = 0
            for (candidate in index) {
                val length = candidate.keys
                    .filter { matchesToken(token, it) }
                    .maxOfOrNull { it.length } ?: continue
                if (length > bestLength) {
                    best = candidate
                    bestLength = length
                }
            }
            if (best != null) matched += best
        }
        if (matched.size == 1) {
            return matched.first().toResult()
        }

        // 辞書不一致の場合
        return NormalizationResult(
            normalizedName = cleaned,
            category = "日用品",
            isMatchedInDictionary = false
        )
    }

    private fun IndexedEntry.toResult() = NormalizationResult(
        normalizedName = entry.normalizedName,
        category = entry.category,
        isMatchedInDictionary = true
    )

    /**
     * 照合用キーの部分一致。1 つの語(空白で区切った単位)とキーワードを比べる。
     * 除外語を増やして追いかけず、キーワードの種類ごとに一致のしかたを絞る。
     * - 語とキーワードが同じなら一致
     * - カナだけのキーワード(サトウ、ラップ、リンス、チーズ)は語頭にあり、直後が英数字のときだけ一致。
     *   かな語は別の言葉の一部になりやすい(サトウのごはん、ストラップ、プリンス)ので、語中・語末は見ない
     * - 漢字などを含む 3 文字以上のキーワードは語に含まれていればよい。ただし直前・直後が別の食品を表す語
     *   (バタークッキー、ピーナッツバター)のとき、キーワードがカナで終わり直後もカナが続くときは一致にしない
     * - 漢字を含む 1〜2 文字のキーワード(塩、米、牛乳、豆腐)は、語の末尾にあり、前が国産・ミニなどの
     *   修飾語のときだけ一致とする(「国産米」は米、「塩サバ」「麻婆豆腐」「いちご牛乳」は一致なし)
     * - それ以外の短いキーワードは完全一致だけ
     */
    private fun matchesToken(token: String, keyword: String): Boolean {
        if (token == keyword) return true
        if (keyword.isEmpty() || token.length <= keyword.length) return false

        if (keyword.all { isKatakana(it) }) {
            if (!token.startsWith(keyword)) return false
            val next = token[keyword.length]
            return next in 'a'..'z' || next in '0'..'9'
        }

        if (keyword.length >= MIN_CONTAINS_LENGTH) {
            val endsWithKana = isKatakana(keyword.last())
            var from = token.indexOf(keyword)
            while (from >= 0) {
                val before = token.substring(0, from)
                val after = token.substring(from + keyword.length)
                val wordContinues = endsWithKana && after.isNotEmpty() && isKatakana(after[0])
                if (!wordContinues &&
                    EXCLUDED_AFTER.none { after.startsWith(it) } &&
                    EXCLUDED_BEFORE.none { before.endsWith(it) }
                ) return true
                from = token.indexOf(keyword, from + 1)
            }
            return false
        }

        if (containsHan(keyword) && token.endsWith(keyword)) {
            return token.dropLast(keyword.length) in QUALIFIERS
        }
        return false
    }

    /** 畳み込み後(ひらがなはカタカナになっている)のカタカナ・長音符・中黒 */
    private fun isKatakana(c: Char): Boolean = c in '゠'..'ヿ'

    private fun containsHan(text: String): Boolean =
        text.any { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN }

    private fun normalizeConfusableChars(str: String): String {
        var res = str
        // カタカナに続く「一」(漢数字のいち) を長音符「ー」に変換 (例: ペーパー、ティシュー、ペーパ一)
        res = res.replace(Regex("(?<=[\u30A1-\u30FA\u30FC])一"), "ー")
        // かな(カタカナ・ひらがな)の直後のハイフン類 (半角ｰ は半角カナの変換で済み) は長音符にする (例: シャンプ－)
        res = res.replace(Regex("(?<=[\u3041-\u3096\u30A1-\u30FA\u30FC])[－―‐‑−]"), "ー")
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

    companion object {
        private const val MIN_CONTAINS_LENGTH = 3

        /** 1〜2 文字の漢字キーワードの前に付いてよい修飾語 (国産米、ミニ牛乳、ゆで卵)。畳み込み後の形で比べる */
        private val QUALIFIERS: Set<String> = listOf(
            "国産", "国内産", "有機", "特選", "無添加", "北海道", "ミニ", "ゆで", "茹で", "新", "徳用", "業務用"
        ).map { foldForMatch(it) }.toSet()

        /** キーワードの直後にあると別の食品になる語 (バター + クッキー など) */
        private val EXCLUDED_AFTER = listOf(
            "クッキー", "ビスケット", "サブレ", "ケーキ", "クラッカー", "アイス", "キャンディ",
            "ゼリー", "せんべい", "センベイ", "煎餅", "ラーメン", "ポテトチップ"
        ).map { foldForMatch(it) }

        /** キーワードの直前にあると別の食品になる語 (ピーナッツ + バター など) */
        private val EXCLUDED_BEFORE = listOf(
            "ピーナッツ", "ピーナツ", "アーモンド", "ガーリック", "チョコ", "シナモン", "メープル"
        ).map { foldForMatch(it) }

        /**
         * 照合用の畳み込み。半角カナ→全角、全角英数→半角、ひらがな→カタカナ、英字は小文字にする。
         * 辞書のキーワードにも入力にも同じ処理をかけて比較する。
         */
        fun foldForMatch(text: String): String {
            val sb = StringBuilder()
            for (c in halfWidthKanaToFullWidth(text)) {
                val h = when {
                    c in '！'..'～' -> (c - 0xFEE0)
                    c == '　' -> ' '
                    else -> c
                }
                val k = if (h in 'ぁ'..'ゖ') (h + 0x60) else h
                sb.append(k.lowercaseChar())
            }
            return sb.toString().replace(Regex("\\s+"), " ").trim()
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
