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
import com.example.receiptreminder.data.bridge.CapturedImageTracker
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class ReceiptReviewViewModelTest {

    private class StubScanner(var result: Result<ReceiptCandidate>) : ReceiptScanner {
        var calls = 0
        var gate: CompletableDeferred<Unit>? = null
        val deleted = mutableListOf<String>()
        override suspend fun scanReceipt(imageUris: List<String>): Result<ReceiptCandidate> {
            calls++
            gate?.await()
            return result
        }

        override fun deleteImages(imageUris: List<String>) {
            deleted += imageUris
        }
    }

    private class StubService : ReceiptReviewService {
        var duplicateKeys = setOf<Triple<String, String, Int>>()
        val duplicateQueries = mutableListOf<Triple<String, String, Int>>()
        var saveResult: Result<Unit> = Result.success(Unit)
        val saved = mutableListOf<ReceiptCandidate>()
        var discardCalls = 0
        var suggestions = mapOf<String, Boolean>()
        val suggestQueries = mutableListOf<String>()

        override suspend fun suggestTracked(name: String): Boolean? {
            suggestQueries += name
            return suggestions[name]
        }

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
        vm.onImagesPicked(listOf("content://picked/1"))
        return vm
    }

    @Test
    fun opensOnSourceChoiceWithoutScanning() = runTest {
        val scanner = StubScanner(Result.success(candidate()))
        val vm = ReceiptReviewViewModel(scanner, StubService())
        advanceUntilIdle()
        assertEquals(ReviewPhase.ChooseSource, vm.uiState.value.phase)
        assertEquals(0, scanner.calls)

        vm.onImagesPicked(emptyList())
        advanceUntilIdle()
        assertEquals(ReviewPhase.ChooseSource, vm.uiState.value.phase)
        assertEquals(0, scanner.calls)
    }

    @Test
    fun capturedImagesAreDeletedAfterSave() = runTest {
        val scanner = StubScanner(Result.success(candidate()))
        val vm = ReceiptReviewViewModel(scanner, StubService())
        vm.onImagesCaptured(listOf("file://scan/1", "file://scan/2"))
        advanceUntilIdle()
        assertTrue(vm.uiState.value.hasCapturedImages)
        assertTrue(scanner.deleted.isEmpty())

        vm.save()
        advanceUntilIdle()
        assertEquals(listOf("file://scan/1", "file://scan/2"), scanner.deleted)
    }

    @Test
    fun capturedImagesAreKeptWhenSaveFailsThenDeletedOnCancel() = runTest {
        val scanner = StubScanner(Result.success(candidate()))
        val service = StubService().apply { saveResult = Result.failure(RuntimeException("x")) }
        val vm = ReceiptReviewViewModel(scanner, service)
        vm.onImagesCaptured(listOf("file://scan/1"))
        advanceUntilIdle()
        vm.save()
        advanceUntilIdle()
        assertTrue(scanner.deleted.isEmpty())

        vm.cancel()
        advanceUntilIdle()
        assertEquals(listOf("file://scan/1"), scanner.deleted)
    }

    @Test
    fun pickedImagesAreNeverDeleted() = runTest {
        val scanner = StubScanner(Result.success(candidate()))
        val vm = vm(scanner, StubService())
        advanceUntilIdle()
        assertFalse(vm.uiState.value.hasCapturedImages)
        assertTrue(vm.uiState.value.hasPickedImages)

        vm.save()
        advanceUntilIdle()
        assertEquals(ReviewResult.Saved, vm.uiState.value.finished)
        assertTrue(scanner.deleted.isEmpty())
    }

    @Test
    fun capturedImagesAreNotReportedAsPicked() = runTest {
        val vm = ReceiptReviewViewModel(StubScanner(Result.success(candidate())), StubService())
        vm.onImagesCaptured(listOf("file://scan/1"))
        advanceUntilIdle()
        assertTrue(vm.uiState.value.hasCapturedImages)
        assertFalse(vm.uiState.value.hasPickedImages)
    }

    @Test
    fun manualInputAfterFailedCaptureHasNoImages() = runTest {
        val vm = ReceiptReviewViewModel(StubScanner(Result.failure(RuntimeException("x"))), StubService())
        vm.onImagesCaptured(listOf("file://scan/1"))
        advanceUntilIdle()

        vm.startManualInput()
        assertFalse(vm.uiState.value.hasCapturedImages)
        assertFalse(vm.uiState.value.hasPickedImages)
    }

    @Test
    fun choosingAgainDeletesCapturedImagesAndReturnsToSourceChoice() = runTest {
        val scanner = StubScanner(Result.failure(RuntimeException("x")))
        val vm = ReceiptReviewViewModel(scanner, StubService())
        vm.onImagesCaptured(listOf("file://scan/1"))
        advanceUntilIdle()
        assertEquals(ReviewPhase.ScanFailed, vm.uiState.value.phase)

        vm.chooseSourceAgain()
        assertEquals(ReviewPhase.ChooseSource, vm.uiState.value.phase)
        assertEquals(listOf("file://scan/1"), scanner.deleted)
    }

    @Test
    fun captureUnavailableOffersFailureChoices() = runTest {
        val vm = ReceiptReviewViewModel(StubScanner(Result.success(candidate())), StubService())
        vm.onCaptureUnavailable()
        assertEquals(ReviewPhase.ScanFailed, vm.uiState.value.phase)
    }

    @Test
    fun scanStartsAfterImagesAreChosenAndShowsScanningThenEditing() = runTest {
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
        vm.chooseSourceAgain()
        vm.onImagesPicked(listOf("content://picked/2"))
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

    @Test
    fun userCanToggleTrackedPerItemAndSaveKeepsIt() = runTest {
        val service = StubService()
        val vm = vm(StubScanner(Result.success(candidate())), service)
        advanceUntilIdle()

        // i2 は予測の対象外で読み込まれる。画面から対象に切り替えられる(R-4, P-4)
        assertFalse(vm.uiState.value.form.items.first { it.id == "i2" }.isTracked)
        vm.onItemTrackedChange("i2", true)
        vm.onItemTrackedChange("i1", false)
        assertTrue(vm.uiState.value.form.items.first { it.id == "i2" }.isTracked)
        assertFalse(vm.uiState.value.form.items.first { it.id == "i1" }.isTracked)

        vm.save()
        advanceUntilIdle()
        val saved = service.saved.single().items.associateBy { it.id }
        assertTrue(saved.getValue("i2").isTracked)
        assertFalse(saved.getValue("i1").isTracked)
    }

    @Test
    fun renamingToDictionaryNameMakesItemTracked() = runTest {
        val service = StubService().apply { suggestions = mapOf("牛乳" to true, "ヨガ" to false) }
        val vm = vm(StubScanner(Result.success(candidate())), service)
        advanceUntilIdle()

        vm.onItemNameChange("i2", "牛乳")
        advanceUntilIdle()
        assertTrue(vm.uiState.value.form.items.first { it.id == "i2" }.isTracked)

        vm.onItemNameChange("i2", "ヨガ")
        advanceUntilIdle()
        assertFalse(vm.uiState.value.form.items.first { it.id == "i2" }.isTracked)
    }

    @Test
    fun renamingDoesNotOverrideAUserToggle() = runTest {
        val service = StubService().apply { suggestions = mapOf("牛乳" to true) }
        val vm = vm(StubScanner(Result.success(candidate())), service)
        advanceUntilIdle()

        vm.onItemTrackedChange("i2", false)
        vm.onItemNameChange("i2", "牛乳")
        advanceUntilIdle()
        assertFalse(vm.uiState.value.form.items.first { it.id == "i2" }.isTracked)
        assertTrue(service.suggestQueries.isEmpty())
    }

    @Test
    fun staleSuggestionIsIgnoredAndUnknownSuggestionKeepsCurrentValue() = runTest {
        val service = StubService().apply { suggestions = mapOf("牛乳" to true) }
        val vm = vm(StubScanner(Result.success(candidate())), service)
        advanceUntilIdle()

        // 判断できない名前(null)は現在の指定を変えない
        vm.onItemNameChange("i1", "どれでもない")
        advanceUntilIdle()
        assertTrue(vm.uiState.value.form.items.first { it.id == "i1" }.isTracked)

        // 入力の途中で結果が返っても、最後に入力した名前の結果だけが反映される
        vm.onItemNameChange("i2", "牛")
        vm.onItemNameChange("i2", "牛乳")
        advanceUntilIdle()
        assertEquals(listOf("どれでもない", "牛乳"), service.suggestQueries)
        assertTrue(vm.uiState.value.form.items.first { it.id == "i2" }.isTracked)
    }

    @Test
    fun onCleared_deletesCapturedImagesStillOnDisk() = runTest {
        val scanner = StubScanner(Result.success(candidate()))
        val tracker = CapturedImageTracker()
        val vm = ReceiptReviewViewModel(scanner, StubService(), tracker)
        vm.onImagesCaptured(listOf("file:///cache/scan/1.jpg"))
        advanceUntilIdle()
        assertTrue(tracker.isInUse("/cache/scan/1.jpg".let { java.io.File(it).absolutePath }))

        vm.onCleared() // CLEAR_TASK やプロセス終了で画面が破棄された
        assertEquals(listOf("file:///cache/scan/1.jpg"), scanner.deleted)
        assertFalse(tracker.isInUse(java.io.File("/cache/scan/1.jpg").absolutePath))
    }

    @Test
    fun onCleared_whileScanning_deletesAndCancels() = runTest {
        val scanner = StubScanner(Result.success(candidate())).apply { gate = CompletableDeferred() }
        val vm = ReceiptReviewViewModel(scanner, StubService(), CapturedImageTracker())
        vm.onImagesCaptured(listOf("file:///cache/scan/1.jpg"))
        advanceUntilIdle()
        assertEquals(ReviewPhase.Scanning, vm.uiState.value.phase)
        vm.onCleared()
        assertEquals(listOf("file:///cache/scan/1.jpg"), scanner.deleted)
    }

    @Test
    fun onCleared_afterSave_doesNotDeleteAgain() = runTest {
        val scanner = StubScanner(Result.success(candidate()))
        val vm = ReceiptReviewViewModel(scanner, StubService(), CapturedImageTracker())
        vm.onImagesCaptured(listOf("file:///cache/scan/1.jpg"))
        advanceUntilIdle()
        vm.save()
        advanceUntilIdle()
        assertEquals(1, scanner.deleted.size)
        vm.onCleared()
        assertEquals(1, scanner.deleted.size)
    }

    @Test
    fun onCleared_doesNotDeleteImagesThePickerChose() = runTest {
        val scanner = StubScanner(Result.success(candidate()))
        val vm = ReceiptReviewViewModel(scanner, StubService(), CapturedImageTracker())
        vm.onImagesPicked(listOf("content://picked/1"))
        advanceUntilIdle()
        vm.onCleared()
        assertTrue(scanner.deleted.isEmpty())
    }

    @Test
    fun recapture_deletesPreviousImagesBeforeTrackingNewOnes() = runTest {
        val scanner = StubScanner(Result.success(candidate()))
        val tracker = CapturedImageTracker()
        val vm = ReceiptReviewViewModel(scanner, StubService(), tracker)
        vm.onImagesCaptured(listOf("file:///cache/scan/1.jpg"))
        vm.onImagesCaptured(listOf("file:///cache/scan/2.jpg"))
        advanceUntilIdle()
        assertEquals(listOf("file:///cache/scan/1.jpg"), scanner.deleted)
        assertFalse(tracker.isInUse(java.io.File("/cache/scan/1.jpg").absolutePath))
        assertTrue(tracker.isInUse(java.io.File("/cache/scan/2.jpg").absolutePath))
    }
}
