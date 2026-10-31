package com.example.receiptreminder.di

import android.content.Context
import android.content.SharedPreferences
import com.example.receiptreminder.data.bridge.DataUpdateTrigger
import com.example.receiptreminder.data.bridge.RealGeofenceManager
import com.example.receiptreminder.data.bridge.RealNotificationSettingsRepository
import com.example.receiptreminder.data.bridge.RealProductRepository
import com.example.receiptreminder.data.bridge.RealPurchaseRepository
import com.example.receiptreminder.data.bridge.RealReceiptReviewService
import com.example.receiptreminder.data.bridge.RealReceiptScanner
import com.example.receiptreminder.data.bridge.RealShoppingListRepository
import com.example.receiptreminder.data.bridge.RealStoreRepository
import com.example.receiptreminder.domain.repository.GeofenceManager
import com.example.receiptreminder.domain.repository.NotificationSettingsRepository
import com.example.receiptreminder.domain.repository.ProductRepository
import com.example.receiptreminder.domain.repository.PurchaseRepository
import com.example.receiptreminder.domain.repository.ReceiptReviewService
import com.example.receiptreminder.domain.repository.ReceiptScanner
import com.example.receiptreminder.domain.repository.ShoppingListRepository
import com.example.receiptreminder.domain.repository.StoreRepository
import com.google.android.gms.location.LocationServices
import data.AppDatabase
import data.DefaultReminderRepository
import domain.repository.ReminderRepository
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import receipt.normalize.DefaultProductNormalizer
import receipt.normalize.DictionaryEntry
import receipt.parser.DefaultReceiptParser
import receipt.scan.DefaultReceiptScanner
import kotlin.coroutines.resume

/**
 * 実装（Room, Play Services, ML Kit）で配線された本番用 AppContainer。
 * 手動 DI により依存性を組み立てる。
 */
class RealAppContainer(
    private val context: Context,
    private val database: AppDatabase = AppDatabase.getInstance(context),
    private val sharedPreferences: SharedPreferences? = context.getSharedPreferences("receipt_reminder_prefs", Context.MODE_PRIVATE),
    private val trigger: DataUpdateTrigger = DataUpdateTrigger()
) : AppContainer {

    val reminderRepository: ReminderRepository by lazy {
        DefaultReminderRepository(
            productDao = database.productDao(),
            purchaseDao = database.purchaseDao(),
            receiptDao = database.receiptDao(),
            storeDao = database.storeDao()
        )
    }

    override val notificationSettingsRepository: NotificationSettingsRepository by lazy {
        RealNotificationSettingsRepository(sharedPreferences = sharedPreferences)
    }

    override val shoppingListRepository: ShoppingListRepository by lazy {
        RealShoppingListRepository(
            reminderRepository = reminderRepository,
            trigger = trigger
        )
    }

    override val productRepository: ProductRepository by lazy {
        RealProductRepository(
            reminderRepository = reminderRepository,
            trigger = trigger
        )
    }

    override val purchaseRepository: PurchaseRepository by lazy {
        RealPurchaseRepository(
            reminderRepository = reminderRepository,
            trigger = trigger
        )
    }

    override val storeRepository: StoreRepository by lazy {
        RealStoreRepository(
            reminderRepository = reminderRepository,
            currentLocationProvider = { getCurrentLocation(context) },
            trigger = trigger
        )
    }

    private val ocrScanner: DefaultReceiptScanner by lazy {
        DefaultReceiptScanner(context)
    }

    private val productNormalizer: DefaultProductNormalizer by lazy {
        val dictionary = loadProductDictionaryFromAssets(context)
        DefaultProductNormalizer(initialDictionary = dictionary)
    }

    private val receiptParser: DefaultReceiptParser by lazy {
        DefaultReceiptParser()
    }

    override val receiptReviewService: ReceiptReviewService by lazy {
        RealReceiptReviewService(
            reminderRepository = reminderRepository,
            ocrScanner = ocrScanner,
            normalizer = productNormalizer,
            trigger = trigger
        )
    }

    override val receiptScanner: ReceiptScanner by lazy {
        RealReceiptScanner(
            ocrScanner = ocrScanner,
            parser = receiptParser,
            normalizer = productNormalizer,
            reviewService = receiptReviewService as? RealReceiptReviewService
        )
    }

    override val geofenceManager: GeofenceManager by lazy {
        RealGeofenceManager(
            context = context,
            storeRepository = storeRepository,
            shoppingListRepository = shoppingListRepository,
            notificationSettingsRepository = notificationSettingsRepository
        )
    }

    companion object {
        /**
         * assets/products.json から商品辞書エントリを読み込む。
         */
        fun loadProductDictionaryFromAssets(context: Context): List<DictionaryEntry> {
            return try {
                val jsonString = context.assets.open("products.json").bufferedReader().use { it.readText() }
                val jsonArray = JSONArray(jsonString)
                val entries = mutableListOf<DictionaryEntry>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val name = obj.getString("name")
                    val category = obj.optString("category", "日用品")
                    val keywordsArray = obj.optJSONArray("keywords")
                    val keywords = mutableListOf<String>()
                    if (keywordsArray != null) {
                        for (j in 0 until keywordsArray.length()) {
                            keywords.add(keywordsArray.getString(j))
                        }
                    }
                    entries.add(DictionaryEntry(name, category, keywords))
                }
                if (entries.isNotEmpty()) entries else DefaultProductNormalizer.defaultProductDictionary()
            } catch (_: Exception) {
                DefaultProductNormalizer.defaultProductDictionary()
            }
        }

        /**
         * FusedLocationProviderClient から現在地を取得する。
         * 権限拒否時やエラー時はクラッシュせず既定座標（東京駅）を返す。
         */
        private suspend fun getCurrentLocation(context: Context): Pair<Double, Double>? {
            return try {
                val client = LocationServices.getFusedLocationProviderClient(context)
                val location = suspendCancellableCoroutine<android.location.Location?> { cont ->
                    client.lastLocation
                        .addOnSuccessListener { cont.resume(it) }
                        .addOnFailureListener { cont.resume(null) }
                }
                if (location != null) {
                    location.latitude to location.longitude
                } else {
                    35.6812 to 139.7671
                }
            } catch (_: SecurityException) {
                35.6812 to 139.7671
            } catch (_: Exception) {
                35.6812 to 139.7671
            }
        }
    }
}
