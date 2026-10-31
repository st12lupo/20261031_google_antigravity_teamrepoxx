package receipt.scan

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import domain.receipt.ReceiptScanner
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class DefaultReceiptScanner(
    private val context: Context,
    customRecognizer: TextRecognizer? = null
) : ReceiptScanner {

    private val recognizer: TextRecognizer by lazy {
        customRecognizer ?: TextRecognition.getClient(
            JapaneseTextRecognizerOptions.Builder().build()
        )
    }

    /**
     * レシート画像をオンデバイス(端末内)の ML Kit 日本語認識で解析し、行単位の文字列リストを返す。
     * 外部サーバーへの画像・データ送信は一切行わない (プライバシー要件準拠)。
     */
    override suspend fun recognizeText(imageUriString: String): List<String> {
        val uri = Uri.parse(imageUriString)
        val image = InputImage.fromFilePath(context, uri)

        return suspendCancellableCoroutine { continuation ->
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val lines = mutableListOf<String>()
                    for (block in visionText.textBlocks) {
                        for (line in block.lines) {
                            val text = line.text.trim()
                            if (text.isNotEmpty()) {
                                lines.add(text)
                            }
                        }
                    }
                    continuation.resume(lines)
                }
                .addOnFailureListener { exception ->
                    continuation.resumeWithException(exception)
                }
        }
    }

    /**
     * 保存後にレシート画像ファイルを端末から完全に削除する (要件 R-6)。
     * 端末内にレシート画像を残さず、プライバシーを保護する。
     */
    override fun deleteImage(imageUriString: String) {
        if (imageUriString.isBlank()) return
        try {
            val javaUri = runCatching { java.net.URI(imageUriString) }.getOrNull()
            if (javaUri != null && javaUri.scheme.equals("file", ignoreCase = true)) {
                val file = runCatching { File(javaUri) }.getOrNull() ?: File(javaUri.path)
                if (file.exists()) {
                    file.delete()
                }
            } else if (imageUriString.startsWith("content://", ignoreCase = true)) {
                val androidUri = Uri.parse(imageUriString)
                context.contentResolver.delete(androidUri, null, null)
            } else {
                val file = File(imageUriString)
                if (file.exists()) {
                    file.delete()
                }
            }
        } catch (_: Exception) {
            // 画像削除時の例外でアプリをクラッシュさせない
        }
    }
}
