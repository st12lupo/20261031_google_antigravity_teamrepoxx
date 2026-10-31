package data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import domain.model.Receipt

@Entity(
    tableName = "receipts",
    indices = [
        Index(value = ["purchasedOnEpochDay"]),
        Index(value = ["storeName", "purchasedOnEpochDay", "totalAmount"])
    ]
)
data class ReceiptEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val storeName: String,
    val purchasedOnEpochDay: Long,
    val totalAmount: Int,
    val createdAtMillis: Long = System.currentTimeMillis()
) {
    fun toDomain(): Receipt = Receipt(
        id = id,
        storeName = storeName,
        purchasedOnEpochDay = purchasedOnEpochDay,
        totalAmount = totalAmount,
        createdAtMillis = createdAtMillis
    )

    companion object {
        fun fromDomain(domain: Receipt): ReceiptEntity = ReceiptEntity(
            id = domain.id,
            storeName = domain.storeName,
            purchasedOnEpochDay = domain.purchasedOnEpochDay,
            totalAmount = domain.totalAmount,
            createdAtMillis = domain.createdAtMillis
        )
    }
}
