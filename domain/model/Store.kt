package domain.model

data class Store(
    val id: Long = 0,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float = 200f,
    val notifyEnabled: Boolean = true,
    val lastNotifiedOnEpochDay: Long? = null
)
