package com.example.receiptreminder.fake

import com.example.receiptreminder.domain.model.ReceiptCandidate
import com.example.receiptreminder.domain.repository.ReceiptScanner

class FakeReceiptScanner(
    private var candidateToReturn: ReceiptCandidate = SampleData.sampleReceiptCandidate()
) : ReceiptScanner {

    override suspend fun scanReceipt(imageUris: List<String>): Result<ReceiptCandidate> {
        return Result.success(candidateToReturn)
    }

    override fun deleteImages(imageUris: List<String>) = Unit

    fun setCandidate(candidate: ReceiptCandidate) {
        candidateToReturn = candidate
    }
}
