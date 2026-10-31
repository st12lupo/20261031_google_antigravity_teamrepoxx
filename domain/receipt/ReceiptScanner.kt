package domain.receipt

interface ReceiptScanner {
    /**
     * レシート画像を端末内で文字認識し、行ごとの文字列リストを返す。
     * APIキーや課金、外部サーバー通信は行わない。
     */
    suspend fun recognizeText(imageUriString: String): List<String>

    /**
     * 保存後にレシート画像ファイルを削除し、端末内に画像を残さないようにする (R-6)。
     */
    fun deleteImage(imageUriString: String)
}
