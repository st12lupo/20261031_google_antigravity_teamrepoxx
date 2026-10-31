package data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import data.dao.ProductDao
import data.dao.PurchaseDao
import data.dao.ReceiptDao
import data.dao.StoreDao
import data.entity.ProductAliasEntity
import data.entity.ProductEntity
import data.entity.PurchaseEntity
import data.entity.ReceiptEntity
import data.entity.StoreEntity

@Database(
    entities = [
        ProductEntity::class,
        ProductAliasEntity::class,
        ReceiptEntity::class,
        PurchaseEntity::class,
        StoreEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun productDao(): ProductDao
    abstract fun purchaseDao(): PurchaseDao
    abstract fun receiptDao(): ReceiptDao
    abstract fun storeDao(): StoreDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "receipt_reminder.db"
                ).addMigrations(*AppMigrations.ALL).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
