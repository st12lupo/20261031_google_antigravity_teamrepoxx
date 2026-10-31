package com.example.receiptreminder.ui.product

import com.example.receiptreminder.domain.model.Product
import com.example.receiptreminder.domain.model.PurchaseRecord
import com.example.receiptreminder.domain.model.PurchaseSource
import com.example.receiptreminder.domain.repository.ProductRepository
import com.example.receiptreminder.fake.FakeProductRepository
import com.example.receiptreminder.fake.FakeShoppingListRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class ProductViewModelTest {

    private val today = LocalDate.of(2026, 10, 31)

    private val soap = Product(
        id = "p-soap", name = "テスト洗剤", cycleDays = 30.0, isTracked = true,
        predictedOutDate = today.plusDays(2), remainingDays = 2
    )
    private val milk = Product(
        id = "p-milk", name = "テスト牛乳", cycleDays = 7.0, isTracked = true,
        isEstimated = true, predictedOutDate = today.plusDays(1), remainingDays = 1
    )
    private val salt = Product(
        id = "p-salt", name = "テスト塩", isTracked = false, remainingDays = 40
    )

    private fun purchase(id: String, date: LocalDate, qty: Int = 1, productId: String = soap.id) =
        PurchaseRecord(
            id = id, productId = productId, productName = "テスト洗剤",
            purchasedOn = date, quantity = qty, source = PurchaseSource.RECEIPT, storeName = "テスト店"
        )

    private lateinit var products: FakeProductRepository
    private lateinit var shopping: FakeShoppingListRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        products = FakeProductRepository(
            initialProducts = listOf(soap, milk, salt),
            initialPurchases = listOf(
                purchase("a", today.minusDays(60)),
                purchase("b", today.minusDays(30), 2)
            )
        )
        shopping = FakeShoppingListRepository(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun detail(id: String? = soap.id, repo: ProductRepository = products) =
        ProductDetailViewModel(id, repo, shopping) { today }

    private fun ProductDetailViewModel.loaded() = uiState.value.content as ProductDetailContent.Loaded

    // --- 一覧 ---

    @Test
    fun list_showsAllProducts_trackedFirstThenUntracked() {
        val vm = ProductListViewModel(products)
        val state = vm.uiState.value as ProductListUiState.Success
        assertEquals(listOf("p-milk", "p-soap", "p-salt"), state.products.map { it.id })
        assertFalse(state.products.last().isTracked)
    }

    @Test
    fun list_emptyRepository_isEmptySuccess() {
        val vm = ProductListViewModel(FakeProductRepository(emptyList(), emptyList()))
        assertEquals(emptyList<Product>(), (vm.uiState.value as ProductListUiState.Success).products)
    }

    @Test
    fun list_reflectsFlowUpdates() = runTest {
        val vm = ProductListViewModel(products)
        products.updateTrackedStatus(milk.id, false)
        val state = vm.uiState.value as ProductListUiState.Success
        assertEquals(listOf("p-soap", "p-salt", "p-milk"), state.products.map { it.id })
    }

    @Test
    fun list_errorThenRetry() {
        var fail = true
        val repo = object : ProductRepository by products {
            override fun getProductsStream(): Flow<List<Product>> =
                if (fail) flow { throw IllegalStateException("x") } else products.getProductsStream()
        }
        val vm = ProductListViewModel(repo)
        assertEquals(ProductListUiState.Error, vm.uiState.value)
        fail = false
        vm.retry()
        assertTrue(vm.uiState.value is ProductListUiState.Success)
    }

    // --- 詳細の読み込み ---

    @Test
    fun detail_loadsProductAndHistoryNewestFirst() {
        val loaded = detail().loaded()
        assertEquals(soap.id, loaded.product.id)
        assertEquals(listOf("b", "a"), loaded.purchases.map { it.id })
    }

    @Test
    fun detail_unknownOrNullId_isNotFound() {
        assertEquals(ProductDetailContent.NotFound, detail("missing").uiState.value.content)
        assertEquals(ProductDetailContent.NotFound, detail(null).uiState.value.content)
    }

    @Test
    fun detail_streamError_showsErrorAndRetryRecovers() {
        var fail = true
        val repo = object : ProductRepository by products {
            override fun getProductsStream(): Flow<List<Product>> =
                if (fail) flow { throw IllegalStateException("x") } else products.getProductsStream()
        }
        val vm = detail(repo = repo)
        assertEquals(ProductDetailContent.Error, vm.uiState.value.content)
        fail = false
        vm.retry()
        assertTrue(vm.uiState.value.content is ProductDetailContent.Loaded)
    }

    // --- P-4 ---

    @Test
    fun setTracked_updatesProductImmediately() {
        val vm = detail()
        vm.setTracked(false)
        assertFalse(vm.loaded().product.isTracked)
        assertEquals(ProductMessage.TRACKED_OFF, vm.uiState.value.message)
        vm.setTracked(true)
        assertTrue(vm.loaded().product.isTracked)
    }

    // --- 買い物リスト操作 ---

    @Test
    fun actions_callShoppingListRepository() {
        val calls = mutableListOf<String>()
        val recording = object : com.example.receiptreminder.domain.repository.ShoppingListRepository by shopping {
            override suspend fun markAsPurchased(productId: String, quantity: Int) { calls += "buy:$productId:$quantity" }
            override suspend fun snooze(productId: String) { calls += "snooze:$productId" }
            override suspend fun stopTracking(productId: String) { calls += "stop:$productId" }
        }
        val vm = ProductDetailViewModel(soap.id, products, recording) { today }
        vm.markPurchased()
        vm.snooze()
        vm.stopTracking()
        assertEquals(listOf("buy:p-soap:1", "snooze:p-soap", "stop:p-soap"), calls)
        assertEquals(ProductMessage.STOPPED, vm.uiState.value.message)
        vm.messageShown()
        assertNull(vm.uiState.value.message)
    }

    @Test
    fun action_failure_reportsFailedMessage() {
        val failing = object : com.example.receiptreminder.domain.repository.ShoppingListRepository by shopping {
            override suspend fun snooze(productId: String) { throw IllegalStateException("x") }
        }
        val vm = ProductDetailViewModel(soap.id, products, failing) { today }
        vm.snooze()
        assertEquals(ProductMessage.FAILED, vm.uiState.value.message)
    }

    @Test
    fun addPurchase_validInput_callsAddManualPurchaseWithProductName() {
        var captured: Triple<String, LocalDate, Int>? = null
        val recording = object : com.example.receiptreminder.domain.repository.ShoppingListRepository by shopping {
            override suspend fun addManualPurchase(
                productName: String, purchasedOn: LocalDate, quantity: Int, price: Int?, storeName: String?
            ) { captured = Triple(productName, purchasedOn, quantity) }
        }
        val vm = ProductDetailViewModel(soap.id, products, recording) { today }
        vm.startAddPurchase()
        assertEquals("2026-10-31", vm.uiState.value.editor?.dateText)
        vm.updateEditor(dateText = "2026/10/29", quantityText = "3")
        vm.saveEditor()
        assertNull(vm.uiState.value.editor)
        assertEquals(Triple("テスト洗剤", LocalDate.of(2026, 10, 29), 3), captured)
        assertEquals(ProductMessage.ADDED, vm.uiState.value.message)
    }

    // --- B-4 修正 ---

    @Test
    fun editPurchase_updatesHistoryImmediately() {
        val vm = detail()
        vm.startEditPurchase("a")
        assertEquals(today.minusDays(60).toString(), vm.uiState.value.editor?.dateText)
        vm.updateEditor(dateText = "2026-09-01", quantityText = "4")
        vm.saveEditor()
        val updated = vm.loaded().purchases.first { it.id == "a" }
        assertEquals(LocalDate.of(2026, 9, 1), updated.purchasedOn)
        assertEquals(4, updated.quantity)
        assertEquals("テスト店", updated.storeName)
        assertEquals(ProductMessage.UPDATED, vm.uiState.value.message)
    }

    @Test
    fun editPurchase_invalidInput_keepsEditorAndDoesNotSave() {
        val vm = detail()
        vm.startEditPurchase("a")
        vm.updateEditor(dateText = "2026-13-40", quantityText = "0")
        vm.saveEditor()
        val editor = vm.uiState.value.editor!!
        assertEquals(DateInputError.INVALID, editor.dateError)
        assertTrue(editor.quantityError)
        assertEquals(today.minusDays(60), vm.loaded().purchases.first { it.id == "a" }.purchasedOn)
        // 入力し直すとエラーが消える
        vm.updateEditor(dateText = "2026-09-01")
        assertNull(vm.uiState.value.editor?.dateError)
    }

    // --- B-4 削除 ---

    @Test
    fun delete_requiresConfirmation() {
        val vm = detail()
        vm.requestDelete("a")
        assertEquals("a", vm.uiState.value.deleteTarget?.id)
        assertEquals(2, vm.loaded().purchases.size)
        vm.dismissDelete()
        assertNull(vm.uiState.value.deleteTarget)
        assertEquals(2, vm.loaded().purchases.size)
    }

    @Test
    fun delete_confirmed_removesFromHistoryImmediately() {
        val vm = detail()
        vm.requestDelete("a")
        vm.confirmDelete()
        assertEquals(listOf("b"), vm.loaded().purchases.map { it.id })
        assertEquals(ProductMessage.DELETED, vm.uiState.value.message)
        assertNull(vm.uiState.value.deleteTarget)
    }

    // --- 入力検証 ---

    @Test
    fun validate_rules() {
        assertTrue(validatePurchaseInput("2026-10-31", "1", today) is PurchaseValidation.Valid)
        assertTrue(validatePurchaseInput("2026-1-5", "99", today) is PurchaseValidation.Valid)
        assertEquals(
            DateInputError.FUTURE,
            (validatePurchaseInput("2026-11-01", "1", today) as PurchaseValidation.Invalid).dateError
        )
        assertEquals(
            DateInputError.INVALID,
            (validatePurchaseInput("2026-02-30", "1", today) as PurchaseValidation.Invalid).dateError
        )
        assertEquals(
            DateInputError.INVALID,
            (validatePurchaseInput("", "1", today) as PurchaseValidation.Invalid).dateError
        )
        listOf("0", "-1", "100", "abc", "", "1.5").forEach {
            assertTrue(
                it,
                (validatePurchaseInput("2026-10-31", it, today) as PurchaseValidation.Invalid).quantityError
            )
        }
    }

    @Test
    fun formatCycle_dropsTrailingZero() {
        assertEquals("30", formatCycleDays(30.0))
        assertEquals("7.5", formatCycleDays(7.46))
    }
}
