package com.resuelve.preciosve.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface SearchDao {

    @Query("SELECT * FROM search_queries WHERE query = :query LIMIT 1")
    suspend fun getQuery(query: String): SearchQueryEntity?

    @Query("SELECT * FROM cached_products WHERE searchQuery = :query")
    suspend fun getProductsForQuery(query: String): List<CachedProductEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuery(queryEntity: SearchQueryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProducts(products: List<CachedProductEntity>)

    @Query("DELETE FROM search_queries WHERE query = :query")
    suspend fun deleteQuery(query: String)

    @Query("DELETE FROM search_queries WHERE expiresAtMillis < :nowMillis")
    suspend fun deleteExpiredQueries(nowMillis: Long)

    @Query("SELECT * FROM cached_products WHERE name LIKE '%' || :query || '%' LIMIT 80")
    suspend fun searchCachedProducts(query: String): List<CachedProductEntity>

    @Query("SELECT DISTINCT name FROM cached_products WHERE name LIKE :prefix || '%' LIMIT 10")
    suspend fun getAutocompleteSuggestions(prefix: String): List<String>

    @Query("SELECT query FROM recent_searches ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecentSearches(limit: Int = 10): kotlinx.coroutines.flow.Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecentSearch(recentSearch: RecentSearchEntity)

    @Query("DELETE FROM recent_searches WHERE query = :query")
    suspend fun deleteRecentSearch(query: String)

    @Query("DELETE FROM recent_searches")
    suspend fun clearRecentSearches()

    @Transaction
    suspend fun saveSearchResults(
        query: String,
        createdAtMillis: Long,
        expiresAtMillis: Long,
        products: List<CachedProductEntity>
    ) {
        insertQuery(SearchQueryEntity(query, createdAtMillis, expiresAtMillis))
        if (products.isNotEmpty()) {
            insertProducts(products)
        }
    }
}
