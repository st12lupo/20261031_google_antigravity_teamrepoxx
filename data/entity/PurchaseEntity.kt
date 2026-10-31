package data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import domain.model.Purchase
import domain.model.PurchaseSource

@Entity(
    tableName = "purchases",
    indices = [
        Index(value = ["productId"]),
        Index(value = ["receiptId"]),
        Index(value = ["purchasedOnEpochDay"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ReceiptEntity::class,
            parentColumns = ["id"],
            childColumns = ["receiptId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class PurchaseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val receiptId: Long? = null,
    val productId: Long,
    val purchasedOnEpochDay: Long,
    val quantity: Double = 1.0,
    val price: Int = 0,
    val source: String = "RECEIPT"
) {
    fun toDomain(): Purchase = Purchase(
        id = id,
        receiptId = receiptId,
        productId = productId,
        purchasedOnEpochDay = purchasedOnEpochDay,
        quantity = quantity,
        price = price,
        source = runCatching { PurchaseSource.valueOf(source) }.getOrDefault(PurchaseSource.RECEIPT)
    )

    companion object {
        fun fromDomain(domain: Purchase): PurchaseEntity = PurchaseEntity(
            id = domain.id,
            receiptId = domain.receiptId,
            productId = domain.productId,
            purchasedOnEpochDay = domain.purchasedOnEpochDay,
            quantity = domain.quantity,
            price = domain.price,
            source = domain.source.name
        )
    }
}
