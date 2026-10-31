package com.example.receiptreminder.ui.review

import com.example.receiptreminder.domain.model.ReceiptCandidate
import com.example.receiptreminder.domain.model.ReceiptItemCandidate
import com.example.receiptreminder.domain.repository.ReceiptReviewService
import com.example.receiptreminder.domain.repository.ReceiptScanner
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
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
class ReceiptReviewViewModelTest {

    private class StubScanner(var result: Result<ReceiptCandidate>) : ReceiptScanner {
        var calls = 0
        var gate: CompletableDeferred<Unit>? = null
        override suspend fun scanReceipt(): Result<ReceiptCandidate> {
            calls++
            gate?.await()
            return result
        }
    }

    private class StubService : ReceiptReviewService {
        var duplicateKeys = setOf<Triple<String, String, Int>>()
        val duplicateQueries = mutableListOf<Triple<String, String, Int>>()
        var saveResult: Result<Unit> = Result.success(Unit)
        val saved = mutableListOf<ReceiptCandidate>()
        var discardCalls = 0

        override suspend fun getPendingReceipt(): ReceiptCandidate? = null
        override suspend fun checkDuplicate(storeName: String, dateString: String, totalAmount: Int): Boolean {
            val key = Triple(storeName, dateString, totalAmount)
            duplicateQueries += key
            return key in duplicateKeys
        }

        override suspend fun saveReceipt(receipt: ReceiptCandidate): Result<Unit> {
            saved += receipt
            return saveResult
        }

        override suspend fun discardReceipt() {
            discardCalls++
        }
    }

    private fun candidate() = ReceiptCandidate(
        id = "r1",
        storeName = "テスト商店",
        purchasedOn = LocalDate.of(2026, 10, 31),
        totalAmount = 600,
        items = listOf(
            ReceiptItemCandidate("i1", "ｱｲﾃﾑA", "品物A", 2, 400, true, "まとめます"),
            ReceiptItemCandidate("i2", "ｱｲﾃﾑB", "品物B", 1, 200, false)
        )
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.vm(scanner: StubScanner, service: StubService): ReceiptReviewViewModel {
        val vm = ReceiptReviewViewModel(scanner, service)
        return vm
    }

    @Test
    fun scanStartsOnOpenAndShowsScanningThenEditing() = runTest {
        val scanner = StubScanner(Result.success(candidate())).apply { gate = CompletableDeferred() }
        val vm = vm(scanner, StubService())
        advanceUntilIdle()
        assertEquals(1, scanner.calls)
        assertEquals(ReviewPhase.Scanning, vm.uiState.value.phase)

        scanner.gate!!.complete(Unit)
        advanceUntilIdle()
        val s = vm.uiState.value
        assertEquals(ReviewPhase.Editing, s.phase)
        assertEquals("テスト商店", s.form.storeName)
        assertEquals("2026/10/31", s.form.date)
        assertEquals("600", s.form.total)
        assertEquals(2, s.form.items.size)
        assertEquals("2", s.form.items[0].quantity)
    }

    @Test
    fun scanFailureOffersRetakeAndManualInput() = runTest {
        val scanner = StubScanner(Result.failure(RuntimeException("x")))
        val vm = vm(scanner, StubService())
        advanceUntilIdle()
        assertEquals(ReviewPhase.ScanFailed, vm.uiState.value.phase)

        scanner.result = Result.success(candidate())
        vm.scan()
        advanceUntilIdle()
        assertEquals(2, scanner.calls)
        assertEquals(ReviewPhase.Editing, vm.uiState.value.phase)
    }

    @Test
    fun manualInputOpensEmptyCandidate() = runTest {
        val vm = vm(StubScanner(Result.failure(RuntimeException())), StubService())
        advanceUntilIdle()
        vm.startManualInput()
        val s = vm.uiState.value
        assertEquals(ReviewPhase.Editing, s.phase)
        assertEquals("", s.form.storeName)
        assertEquals("", s.form.date)
        assertEquals("", s.form.total)
        assertTrue(s.form.items.isEmpty())
    }

    @Test
    fun duplicateWarningIsShownAndRecomputedWhenValuesChange() = runTest {
        val service = StubService().apply {
            duplicateKeys = setOf(Triple("テスト商店", "2026-10-31", 600))
        }
        val vm = vm(StubScanner(Result.success(candidate())), service)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isDuplicate)

        vm.onTotalChange("601")
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isDuplicate)

