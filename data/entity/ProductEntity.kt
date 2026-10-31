package data.entity

import androidx.room.ColumnInfo
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
    val snoozedUntilEpochDay: Long? = null,
    @ColumnInfo(defaultValue = "0")
    val stoppedByUser: Boolean = false
) {
    fun toDomain(): Product = Product(
        id = id,
        name = name,
        category = category,
        isTracked = isTracked,
        snoozedUntilEpochDay = snoozedUntilEpochDay,
        stoppedByUser = stoppedByUser
    )

    companion object {
        fun fromDomain(domain: Product): ProductEntity = ProductEntity(
            id = domain.id,
            name = domain.name,
            category = domain.category,
            isTracked = domain.isTracked,
            snoozedUntilEpochDay = domain.snoozedUntilEpochDay,
            stoppedByUser = domain.stoppedByUser
        )
    }
}
