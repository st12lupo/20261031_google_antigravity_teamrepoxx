package domain.receipt

data class NormalizationResult(
    val normalizedName: String,
    val category: String,
    val isMatchedInDictionary: Boolean
)

interface ProductNormalizer {
    /**
     * 全半角やカナの違い、記号、容量表記を吸収して、同じ商品名とカテゴリにまとめる。
     */
    fun normalize(rawText: String): NormalizationResult

    /**
     * 記号、容量表記(ml, g, kg, 個, パック等)、ノイズ語を除去して正規化する。
     */
    fun cleanText(rawText: String): String
}
