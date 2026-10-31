package com.example.receiptreminder

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ManifestAndIntentTest {
    private fun appFile(path: String): File =
        listOf(File(path), File("app/$path")).first { it.exists() }

    @Test
    fun openShoppingListAction_isRecognized() {
        assertTrue(OpenShoppingList.matches(OpenShoppingList.ACTION))
        assertFalse(OpenShoppingList.matches(null))
        assertFalse(OpenShoppingList.matches("android.intent.action.MAIN"))
    }

    @Test
    fun backupAndDeviceTransferAreDisabled() {
        val manifest = appFile("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android:allowBackup=\"false\""))
        assertTrue(manifest.contains("android:fullBackupContent=\"false\""))
        assertTrue(manifest.contains("android:dataExtractionRules=\"@xml/data_extraction_rules\""))
        val rules = appFile("src/main/res/xml/data_extraction_rules.xml").readText()
        for (section in listOf("cloud-backup", "device-transfer")) {
            val body = rules.substringAfter("<$section>").substringBefore("</$section>")
            for (domain in listOf("root", "file", "database", "sharedpref", "external")) {
                assertTrue("$section / $domain", body.contains("domain=\"$domain\" path=\".\""))
            }
            assertFalse(body.contains("<include"))
        }
    }

    @Test
    fun notificationDoesNotClearTheTask() {
        val sender = appFile(
            "src/main/java/com/example/receiptreminder/data/bridge/AndroidStoreNotificationSender.kt"
        ).readText()
        assertFalse(sender.contains("FLAG_ACTIVITY_CLEAR_TASK"))
        assertTrue(sender.contains("FLAG_ACTIVITY_SINGLE_TOP"))
    }
}
