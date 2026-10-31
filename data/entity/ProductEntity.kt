package data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import domain.model.Product

@Entity(
    tableName = "products",
    indices = [Index(value = ["name"], unique = true)]
)
data class ProductEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val category: String = "日用品",
    val isTracked: Boolean = true,
    val snoozedUntilEpochDay: Long? = null
) {
    fun toDomain(): Product = Product(
        id = id,
        name = name,
        category = category,
        isTracked = isTracked,
        snoozedUntilEpochDay = snoozedUntilEpochDay
    )

    companion object {
        fun fromDomain(domain: Product): ProductEntity = ProductEntity(
            id = domain.id,
            name = domain.name,
            category = domain.category,
            isTracked = domain.isTracked,
            snoozedUntilEpochDay = domain.snoozedUntilEpochDay
        )
    }
}
