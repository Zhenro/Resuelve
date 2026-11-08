package com.resuelve.preciosve.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ShoppingDao {

    @Query("SELECT * FROM shopping_items ORDER BY addedAtMillis DESC")
    fun observeAll(): Flow<List<ShoppingItemEntity>>

    @Query("SELECT * FROM shopping_items ORDER BY addedAtMillis DESC")
    suspend fun getAll(): List<ShoppingItemEntity>

    @Query("SELECT COUNT(*) FROM shopping_items")
    fun observeCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(item: ShoppingItemEntity)

    @Query("UPDATE shopping_items SET quantity = :quantity WHERE id = :id")
    suspend fun updateQuantity(id: String, quantity: Int)

    @Query("UPDATE shopping_items SET isPurchased = :isPurchased WHERE id = :id")
    suspend fun updatePurchased(id: String, isPurchased: Boolean)

    @Query("DELETE FROM shopping_items WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM shopping_items")
    suspend fun clearAll()
}
