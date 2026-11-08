package com.resuelve.preciosve.ui.main

import com.resuelve.preciosve.data.bcv.BcvRateManager
import com.resuelve.preciosve.domain.bcv.RateStore
import com.resuelve.preciosve.domain.model.BasketComparison
import com.resuelve.preciosve.domain.model.BcvRate
import com.resuelve.preciosve.domain.model.ComparisonGroup
import com.resuelve.preciosve.domain.model.ProductItem
import com.resuelve.preciosve.domain.model.ShoppingItem
import com.resuelve.preciosve.domain.model.Store
import com.resuelve.preciosve.domain.repository.PriceComparisonRepository
import com.resuelve.preciosve.domain.repository.ShoppingListRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private class FakeRepo : PriceComparisonRepository {
        var lastQuery: String? = null
        var returnGroups = listOf<ComparisonGroup>()

        override fun search(query: String): Flow<List<ComparisonGroup>> = flow {
            lastQuery = query
            emit(returnGroups)
        }

        override fun observeRecentSearches(): Flow<List<String>> = kotlinx.coroutines.flow.flowOf(emptyList())
        override suspend fun deleteRecentSearch(query: String) {}
        override suspend fun clearRecentSearches() {}
        override suspend fun getAutocompleteSuggestions(prefix: String): List<String> = emptyList()
    }

    private class FakeShoppingRepo : ShoppingListRepository {
        val itemsFlow = MutableStateFlow<List<ShoppingItem>>(emptyList())
        override fun observeItems(): Flow<List<ShoppingItem>> = itemsFlow
        override fun observeItemCount(): Flow<Int> = MutableStateFlow(0)
        override fun observeBasketComparison(): Flow<BasketComparison> = MutableStateFlow(BasketComparison.empty())
        override suspend fun addItem(group: ComparisonGroup, quantity: Int) {}
        override suspend fun updateQuantity(id: String, quantity: Int) {}
        override suspend fun togglePurchased(id: String, isPurchased: Boolean) {}
        override suspend fun removeItem(id: String) {}
        override suspend fun clearAll() {}
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `estado inicial es Idle`() = runTest(testDispatcher) {
        val repo = FakeRepo()
        val shoppingRepo = FakeShoppingRepo()
        val bcv = BcvRateManager(emptyList(), object : RateStore {
            override fun load(): BcvRate? = null
            override fun save(rate: BcvRate) {}
        })
        val vm = MainViewModel(repo, shoppingRepo, bcv)

        assertEquals(UiState.Idle, vm.uiState.value)
    }

    @Test
    fun `debounce de 400ms y busqueda exitosa`() = runTest(testDispatcher) {
        val repo = FakeRepo()
        val shoppingRepo = FakeShoppingRepo()
        val sampleItem = ProductItem("1", Store.GAMA, "Harina", 1.0, 1.0, 0.0, "", "")
        val sampleGroup = ComparisonGroup.create("harina", "Harina", listOf(sampleItem))
        repo.returnGroups = listOf(sampleGroup)

        val bcv = BcvRateManager(emptyList(), object : RateStore {
            override fun load(): BcvRate? = null
            override fun save(rate: BcvRate) {}
        })
        val vm = MainViewModel(repo, shoppingRepo, bcv)

        vm.onQueryChanged("harina")
        advanceTimeBy(300L)
        // Aún en debounce (no han pasado los 400ms)
        assertEquals(UiState.Idle, vm.uiState.value)

        advanceTimeBy(150L)
        advanceUntilIdle()

        assertEquals("harina", repo.lastQuery)
        assertTrue(vm.uiState.value is UiState.Success)
        val success = vm.uiState.value as UiState.Success
        assertEquals(1, success.groups.size)
    }
}
