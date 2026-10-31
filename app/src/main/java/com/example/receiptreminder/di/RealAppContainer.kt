package com.example.receiptreminder.di

import android.content.Context
import android.content.SharedPreferences
import com.example.receiptreminder.data.bridge.DataUpdateTrigger
import com.example.receiptreminder.data.bridge.LearnedAliasStore
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
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import data.AppDatabase
import androidx.room.withTransaction
import data.DefaultReminderRepository
import data.TransactionRunner
import domain.repository.ReminderRepository
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import receipt.normalize.DefaultProductNormalizer
import receipt.normalize.DictionaryEntry
import receipt.normalize.ProductDictionaryLoader
import prediction.DefaultConsumptionPredictor
import receipt.parser.DefaultReceiptParser
import receipt.scan.DefaultReceiptScanner
import geofence.GeofenceSyncGate
import geofence.PrefsSyncStateStore
import geofence.geofenceEnvironment
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
            storeDao = database.storeDao(),
            transactionRunner = object : TransactionRunner {
                override suspend fun <T> run(block: suspend () -> T): T =
                    database.withTransaction { block() }
            }
        )
    }

    override val notificationSettingsRepository: NotificationSettingsRepository by lazy {
        RealNotificationSettingsRepository(sharedPreferences = sharedPreferences)
    }

    /** 辞書の既定周期をカテゴリ別に反映した予測器(F-3)。辞書にないカテゴリは予測器の既定値のまま */
    private val consumptionPredictor: DefaultConsumptionPredictor by lazy {
        DefaultConsumptionPredictor(
            DefaultConsumptionPredictor.DEFAULT_CATEGORY_CYCLES +
                ProductDictionaryLoader.categoryCycleDays(productDictionary)
        )
    }

    override val shoppingListRepository: ShoppingListRepository by lazy {
        RealShoppingListRepository(
            reminderRepository = reminderRepository,
            consumptionPredictor = consumptionPredictor,
            trigger = trigger,
            normalizer = productNormalizer,
            aliasStore = learnedAliasStore
        )
    }

    override val productRepository: ProductRepository by lazy {
        RealProductRepository(
            reminderRepository = reminderRepository,
            consumptionPredictor = consumptionPredictor,
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
            trigger = trigger,
            onClearAllData = { learnedAliasStore.clear() }
        )
    }

    private val ocrScanner: DefaultReceiptScanner by lazy {
        DefaultReceiptScanner(context)
    }

    private val productDictionary: List<DictionaryEntry> by lazy {
        loadProductDictionaryFromAssets(context)
    }

    private val productNormalizer: DefaultProductNormalizer by lazy {
        DefaultProductNormalizer(initialDictionary = productDictionary)
    }

    /** 確認画面の修正から覚えた別名の保存と読み込み(P-2) */
    private val learnedAliasStore: LearnedAliasStore by lazy {
        LearnedAliasStore(reminderRepository, productNormalizer)
    }

    private val receiptParser: DefaultReceiptParser by lazy {
        DefaultReceiptParser()
    }

    override val receiptReviewService: ReceiptReviewService by lazy {
        RealReceiptReviewService(
            reminderRepository = reminderRepository,
            ocrScanner = ocrScanner,
            normalizer = productNormalizer,
            trigger = trigger,
            aliasStore = learnedAliasStore
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
            notificationSettingsRepository = notificationSettingsRepository,
            syncGate = GeofenceSyncGate(PrefsSyncStateStore.of(context)),
            environmentProvider = { geofenceEnvironment(context) }
        )
    }

    companion object {
        /**
         * assets/products.json から商品辞書エントリを読み込む。
         */
        fun loadProductDictionaryFromAssets(context: Context): List<DictionaryEntry> {
            return try {
                val jsonString = context.assets.open("products.json").bufferedReader().use { it.readText() }
                val entries = ProductDictionaryLoader.parse(jsonString)
                if (entries.isNotEmpty()) entries else DefaultProductNormalizer.defaultProductDictionary()
            } catch (_: Exception) {
                DefaultProductNormalizer.defaultProductDictionary()
            }
        }

        /** この時間より古い最終位置は店舗の登録に使わない */
        internal const val MAX_CACHED_LOCATION_AGE_MS = 30_000L
        private const val CURRENT_LOCATION_TIMEOUT_MS = 15_000L

        /**
         * 現在地を取得する。高精度の現在地取得を優先し、取れないときは新しい最終位置だけを使う。
         * どちらも取れない・権限がないときは null を返す(既定の座標にはしない)。
         */
        private suspend fun getCurrentLocation(context: Context): Pair<Double, Double>? {
            return try {
                val client = LocationServices.getFusedLocationProviderClient(context)
                val fresh = withTimeoutOrNull(CURRENT_LOCATION_TIMEOUT_MS) {
                    suspendCancellableCoroutine<android.location.Location?> { cont ->
                        val source = CancellationTokenSource()
                        cont.invokeOnCancellation { source.cancel() }
                        client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, source.token)
                            .addOnSuccessListener { if (cont.isActive) cont.resume(it) }
                            .addOnFailureListener { if (cont.isActive) cont.resume(null) }
                            .addOnCanceledListener { if (cont.isActive) cont.resume(null) }
                    }
                }
                val location = fresh ?: suspendCancellableCoroutine<android.location.Location?> { cont ->
                    client.lastLocation
                        .addOnSuccessListener { if (cont.isActive) cont.resume(it) }
                        .addOnFailureListener { if (cont.isActive) cont.resume(null) }
                }?.takeIf { isRecentEnough(it.time, System.currentTimeMillis()) }
                location?.let { it.latitude to it.longitude }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        }

        /** 最終位置が使えるほど新しいか */
        internal fun isRecentEnough(locationTimeMs: Long, nowMs: Long): Boolean =
            nowMs - locationTimeMs in 0..MAX_CACHED_LOCATION_AGE_MS
    }
}