        vm.onTotalChange("600")
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isDuplicate)

        vm.onStoreNameChange("別の店")
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isDuplicate)

        vm.onStoreNameChange("テスト商店")
        vm.onDateChange("2026/10/30")
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isDuplicate)
    }

    @Test
    fun duplicateIsNotCheckedWhileInputIsInvalid() = runTest {
        val service = StubService()
        val vm = vm(StubScanner(Result.success(candidate())), service)
        advanceUntilIdle()
        service.duplicateQueries.clear()
        vm.onDateChange("2026/13/45")
        vm.onTotalChange("abc")
        advanceUntilIdle()
        assertTrue(service.duplicateQueries.isEmpty())
        assertFalse(vm.uiState.value.isDuplicate)
    }

    @Test
    fun duplicateCandidateCanStillBeSaved() = runTest {
        val service = StubService().apply {
            duplicateKeys = setOf(Triple("テスト商店", "2026-10-31", 600))
        }
        val vm = vm(StubScanner(Result.success(candidate())), service)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isDuplicate)
        vm.save()
        advanceUntilIdle()
        assertEquals(ReviewResult.Saved, vm.uiState.value.finished)
        assertEquals(1, service.saved.size)
    }

    @Test
    fun editedValuesAreSavedIncludingAddAndRemove() = runTest {
        val service = StubService()
        val vm = vm(StubScanner(Result.success(candidate())), service)
        advanceUntilIdle()

        vm.onStoreNameChange(" 新しい店 ")
        vm.onDateChange("2026-10-01")
        vm.onTotalChange("¥1,234")
        vm.onItemNameChange("i1", "品物A改")
        vm.onItemQuantityChange("i1", "３")
        vm.onItemPriceChange("i1", "500")
        vm.removeItem("i2")
        vm.addItem()
        val newId = vm.uiState.value.form.items.last().id
        vm.onItemNameChange(newId, "追加品")
        vm.onItemPriceChange(newId, "100")
        vm.save()
        advanceUntilIdle()

        assertEquals(ReviewResult.Saved, vm.uiState.value.finished)
        val saved = service.saved.single()
        assertEquals("r1", saved.id)
        assertEquals("新しい店", saved.storeName)
        assertEquals(LocalDate.of(2026, 10, 1), saved.purchasedOn)
        assertEquals(1234, saved.totalAmount)
        assertEquals(2, saved.items.size)
        assertEquals("品物A改", saved.items[0].normalizedName)
        assertEquals(3, saved.items[0].quantity)
        assertEquals(500, saved.items[0].price)
        assertEquals("ｱｲﾃﾑA", saved.items[0].rawOcrText)
        assertEquals("追加品", saved.items[1].normalizedName)
        assertEquals(1, saved.items[1].quantity)
        assertEquals(100, saved.items[1].price)
    }

    @Test
    fun invalidInputBlocksSaveAndShowsErrors() = runTest {
        val service = StubService()
        val vm = vm(StubScanner(Result.success(candidate())), service)
        advanceUntilIdle()
        vm.onStoreNameChange("  ")
        vm.onDateChange("2026/02/30")
        vm.onTotalChange("-5")
        vm.onItemNameChange("i1", "")
        vm.onItemQuantityChange("i1", "0")
        vm.onItemPriceChange("i1", "1.5")
        vm.save()
        advanceUntilIdle()

        val s = vm.uiState.value
        assertTrue(service.saved.isEmpty())
        assertTrue(s.showErrors)
        assertNull(s.finished)
        val v = s.validation
        assertEquals(FieldError.Required, v.store)
        assertEquals(FieldError.InvalidDate, v.date)
        assertEquals(FieldError.InvalidNumber, v.total)
        val item = v.items.getValue("i1")
        assertEquals(FieldError.Required, item.name)
        assertEquals(FieldError.InvalidQuantity, item.quantity)
        assertEquals(FieldError.InvalidNumber, item.price)
    }

    @Test
    fun receiptWithoutItemsCannotBeSaved() = runTest {
        val service = StubService()
        val vm = vm(StubScanner(Result.failure(RuntimeException())), service)
        advanceUntilIdle()
        vm.startManualInput()
        vm.onStoreNameChange("手入力店")
        vm.onDateChange("2026/10/31")
        vm.onTotalChange("100")
        vm.save()
        advanceUntilIdle()
        assertTrue(service.saved.isEmpty())
        assertTrue(vm.uiState.value.validation.noItems)
    }

    @Test
    fun saveFailureShowsErrorAndKeepsInput() = runTest {
        val service = StubService().apply { saveResult = Result.failure(RuntimeException("x")) }
        val vm = vm(StubScanner(Result.success(candidate())), service)
        advanceUntilIdle()
        vm.onStoreNameChange("編集した店")
        vm.save()
        advanceUntilIdle()

        val s = vm.uiState.value
        assertTrue(s.saveFailed)
        assertFalse(s.isSaving)
        assertNull(s.finished)
        assertEquals("編集した店", s.form.storeName)

        service.saveResult = Result.success(Unit)
        vm.save()
        advanceUntilIdle()
        assertEquals(ReviewResult.Saved, vm.uiState.value.finished)
        assertFalse(vm.uiState.value.saveFailed)
    }

    @Test
    fun cancelDiscardsReceiptAndFinishes() = runTest {
        val service = StubService()
        val vm = vm(StubScanner(Result.success(candidate())), service)
        advanceUntilIdle()
        vm.cancel()
        advanceUntilIdle()
        assertEquals(1, service.discardCalls)
        assertEquals(ReviewResult.Cancelled, vm.uiState.value.finished)
        assertTrue(service.saved.isEmpty())
    }

    @Test
    fun cancelDuringScanDiscardsAndIgnoresLateResult() = runTest {
        val scanner = StubScanner(Result.success(candidate())).apply { gate = CompletableDeferred() }
        val service = StubService()
        val vm = vm(scanner, service)
        advanceUntilIdle()
        vm.cancel()
        advanceUntilIdle()
        scanner.gate!!.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, service.discardCalls)
        assertEquals(ReviewResult.Cancelled, vm.uiState.value.finished)
        assertNotNull(vm.uiState.value)
        assertEquals(ReviewPhase.Scanning, vm.uiState.value.phase)
    }
}
