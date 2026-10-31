package receipt.scan

import android.content.ContentResolver
import android.content.Context
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ReceiptScannerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var dummyContext: Context
    private lateinit var scanner: DefaultReceiptScanner

    @Before
    fun setup() {
        dummyContext = object : android.content.ContextWrapper(null) {
            override fun getContentResolver(): ContentResolver {
                throw UnsupportedOperationException("Not needed for file tests")
            }
        }
        scanner = DefaultReceiptScanner(dummyContext)
    }

    @Test
    fun `deleteImage deletes temporary receipt image file successfully (R-6)`() {
        // 1. レシート撮影時の一時画像ファイルを模倣して作成
        val tempImageFile = tempFolder.newFile("receipt_temp_001.jpg")
        tempImageFile.writeText("dummy image binary data")
        assertTrue("Image file should exist initially", tempImageFile.exists())

        // 2. deleteImage(fileUri) を実行 (要件 R-6: 保存後にレシート画像を削除)
        scanner.deleteImage(tempImageFile.toURI().toString())

        // 3. 検証: ファイルが削除され端末内に残らないこと
        assertFalse("Receipt image file must be deleted after saving", tempImageFile.exists())
    }

    @Test
    fun `deleteImage handles raw file path string correctly (R-6)`() {
        val tempImageFile = tempFolder.newFile("receipt_temp_002.jpg")
        tempImageFile.writeText("dummy image binary data 2")
        assertTrue(tempImageFile.exists())

        scanner.deleteImage(tempImageFile.absolutePath)

        assertFalse(tempImageFile.exists())
    }

    @Test
    fun `deleteImage does not throw exception when file does not exist or invalid uri`() {
        // 存在しないファイルや空文字列を渡してもクラッシュしないことを確認
        scanner.deleteImage("file:///non_existent_path_999.jpg")
        scanner.deleteImage("")
        scanner.deleteImage("invalid:uri:format")
    }
}
