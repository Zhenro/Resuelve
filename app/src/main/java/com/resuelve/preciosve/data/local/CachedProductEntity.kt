package com.resuelve.preciosve.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.resuelve.preciosve.domain.model.ProductItem
import com.resuelve.preciosve.domain.model.Store

/**
 * Tabla de productos cacheados vinculados a una consulta de búsqueda.
 */
@Entity(
    tableName = "cached_products",
    foreignKeys = [
        ForeignKey(
            entity = SearchQueryEntity::class,
            parentColumns = ["query"],
            childColumns = ["searchQuery"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["searchQuery"]),
        Index(value = ["searchQuery", "store"])
    ]
)
data class CachedProductEntity(
    @PrimaryKey val id: String,
    val searchQuery: String,
    val store: String,
    val name: String,
    val basePriceRef: Double,
    val totalPriceRef: Double,
    val totalPriceVes: Double,
    val imageUrl: String,
    val productUrl: String
) {
    fun toDomain(): ProductItem = ProductItem(
        id = id,
        store = Store.valueOf(store),
        name = name,
        basePriceRef = basePriceRef,
        totalPriceRef = totalPriceRef,
        totalPriceVes = totalPriceVes,
        imageUrl = imageUrl,
        productUrl = productUrl
    )

    companion object {
        fun fromDomain(item: ProductItem, searchQuery: String): CachedProductEntity =
            CachedProductEntity(
                id = item.id,
                searchQuery = searchQuery,
                store = item.store.name,
                name = item.name,
                basePriceRef = item.basePriceRef,
                totalPriceRef = item.totalPriceRef,
                totalPriceVes = item.totalPriceVes,
                imageUrl = item.imageUrl,
                productUrl = item.productUrl
            )
    }
}
