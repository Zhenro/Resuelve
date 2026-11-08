package com.resuelve.preciosve.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.resuelve.preciosve.data.bcv.BcvRateManager
import com.resuelve.preciosve.domain.model.BasketComparison
import com.resuelve.preciosve.domain.model.BcvRate
import com.resuelve.preciosve.domain.model.ComparisonGroup
import com.resuelve.preciosve.domain.model.ProductCategory
import com.resuelve.preciosve.domain.model.ShoppingItem
import com.resuelve.preciosve.domain.repository.PriceComparisonRepository
import com.resuelve.preciosve.domain.repository.ShoppingListRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel principal:
 * - Emite la tasa BCV en tiempo real.
 * - Gestiona la búsqueda reactiva con debounce de 400 ms.
 * - Integra la selección rápida de categorías populares.
 * - Gestiona la lista de compras del usuario y la comparativa consolidada de la cesta.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class MainViewModel(
    private val repository: PriceComparisonRepository,
    private val shoppingListRepository: ShoppingListRepository,
    private val bcvRateManager: BcvRateManager
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    val currentSearchQuery: StateFlow<String> = searchQuery.asStateFlow()

    val bcvRate: StateFlow<BcvRate?> = bcvRateManager.rate

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    // Categoría actualmente seleccionada
    private val _selectedCategory = MutableStateFlow<ProductCategory?>(null)
    val selectedCategory: StateFlow<ProductCategory?> = _selectedCategory.asStateFlow()

    // Historial de búsquedas recientes persistente
    val recentSearches: StateFlow<List<String>> =
        repository.observeRecentSearches()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Sugerencias de autocompletado predictivo en vivo
    private val _suggestions = MutableStateFlow<List<String>>(emptyList())
    val suggestions: StateFlow<List<String>> = _suggestions.asStateFlow()

    // Ítems en la lista de compras
    val shoppingItems: StateFlow<List<ShoppingItem>> =
        shoppingListRepository.observeItems()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Mapa de cantidades por grupo para actualizar el estado del botón "+ Agregar" en tiempo real
    val shoppingQuantities: StateFlow<Map<String, Int>> =
        shoppingItems.map { list -> list.associate { it.id to it.quantity } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // Cantidad total de productos en la lista (para badge en BottomNavigationView)
    val shoppingItemCount: StateFlow<Int> =
        shoppingListRepository.observeItemCount()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Evaluación consolidada de la cesta total en los 3 supermercados
    val basketComparison: StateFlow<BasketComparison> =
        shoppingListRepository.observeBasketComparison()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BasketComparison.empty())

    init {
        // Carga y refresco de tasa BCV en segundo plano
        viewModelScope.launch {
            bcvRateManager.refresh()
        }

        // Tubería de búsqueda reactiva no bloqueante
        viewModelScope.launch {
            searchQuery
                .debounce(400L)
                .distinctUntilChanged()
                .flatMapLatest { query ->
                    val clean = query.trim()
                    if (clean.length < 2) {
                        _uiState.value = if (clean.isEmpty()) UiState.Idle else UiState.Empty
                        flowOf(null)
                    } else {
                        _uiState.value = UiState.Loading
                        repository.search(clean)
                            .catch { e ->
                                _uiState.value = UiState.Error(e.message ?: "Error de red al consultar precios")
                            }
                    }
                }
                .collect { groups ->
                    if (groups != null) {
                        _uiState.value = if (groups.isEmpty()) UiState.Empty else UiState.Success(groups)
                    }
                }
        }
    }

    fun onQueryChanged(newQuery: String) {
        if (_selectedCategory.value != null && _selectedCategory.value?.query != newQuery) {
            _selectedCategory.value = null
        }
        searchQuery.value = newQuery
        loadSuggestions(newQuery)
    }

    fun loadSuggestions(prefix: String) {
        viewModelScope.launch {
            if (prefix.trim().length >= 2) {
                _suggestions.value = repository.getAutocompleteSuggestions(prefix)
            } else {
                _suggestions.value = emptyList()
            }
        }
    }

    fun deleteRecentSearch(query: String) {
        viewModelScope.launch {
            repository.deleteRecentSearch(query)
        }
    }

    fun clearRecentSearches() {
        viewModelScope.launch {
            repository.clearRecentSearches()
        }
    }

    fun onCategorySelected(category: ProductCategory) {
        if (_selectedCategory.value?.id == category.id) {
            _selectedCategory.value = null
            searchQuery.value = ""
        } else {
            _selectedCategory.value = category
            searchQuery.value = category.query
        }
    }

    fun clearSearch() {
        _selectedCategory.value = null
        searchQuery.value = ""
        _suggestions.value = emptyList()
    }

    fun refreshBcv() {
        viewModelScope.launch {
            bcvRateManager.refresh(force = true)
        }
    }

    // Acciones de Lista de Compras
    fun addGroupToShoppingList(group: ComparisonGroup) {
        viewModelScope.launch {
            val currentQty = shoppingQuantities.value[group.key] ?: 0
            shoppingListRepository.addItem(group, currentQty + 1)
        }
    }

    fun updateShoppingItemQuantity(id: String, quantity: Int) {
        viewModelScope.launch {
            shoppingListRepository.updateQuantity(id, quantity)
        }
    }

    fun togglePurchased(id: String, isPurchased: Boolean) {
        viewModelScope.launch {
            shoppingListRepository.togglePurchased(id, isPurchased)
        }
    }

    fun addGroupWithQuantity(group: ComparisonGroup, quantity: Int) {
        viewModelScope.launch {
            val currentQty = shoppingQuantities.value[group.key] ?: 0
            shoppingListRepository.addItem(group, currentQty + quantity)
        }
    }

    fun removeShoppingItem(id: String) {
        viewModelScope.launch {
            shoppingListRepository.removeItem(id)
        }
    }

    fun clearShoppingList() {
        viewModelScope.launch {
            shoppingListRepository.clearAll()
        }
    }

    class Factory(
        private val repository: PriceComparisonRepository,
        private val shoppingListRepository: ShoppingListRepository,
        private val bcvRateManager: BcvRateManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainViewModel(repository, shoppingListRepository, bcvRateManager) as T
        }
    }
}
