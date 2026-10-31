package data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import domain.model.ProductAlias

@Entity(
    tableName = "product_aliases",
    indices = [
        Index(value = ["rawText"], unique = true),
        Index(value = ["productId"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ProductAliasEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val rawText: String,
    val productId: Long
) {
    fun toDomain(): ProductAlias = ProductAlias(
        id = id,
        rawText = rawText,
        productId = productId
    )

    companion object {
        fun fromDomain(domain: ProductAlias): ProductAliasEntity = ProductAliasEntity(
            id = domain.id,
            rawText = domain.rawText,
            productId = domain.productId
        )
    }
}
