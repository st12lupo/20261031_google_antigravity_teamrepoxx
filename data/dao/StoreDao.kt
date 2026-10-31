package data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import data.entity.StoreEntity

@Dao
interface StoreDao {

    @Query("SELECT * FROM stores ORDER BY name ASC")
    suspend fun getAllStores(): List<StoreEntity>

    @Query("SELECT * FROM stores WHERE notifyEnabled = 1 ORDER BY name ASC")
    suspend fun getActiveStores(): List<StoreEntity>

    @Query("SELECT * FROM stores WHERE id = :id LIMIT 1")
    suspend fun getStoreById(id: Long): StoreEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStore(store: StoreEntity): Long

    @Update
    suspend fun updateStore(store: StoreEntity)

    @Query("DELETE FROM stores WHERE id = :id")
    suspend fun deleteStore(id: Long)

    @Query("UPDATE stores SET lastNotifiedOnEpochDay = :epochDay WHERE id = :id")
    suspend fun updateLastNotified(id: Long, epochDay: Long)

    @Query("DELETE FROM stores")
    suspend fun deleteAllStores()
}
