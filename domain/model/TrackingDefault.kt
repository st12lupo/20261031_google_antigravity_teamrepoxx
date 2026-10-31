package domain.model

/**
 * レシートの商品を予測対象にするかの既定。スキャン(RealReceiptScanner)と、確認画面で名前を直したとき
 * (RealReceiptReviewService.suggestTracked)が、この 1 か所の判定を使う(P-3, P-4, F-6)。
 */
object TrackingDefault {

    /**
     * - 未登録の商品: 辞書に一致するときだけ対象
     * - 登録済みの商品: 予測対象のまま、または「もう買わない」の印があれば対象。
     *   辞書にない商品を OFF で保存した場合や、詳細画面で OFF にした場合は、次のレシートでも対象外のまま
     */
    fun isTrackedByDefault(registered: Product?, matchedInDictionary: Boolean): Boolean =
        if (registered == null) matchedInDictionary else registered.isTracked || registered.stoppedByUser
}
