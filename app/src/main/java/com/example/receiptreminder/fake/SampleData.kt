package com.example.receiptreminder.fake

import com.example.receiptreminder.domain.model.*
import java.time.LocalDate

/**
 * 画面モックアップ（mockups/*.dc.html）に基づく初期サンプルデータ
 */
object SampleData {
    val today: LocalDate = LocalDate.now()

    val milk = Product(
        id = "prod-milk",
        name = "牛乳",
        category = "飲料・乳製品",
        isTracked = true,
        isEstimated = false,
        cycleDays = 7.0,
        predictedOutDate = today.minusDays(1),
        becameLowDate = today.minusDays(3),
        remainingDays = -1
    )

    val dishSoap = Product(
        id = "prod-dish-soap",
        name = "食器用洗剤",
        category = "日用品",
        isTracked = true,
        isEstimated = false,
        cycleDays = 30.0,
        predictedOutDate = today.plusDays(2),
        becameLowDate = today.minusDays(4),
        remainingDays = 2
    )

    val toiletPaper = Product(
        id = "prod-toilet-paper",
        name = "トイレットペーパー",
        category = "日用品",
        isTracked = true,
        isEstimated = false,
        cycleDays = 28.0,
        predictedOutDate = today.plusDays(4),
        becameLowDate = today.minusDays(2),
        remainingDays = 4
    )

    val shampoo = Product(
        id = "prod-shampoo",
        name = "シャンプー",
        category = "日用品",
        isTracked = true,
        isEstimated = false,
        cycleDays = 45.0,
        predictedOutDate = today.plusDays(18),
        becameLowDate = null,
        remainingDays = 18
    )

    val rice = Product(
        id = "prod-rice",
        name = "米",
        category = "定番食品",
        isTracked = true,
        isEstimated = true, // 「推定」バッジ
        cycleDays = 40.0,
        predictedOutDate = today.plusDays(26),
        becameLowDate = null,
        remainingDays = 26
    )

    fun initialProducts(): List<Product> = listOf(milk, dishSoap, toiletPaper, shampoo, rice)

    fun initialShoppingItems(): List<ShoppingItem> = initialProducts().map { ShoppingItem(it) }

    fun initialPurchasesForDishSoap(): List<PurchaseRecord> = listOf(
        PurchaseRecord(
            id = "purch-soap-1",
            productId = dishSoap.id,
            productName = dishSoap.name,
            purchasedOn = today.minusMonths(1),
            quantity = 1,
            price = 198,
            source = PurchaseSource.RECEIPT,
            storeName = "みどりマート 中央店"
        ),
        PurchaseRecord(
            id = "purch-soap-2",
            productId = dishSoap.id,
            productName = dishSoap.name,
            purchasedOn = today.minusMonths(2),
            quantity = 1,
            price = 198,
            source = PurchaseSource.MANUAL,
            storeName = null
        ),
        PurchaseRecord(
            id = "purch-soap-3",
            productId = dishSoap.id,
            productName = dishSoap.name,
            purchasedOn = today.minusMonths(3),
            quantity = 1,
            price = 198,
            source = PurchaseSource.RECEIPT,
            storeName = "みどりマート 中央店"
        ),
        PurchaseRecord(
            id = "purch-soap-4",
            productId = dishSoap.id,
            productName = dishSoap.name,
            purchasedOn = today.minusMonths(4),
            quantity = 1,
            price = 198,
            source = PurchaseSource.RECEIPT,
            storeName = "ドラッグひかり 駅前店"
        )
    )

    fun sampleReceiptCandidate(): ReceiptCandidate = ReceiptCandidate(
        id = "receipt-sample-1",
        storeName = "みどりマート 中央店",
        purchasedOn = today,
        totalAmount = 1544,
        isDuplicateWarning = true,
        items = listOf(
            ReceiptItemCandidate(
                id = "rc-item-1",
                rawOcrText = "ｼｮｯｷｾﾝｻﾞｲ ｷｭｷｭｯﾄ",
                normalizedName = "食器用洗剤",
                quantity = 1,
                price = 198,
                isTracked = true
            ),
            ReceiptItemCandidate(
                id = "rc-item-2",
                rawOcrText = "ﾄｲﾚｯﾄﾍﾟｰﾊﾟｰ 12R",
                normalizedName = "トイレットペーパー",
                quantity = 1,
                price = 458,
                isTracked = true,
                mergedNotice = "2 日前の「購入済み」と 1 件にまとめます"
            ),
            ReceiptItemCandidate(
                id = "rc-item-3",
                rawOcrText = "ｷﾞｭｳﾆｭｳ 1L 2ｺ X238",
                normalizedName = "牛乳",
                quantity = 2,
                price = 476,
                isTracked = true
            ),
            ReceiptItemCandidate(
                id = "rc-item-4",
                rawOcrText = "ﾄﾘﾑﾈﾆｸ",
                normalizedName = "鶏むね肉",
                quantity = 1,
                price = 412,
                isTracked = false
            )
        )
    )

    fun initialStores(): List<Store> = listOf(
        Store(
            id = "store-1",
            name = "みどりマート 中央店",
            latitude = 35.6812,
            longitude = 139.7671,
            radiusMeters = 200,
            notifyEnabled = true
        ),
        Store(
            id = "store-2",
            name = "ドラッグひかり 駅前店",
            latitude = 35.6895,
            longitude = 139.6917,
            radiusMeters = 300,
            notifyEnabled = true
        ),
        Store(
            id = "store-3",
            name = "まるやまスーパー",
            latitude = 35.6580,
            longitude = 139.7514,
            radiusMeters = 200,
            notifyEnabled = false
        )
    )
}
