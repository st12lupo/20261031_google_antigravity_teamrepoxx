package data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import domain.model.Store

@Entity(tableName = "stores")
data class StoreEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float = 200f,
    val notifyEnabled: Boolean = true,
    val lastNotifiedOnEpochDay: Long? = null
) {
    fun toDomain(): Store = Store(
        id = id,
        name = name,
        latitude = latitude,
        longitude = longitude,
        radiusMeters = radiusMeters,
        notifyEnabled = notifyEnabled,
        lastNotifiedOnEpochDay = lastNotifiedOnEpochDay
    )

    companion object {
        fun fromDomain(domain: Store): StoreEntity = StoreEntity(
            id = domain.id,
            name = domain.name,
            latitude = domain.latitude,
            longitude = domain.longitude,
            radiusMeters = domain.radiusMeters,
            notifyEnabled = domain.notifyEnabled,
            lastNotifiedOnEpochDay = domain.lastNotifiedOnEpochDay
        )
    }
}
