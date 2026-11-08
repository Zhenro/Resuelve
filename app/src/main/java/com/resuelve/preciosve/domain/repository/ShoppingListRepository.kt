package com.resuelve.preciosve.domain.repository

import com.resuelve.preciosve.domain.model.BasketComparison
import com.resuelve.preciosve.domain.model.ComparisonGroup
import com.resuelve.preciosve.domain.model.ShoppingItem
import kotlinx.coroutines.flow.Flow

/**
 * Repositorio para la gestión de la lista de compras del usuario
 * y la evaluación de precios globales entre los 3 supermercados.
 */
interface ShoppingListRepository {
    fun observeItems(): Flow<List<ShoppingItem>>
    fun observeItemCount(): Flow<Int>
    fun observeBasketComparison(): Flow<BasketComparison>
    suspend fun addItem(group: ComparisonGroup, quantity: Int = 1)
    suspend fun updateQuantity(id: String, quantity: Int)
    suspend fun togglePurchased(id: String, isPurchased: Boolean)
    suspend fun removeItem(id: String)
    suspend fun clearAll()
}
