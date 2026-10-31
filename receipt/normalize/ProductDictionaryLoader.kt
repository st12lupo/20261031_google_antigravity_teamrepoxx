package receipt.normalize

/**
 * assets/products.json を商品辞書に読み込む。
 * org.json は端末内の JVM 単体テストで使えないため、必要な範囲だけの小さな JSON 読み取りを持つ。
 */
object ProductDictionaryLoader {

    /** JSON 配列の文字列から辞書項目を作る。形式が不正なら例外を投げる */
    fun parse(json: String): List<DictionaryEntry> {
        val root = MiniJson(json).parseRoot()
        val array = root as? List<*> ?: throw IllegalArgumentException("products.json must be an array")
        return array.mapNotNull { element ->
            val obj = element as? Map<*, *> ?: return@mapNotNull null
            val name = (obj["name"] as? String)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val category = (obj["category"] as? String)?.takeIf { it.isNotBlank() } ?: "日用品"
            val keywords = (obj["keywords"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            val cycle = (obj["defaultCycleDays"] as? Double)?.toInt()?.takeIf { it > 0 }
            DictionaryEntry(name, category, keywords, cycle)
        }
    }

    /**
     * カテゴリごとの既定周期(日)。そのカテゴリの項目の defaultCycleDays の中央値とする(F-3)。
     * 周期を持つ項目がないカテゴリは含めない。
     */
    fun categoryCycleDays(entries: List<DictionaryEntry>): Map<String, Double> =
        entries
            .filter { it.defaultCycleDays != null }
            .groupBy { it.category }
            .mapValues { (_, list) ->
                val sorted = list.mapNotNull { it.defaultCycleDays?.toDouble() }.sorted()
                val mid = sorted.size / 2
                if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2.0
            }

    /** オブジェクト、配列、文字列、数値、true/false/null を読む最小限の JSON パーサー */
    private class MiniJson(private val src: String) {
        private var pos = 0

        fun parseRoot(): Any? {
            val value = parseValue()
            skipWhitespace()
            if (pos != src.length) fail("unexpected trailing characters")
            return value
        }

        private fun parseValue(): Any? {
            skipWhitespace()
            if (pos >= src.length) fail("unexpected end")
            return when (val c = src[pos]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> parseString()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> if (c == '-' || c.isDigit()) parseNumber() else fail("unexpected '$c'")
            }
        }

        private fun parseObject(): Map<String, Any?> {
            val map = LinkedHashMap<String, Any?>()
            pos++
            skipWhitespace()
            if (peek() == '}') { pos++; return map }
            while (true) {
                skipWhitespace()
                val key = parseString()
                skipWhitespace()
                expect(':')
                map[key] = parseValue()
                skipWhitespace()
                when (next()) {
                    ',' -> continue
                    '}' -> return map
                    else -> fail("expected ',' or '}'")
                }
            }
        }

        private fun parseArray(): List<Any?> {
            val list = ArrayList<Any?>()
            pos++
            skipWhitespace()
            if (peek() == ']') { pos++; return list }
            while (true) {
                list.add(parseValue())
                skipWhitespace()
                when (next()) {
                    ',' -> continue
                    ']' -> return list
                    else -> fail("expected ',' or ']'")
                }
            }
        }

        private fun parseString(): String {
            expect('"')
            val sb = StringBuilder()
            while (true) {
                if (pos >= src.length) fail("unterminated string")
                when (val c = src[pos++]) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (pos >= src.length) fail("unterminated escape")
                        when (val e = src[pos++]) {
                            '"', '\\', '/' -> sb.append(e)
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (pos + 4 > src.length) fail("bad unicode escape")
                                sb.append(src.substring(pos, pos + 4).toInt(16).toChar())
                                pos += 4
                            }
                            else -> fail("bad escape")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun parseNumber(): Double {
            val start = pos
            while (pos < src.length && (src[pos].isDigit() || src[pos] in "+-.eE")) pos++
            return src.substring(start, pos).toDoubleOrNull() ?: fail("bad number")
        }

        private fun literal(word: String, value: Any?): Any? {
            if (!src.startsWith(word, pos)) fail("bad literal")
            pos += word.length
            return value
        }

        private fun skipWhitespace() {
            while (pos < src.length && src[pos].isWhitespace()) pos++
        }

        private fun peek(): Char? = src.getOrNull(pos)

        private fun next(): Char? = src.getOrNull(pos++)

        private fun expect(c: Char) {
            if (next() != c) fail("expected '$c'")
        }

        private fun fail(message: String): Nothing =
            throw IllegalArgumentException("Invalid JSON at $pos: $message")
    }
}
