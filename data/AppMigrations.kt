package data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Room のスキーマ移行。既存の商品・購入履歴・別名を残したまま列を足す */
object AppMigrations {

    /** products.stoppedByUser の追加 SQL。既存の行は 0(印なし) になる。ProductEntity の defaultValue と揃える */
    const val ADD_STOPPED_BY_USER =
        "ALTER TABLE `products` ADD COLUMN `stoppedByUser` INTEGER NOT NULL DEFAULT 0"

    val MIGRATION_1_2: Migration = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(ADD_STOPPED_BY_USER)
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}
