package data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import data.entity.PurchaseEntity

@Dao
interface PurchaseDao {

    @Query("SELECT * FROM purchases ORDER BY purchasedOnEpochDay DESC")
    suspend fun getAllPurchases(): List<PurchaseEntity>

    @Query("SELECT * FROM purchases WHERE productId = :productId ORDER BY purchasedOnEpochDay ASC")
    suspend fun getPurchasesForProduct(productId: Long): List<PurchaseEntity>

    @Query(
        """
        SELECT * FROM purchases 
        WHERE productId = :productId 
          AND purchasedOnEpochDay >= :minEpochDay 
          AND purchasedOnEpochDay <= :maxEpochDay
        ORDER BY purchasedOnEpochDay DESC
        """
    )
    suspend fun getRecentPurchasesForProduct(
        productId: Long,
        minEpochDay: Long,
        maxEpochDay: Long
    ): List<PurchaseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPurchase(purchase: PurchaseEntity): Long

    @Update
    suspend fun updatePurchase(purchase: PurchaseEntity)

    @Query("DELETE FROM purchases WHERE id = :id")
    suspend fun deletePurchase(id: Long)

    @Query("DELETE FROM purchases")
    suspend fun deleteAllPurchases()

    @Query("DELETE FROM purchases WHERE receiptId = :receiptId")
    suspend fun deletePurchasesByReceiptId(receiptId: Long)
}
