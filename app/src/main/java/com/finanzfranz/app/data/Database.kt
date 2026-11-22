package com.finanzfranz.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface FinDao {
    @Query("SELECT * FROM tx ORDER BY epochDay DESC, createdAt DESC") fun txs(): Flow<List<Tx>>
    @Query("SELECT * FROM category ORDER BY sort, id") fun categories(): Flow<List<Category>>
    @Query("SELECT * FROM fixed_cost ORDER BY name COLLATE NOCASE") fun fixed(): Flow<List<FixedCost>>
    @Query("SELECT * FROM settings WHERE id = 1") fun settings(): Flow<Settings?>

    @Query("SELECT * FROM tx") suspend fun txsNow(): List<Tx>
    @Query("SELECT * FROM category") suspend fun categoriesNow(): List<Category>
    @Query("SELECT * FROM fixed_cost") suspend fun fixedNow(): List<FixedCost>
    @Query("SELECT * FROM settings WHERE id = 1") suspend fun settingsNow(): Settings?

    @Upsert suspend fun upsertTx(t: Tx)
    @Insert suspend fun insertTxs(t: List<Tx>)
    @Delete suspend fun deleteTx(t: Tx)

    @Upsert suspend fun upsertCategory(c: Category)
    @Insert suspend fun insertCategories(c: List<Category>)
    @Delete suspend fun deleteCategory(c: Category)
    @Query("UPDATE tx SET categoryId = :to WHERE categoryId = :from") suspend fun moveCategory(from: Long, to: Long)

    @Upsert suspend fun upsertFixed(f: FixedCost)
    @Insert suspend fun insertFixed(f: List<FixedCost>)
    @Delete suspend fun deleteFixed(f: FixedCost)
    @Query("UPDATE tx SET fixedCostId = NULL WHERE fixedCostId = :id") suspend fun detachFixed(id: Long)

    @Upsert suspend fun upsertSettings(s: Settings)

    @Query("DELETE FROM tx") suspend fun clearTx()
    @Query("DELETE FROM category") suspend fun clearCategories()
    @Query("DELETE FROM fixed_cost") suspend fun clearFixed()
    @Query("DELETE FROM settings") suspend fun clearSettings()
}

@Database(entities = [Tx::class, Category::class, FixedCost::class, Settings::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): FinDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "finanzfranz.db")
                .build().also { instance = it }
        }
    }
}
