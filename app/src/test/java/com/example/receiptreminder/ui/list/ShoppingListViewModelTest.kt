package com.example.receiptreminder.ui.list

import com.example.receiptreminder.domain.model.Product
import com.example.receiptreminder.domain.model.ShoppingItem
import com.example.receiptreminder.domain.repository.ShoppingListRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class ShoppingListViewModelTest {

    private val today = LocalDate.of(2026, 10, 31)

    private class StubRepository(initial: List<ShoppingItem> = emptyList()) : ShoppingListRepository {
        val items = MutableStateFlow(initial)
        var failStream = false
        var failActions = false
        val purchased = mutableListOf<Pair<String, Int>>()
        val snoozed = mutableListOf<String>()
        val stopped = mutableListOf<String>()
        val added = mutableListOf<Triple<String, LocalDate, Int>>()

        override fun getShoppingItemsStream(): Flow<List<ShoppingItem>> =
            if (failStream) flow { throw IllegalStateException("boom") } else items

        override suspend fun markAsPurchased(productId: String, quantity: Int) {
            if (failActions) throw IllegalStateException("boom")
            purchased += productId to quantity
        }

        override suspend fun snooze(productId: String) {
            if (failActions) throw IllegalStateException("boom")
            snoozed += productId
        }

        override suspend fun stopTracking(productId: String) {
            if (failActions) throw IllegalStateException("boom")
            stopped += productId
        }

        override suspend fun addManualPurchase(
            productName: String,
            purchasedOn: LocalDate,
            quantity: Int,
            price: Int?,
            storeName: String?
        ) {
            if (failActions) throw IllegalStateException("boom")
            added += Triple(productName, purchasedOn, quantity)
        }

        override suspend fun addPurchaseForProduct(productId: String, purchasedOn: LocalDate, quantity: Int, price: Int?) {
            if (failActions) throw IllegalStateException("boom")
        }
    }

    private fun item(id: String, days: Int, estimated: Boolean = false, tracked: Boolean = true) =
        ShoppingItem(
            Product(id = id, name = "name-$id", isTracked = tracked, isEstimated = estimated, remainingDays = days)
        )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun vm(repo: ShoppingListRepository) = ShoppingListViewModel(repo) { today }

    @Test
    fun items_areSplitIntoUrgentAndSafe_sortedByRemainingDays() = runTest {
        val repo = StubRepository(
            listOf(item("c", 26, estimated = true), item("a", 2), item("d", -1), item("b", 18))
        )
        val state = vm(repo).uiState.value

        assertFalse(state.isLoading)
        assertEquals(listOf("d", "a"), state.urgent.map { it.productId })
        assertEquals(listOf("b", "c"), state.safe.map { it.productId })
        assertTrue(state.safe.first { it.productId == "c" }.isEstimated)
        assertEquals(-1, state.urgent.first().remainingDays)
    }

    @Test
    fun untrackedItems_areNotShown() = runTest {
        val repo = StubRepository(listOf(item("a", 2, tracked = false), item("b", 20)))
        val state = vm(repo).uiState.value
        assertTrue(state.urgent.isEmpty())
        assertEquals(listOf("b"), state.safe.map { it.productId })
    }

    @Test
    fun emptyList_isEmptyState() = runTest {
        val state = vm(StubRepository()).uiState.value
        assertTrue(state.isEmpty)
        assertFalse(state.loadFailed)
    }

    @Test
    fun streamError_showsLoadFailed_andRetryRecovers() = runTest {
        val repo = StubRepository(listOf(item("a", 3))).apply { failStream = true }
        val vm = vm(repo)
        assertTrue(vm.uiState.value.loadFailed)
        assertFalse(vm.uiState.value.isEmpty)

        repo.failStream = false
        vm.retry()
        assertFalse(vm.uiState.value.loadFailed)
        assertEquals(listOf("a"), vm.uiState.value.urgent.map { it.productId })
    }

    @Test
    fun actions_areForwardedToRepository_includingSafeItems_andPurchaseQuantityIsOne() = runTest {
        val repo = StubRepository(listOf(item("urgent", 1), item("safe", 20)))
        val vm = vm(repo)

        vm.onPurchased("safe")
        vm.onStillHave("safe")
        vm.onDoNotBuy("urgent")

        assertEquals(listOf("safe" to 1), repo.purchased)
        assertEquals(listOf("safe"), repo.snoozed)
        assertEquals(listOf("urgent"), repo.stopped)
    }

    @Test
    fun actionFailure_setsFlag_untilConsumed() = runTest {
        val repo = StubRepository(listOf(item("a", 1))).apply { failActions = true }
        val vm = vm(repo)

        vm.onPurchased("a")
        assertTrue(vm.uiState.value.actionFailed)
        vm.consumeActionError()
        assertFalse(vm.uiState.value.actionFailed)
    }

    @Test
    fun openAddForm_defaultsToTodayAndQuantityOne() = runTest {
        val vm = vm(StubRepository())
        vm.openAddForm()
        val form = vm.uiState.value.addForm!!
        assertEquals("2026-10-31", form.date)
        assertEquals("1", form.quantity)
        assertEquals("", form.name)
    }

    @Test
    fun submit_validInput_savesTrimmedNameAndClosesForm() = runTest {
        val repo = StubRepository()
        val vm = vm(repo)
        vm.openAddForm()
        vm.onNameChange("  牛乳  ")
        vm.onDateChange("2026-10-30")
        vm.onQuantityChange("3")
        vm.submitAddForm()

        assertEquals(listOf(Triple("牛乳", LocalDate.of(2026, 10, 30), 3)), repo.added)
        assertNull(vm.uiState.value.addForm)
    }

    @Test
    fun submit_acceptsToday() = runTest {
        val repo = StubRepository()
        val vm = vm(repo)
        vm.openAddForm()
        vm.onNameChange("米")
        vm.submitAddForm()
        assertEquals(1, repo.added.size)
        assertEquals(today, repo.added[0].second)
    }

    @Test
    fun submit_invalidInput_showsErrorsAndDoesNotSave() = runTest {
        val repo = StubRepository()
        val vm = vm(repo)
        vm.openAddForm()
        vm.onNameChange("   ")
        vm.onDateChange("2026-02-30")
        vm.onQuantityChange("abc")
        vm.submitAddForm()

        val form = vm.uiState.value.addForm!!
        assertEquals(NameError.EMPTY, form.nameError)
        assertEquals(DateError.INVALID, form.dateError)
        assertEquals(QuantityError.INVALID, form.quantityError)
        assertTrue(repo.added.isEmpty())
    }

    @Test
    fun submit_futureDate_isRejected() = runTest {
        val repo = StubRepository()
        val vm = vm(repo)
        vm.openAddForm()
        vm.onNameChange("米")
        vm.onDateChange("2026-11-01")
        vm.submitAddForm()
        assertEquals(DateError.FUTURE, vm.uiState.value.addForm!!.dateError)
        assertTrue(repo.added.isEmpty())
    }

    @Test
    fun submit_quantityOutOfRange_andNameTooLong_areRejected() = runTest {
        val repo = StubRepository()
        val vm = vm(repo)
        vm.openAddForm()
        vm.onNameChange("あ".repeat(ShoppingListViewModel.MAX_NAME_LENGTH + 1))
        vm.onQuantityChange("0")
        vm.submitAddForm()
        assertEquals(NameError.TOO_LONG, vm.uiState.value.addForm!!.nameError)
        assertEquals(QuantityError.OUT_OF_RANGE, vm.uiState.value.addForm!!.quantityError)

        vm.onQuantityChange((ShoppingListViewModel.MAX_QUANTITY + 1).toString())
        vm.submitAddForm()
        assertEquals(QuantityError.OUT_OF_RANGE, vm.uiState.value.addForm!!.quantityError)
        assertTrue(repo.added.isEmpty())
    }

    @Test
    fun editingField_clearsItsError() = runTest {
        val vm = vm(StubRepository())
        vm.openAddForm()
        vm.submitAddForm()
        assertNotNull(vm.uiState.value.addForm!!.nameError)
        vm.onNameChange("米")
        assertNull(vm.uiState.value.addForm!!.nameError)
    }

    @Test
    fun submit_repositoryFailure_keepsFormOpenAndFlagsError() = runTest {
        val repo = StubRepository().apply { failActions = true }
        val vm = vm(repo)
        vm.openAddForm()
        vm.onNameChange("米")
        vm.submitAddForm()

        val state = vm.uiState.value
        assertNotNull(state.addForm)
        assertFalse(state.addForm!!.isSaving)
        assertTrue(state.actionFailed)
    }

    @Test
    fun dismissAddForm_closesForm() = runTest {
        val vm = vm(StubRepository())
        vm.openAddForm()
        vm.dismissAddForm()
        assertNull(vm.uiState.value.addForm)
    }
}
