package com.resuelve.preciosve.data.repository

import com.resuelve.preciosve.data.bcv.BcvRateManager
import com.resuelve.preciosve.data.local.ShoppingDao
import com.resuelve.preciosve.data.local.ShoppingItemEntity
import com.resuelve.preciosve.domain.model.BasketComparison
import com.resuelve.preciosve.domain.model.ComparisonGroup
import com.resuelve.preciosve.domain.model.ShoppingItem
import com.resuelve.preciosve.domain.repository.ShoppingListRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class ShoppingListRepositoryImpl(
    private val shoppingDao: ShoppingDao,
    private val bcvRateManager: BcvRateManager,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ShoppingListRepository {

    override fun observeItems(): Flow<List<ShoppingItem>> =
        shoppingDao.observeAll()
            .map { list -> list.map { it.toDomain() } }
            .flowOn(ioDispatcher)

    override fun observeItemCount(): Flow<Int> =
        shoppingDao.observeCount()
            .flowOn(ioDispatcher)

    override fun observeBasketComparison(): Flow<BasketComparison> =
        combine(observeItems(), bcvRateManager.rate) { items, rate ->
            BasketComparison.compute(items, rate)
        }.flowOn(ioDispatcher)

    override suspend fun addItem(group: ComparisonGroup, quantity: Int) = withContext(ioDispatcher) {
        val entity = ShoppingItemEntity.fromGroup(group, quantity)
        shoppingDao.insertOrUpdate(entity)
    }

    override suspend fun updateQuantity(id: String, quantity: Int) = withContext(ioDispatcher) {
        if (quantity <= 0) {
            shoppingDao.deleteById(id)
        } else {
            shoppingDao.updateQuantity(id, quantity)
        }
    }

    override suspend fun togglePurchased(id: String, isPurchased: Boolean) = withContext(ioDispatcher) {
        shoppingDao.updatePurchased(id, isPurchased)
    }

    override suspend fun removeItem(id: String) = withContext(ioDispatcher) {
        shoppingDao.deleteById(id)
    }

    override suspend fun clearAll() = withContext(ioDispatcher) {
        shoppingDao.clearAll()
    }
}
