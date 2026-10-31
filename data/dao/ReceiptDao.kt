package data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import data.entity.ReceiptEntity

@Dao
interface ReceiptDao {

    @Query("SELECT * FROM receipts ORDER BY purchasedOnEpochDay DESC")
    suspend fun getAllReceipts(): List<ReceiptEntity>

    @Query(
        """
        SELECT * FROM receipts 
        WHERE storeName = :storeName 
          AND purchasedOnEpochDay = :purchasedOnEpochDay 
          AND totalAmount = :totalAmount 
        LIMIT 1
        """
    )
    suspend fun findDuplicateReceipt(
        storeName: String,
        purchasedOnEpochDay: Long,
        totalAmount: Int
    ): ReceiptEntity?

    @Query("SELECT * FROM receipts WHERE id = :id LIMIT 1")
    suspend fun getReceiptById(id: Long): ReceiptEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReceipt(receipt: ReceiptEntity): Long

    @Query("DELETE FROM receipts WHERE id = :id")
    suspend fun deleteReceipt(id: Long)
}
