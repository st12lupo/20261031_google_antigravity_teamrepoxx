package data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import data.entity.ProductAliasEntity
import data.entity.ProductEntity

@Dao
interface ProductDao {

    @Query("SELECT * FROM products ORDER BY name ASC")
    suspend fun getAllProducts(): List<ProductEntity>

    @Query("SELECT * FROM products WHERE id = :id LIMIT 1")
    suspend fun getProductById(id: Long): ProductEntity?

    @Query("SELECT * FROM products WHERE name = :name LIMIT 1")
    suspend fun findProductByName(name: String): ProductEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: ProductEntity): Long

    @Update
    suspend fun updateProduct(product: ProductEntity)

    @Query("DELETE FROM products WHERE id = :id")
    suspend fun deleteProduct(id: Long)

    @Query("UPDATE products SET isTracked = :isTracked WHERE id = :id")
    suspend fun updateTracked(id: Long, isTracked: Boolean)

    @Query("UPDATE products SET snoozedUntilEpochDay = :snoozedUntilEpochDay WHERE id = :id")
    suspend fun updateSnooze(id: Long, snoozedUntilEpochDay: Long?)

    @Query("SELECT * FROM product_aliases")
    suspend fun getAllAliases(): List<ProductAliasEntity>

    @Query("SELECT * FROM product_aliases WHERE rawText = :rawText LIMIT 1")
    suspend fun findAlias(rawText: String): ProductAliasEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlias(alias: ProductAliasEntity): Long
}
