package com.resuelve.preciosve.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.resuelve.preciosve.domain.model.ComparisonGroup
import com.resuelve.preciosve.domain.model.ShoppingItem
import com.resuelve.preciosve.domain.model.Store

@Entity(tableName = "shopping_items")
data class ShoppingItemEntity(
    @PrimaryKey
    val id: String,
    val displayName: String,
    val imageUrl: String,
    val quantity: Int,
    val gamaPriceRef: Double?,
    val forumPriceRef: Double?,
    val centralPriceRef: Double?,
    val gamaUrl: String,
    val forumUrl: String,
    val centralUrl: String,
    val addedAtMillis: Long,
    val isPurchased: Boolean = false
) {
    fun toDomain(): ShoppingItem = ShoppingItem(
        id = id,
        displayName = displayName,
        imageUrl = imageUrl,
        quantity = quantity,
        gamaPriceRef = gamaPriceRef,
        forumPriceRef = forumPriceRef,
        centralPriceRef = centralPriceRef,
        gamaUrl = gamaUrl,
        forumUrl = forumUrl,
        centralUrl = centralUrl,
        addedAtMillis = addedAtMillis,
        isPurchased = isPurchased
    )

    companion object {
        fun fromGroup(group: ComparisonGroup, quantity: Int = 1, addedAtMillis: Long = System.currentTimeMillis()): ShoppingItemEntity {
            val gamaItem = group.cheapestOf(Store.GAMA)
            val forumItem = group.cheapestOf(Store.FORUM)
            val centralItem = group.cheapestOf(Store.CENTRAL)

            val imageUrl = gamaItem?.imageUrl?.takeIf { it.isNotBlank() }
                ?: forumItem?.imageUrl?.takeIf { it.isNotBlank() }
                ?: centralItem?.imageUrl?.takeIf { it.isNotBlank() }
                .orEmpty()

            return ShoppingItemEntity(
                id = group.key,
                displayName = group.displayName,
                imageUrl = imageUrl,
                quantity = quantity.coerceAtLeast(1),
                gamaPriceRef = gamaItem?.totalPriceRef,
                forumPriceRef = forumItem?.totalPriceRef,
                centralPriceRef = centralItem?.totalPriceRef,
                gamaUrl = gamaItem?.productUrl.orEmpty(),
                forumUrl = forumItem?.productUrl.orEmpty(),
                centralUrl = centralItem?.productUrl.orEmpty(),
                addedAtMillis = addedAtMillis
            )
        }
    }
}
