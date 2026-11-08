package com.resuelve.preciosve.ui.main

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.resuelve.preciosve.PreciosApp
import com.resuelve.preciosve.R
import com.resuelve.preciosve.databinding.ActivityMainBinding
import com.resuelve.preciosve.databinding.DialogFiltersBinding
import com.resuelve.preciosve.databinding.DialogProductDetailBinding
import com.resuelve.preciosve.databinding.ItemStorePriceRowBinding
import com.resuelve.preciosve.domain.model.BasketComparison
import com.resuelve.preciosve.domain.model.BcvRate
import com.resuelve.preciosve.domain.model.ComparisonGroup
import com.resuelve.preciosve.domain.model.ProductCategory
import com.resuelve.preciosve.domain.model.ProductItem
import com.resuelve.preciosve.domain.model.Store
import com.resuelve.preciosve.domain.util.ProductEntityResolver
import com.resuelve.preciosve.domain.util.RelevanceScorer
import com.resuelve.preciosve.domain.util.VenezuelanBarcodeRegistry
import com.resuelve.preciosve.ui.adapter.CategoryAdapter
import com.resuelve.preciosve.ui.adapter.ComparisonAdapter
import com.resuelve.preciosve.ui.adapter.ShoppingListAdapter
import com.resuelve.preciosve.ui.adapter.SuggestionAdapter
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * Pantalla principal de Resuelve Precios:
 * - Catálogo Maestro con búsqueda instantánea FTS y Stale-While-Revalidate (<5ms).
 * - Escáner de código de barras físico con cámara (EAN/UPC).
 * - Autocompletado predictivo en vivo y tolerancia a errores tipográficos.
 * - Historial persistente de búsquedas recientes.
 * - Precios por unidad de medida ($/kg, $/100g).
 * - BottomSheets modernos, Modo Pasillo y Canasta Mixta Súper Ahorro.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var comparisonAdapter: ComparisonAdapter
    private lateinit var categoryAdapter: CategoryAdapter
    private lateinit var shoppingListAdapter: ShoppingListAdapter
    private lateinit var suggestionAdapter: SuggestionAdapter

    private var currentFilterStore: Store? = null
    private var currentSortMode: SortMode = SortMode.RELEVANCE
    private var originalGroups: List<ComparisonGroup> = emptyList()

    enum class SortMode {
        RELEVANCE,
        AVAILABILITY,
        CHEAPEST,
        MAX_SAVINGS,
        NAME
    }

    private val viewModel: MainViewModel by lazy {
        val app = application as PreciosApp
        val factory = MainViewModel.Factory(
            repository = app.container.priceComparisonRepository,
            shoppingListRepository = app.container.shoppingListRepository,
            bcvRateManager = app.container.bcvRateManager
        )
        ViewModelProvider(this, factory)[MainViewModel::class.java]
    }

    private val numberFormat: NumberFormat by lazy {
        NumberFormat.getNumberInstance(Locale.Builder().setLanguage("es").setRegion("VE").build()).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
    }

    // Launcher de Cámara para Escáner de Código de Barras
    private val barcodeLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            val barcode = result.contents.trim()
            val localName = VenezuelanBarcodeRegistry.resolveBarcodeLocal(barcode)
            if (localName != null) {
                applyBarcodeSearch(localName)
            } else {
                Toast.makeText(this, "Consultando base de alimentos...", Toast.LENGTH_SHORT).show()
                lifecycleScope.launch {
                    val app = application as PreciosApp
                    val onlineName = VenezuelanBarcodeRegistry.resolveBarcodeOnline(barcode, app.container.httpClient)
                    if (onlineName != null) {
                        applyBarcodeSearch(onlineName)
                    } else {
                        showUnresolvedBarcodeDialog(barcode)
                    }
                }
            }
        }
    }

    private fun applyBarcodeSearch(productName: String) {
        binding.etSearch.setText(productName)
        binding.etSearch.setSelection(productName.length)
        binding.rvSearchSuggestions.visibility = View.GONE
        viewModel.onQueryChanged(productName)
        Toast.makeText(this, "📦 Identificado: $productName", Toast.LENGTH_LONG).show()
    }

    private fun showUnresolvedBarcodeDialog(barcode: String) {
        AlertDialog.Builder(this)
            .setTitle("Código de barras no catalogado")
            .setMessage("El código $barcode no está registrado en la base de datos de alimentos. Te recomendamos buscar directamente por el nombre comercial (ej. 'Mavesa', 'Arroz Mary', 'Club Social').")
            .setPositiveButton("Escribir nombre") { _, _ ->
                binding.etSearch.requestFocus()
                val imm = getSystemService(INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
                imm?.showSoftInput(binding.etSearch, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupSearchRecyclerView()
        setupSuggestionsRecyclerView()
        setupCategoriesRecyclerView()
        setupShoppingListRecyclerView()
        setupSearchInput()
        setupBarcodeScanner()
        setupPopularSearchChips()
        setupFilterButton()
        setupBottomNav()
        setupBcvRefresh()
        setupClearShoppingListButton()
        setupShareShoppingListButton()
        observeViewModel()
    }

    private fun setupSearchRecyclerView() {
        comparisonAdapter = ComparisonAdapter(
            onProductClick = { group ->
                showProductDetailBottomSheet(group)
            },
            onAddToListClick = { group ->
                handleProductAddToList(group)
            },
            onStoreItemClick = { group, _ ->
                showProductDetailBottomSheet(group)
            }
        )

        binding.rvComparisons.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = comparisonAdapter
            setHasFixedSize(true)
            setItemViewCacheSize(10)
        }
    }

    private fun setupSuggestionsRecyclerView() {
        suggestionAdapter = SuggestionAdapter { selectedSuggestion ->
            binding.etSearch.setText(selectedSuggestion)
            binding.etSearch.setSelection(selectedSuggestion.length)
            binding.rvSearchSuggestions.visibility = View.GONE
            viewModel.onQueryChanged(selectedSuggestion)
        }

        binding.rvSearchSuggestions.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = suggestionAdapter
        }
    }

    private fun handleProductAddToList(group: ComparisonGroup) {
        viewModel.addGroupToShoppingList(group)
        Toast.makeText(this, "¡${group.displayName} agregado a tu cesta!", Toast.LENGTH_SHORT).show()
    }

    private fun setupCategoriesRecyclerView() {
        categoryAdapter = CategoryAdapter(ProductCategory.PRESET_CATEGORIES) { category ->
            viewModel.onCategorySelected(category)
        }

        binding.rvCategories.apply {
            layoutManager = LinearLayoutManager(this@MainActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = categoryAdapter
            setHasFixedSize(true)
        }
    }

    private fun setupShoppingListRecyclerView() {
        shoppingListAdapter = ShoppingListAdapter(
            onQuantityChange = { id, newQty ->
                viewModel.updateShoppingItemQuantity(id, newQty)
            },
            onDeleteClick = { id ->
                viewModel.removeShoppingItem(id)
            },
            onTogglePurchased = { id, isPurchased ->
                viewModel.togglePurchased(id, isPurchased)
            }
        )

        binding.rvShoppingItems.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = shoppingListAdapter
            setHasFixedSize(false)
        }
    }

    private fun setupSearchInput() {
        binding.etSearch.doAfterTextChanged { text ->
            val query = text?.toString().orEmpty()
            binding.btnClearSearch.isVisible = query.isNotEmpty()
            viewModel.onQueryChanged(query)
        }

        binding.btnClearSearch.setOnClickListener {
            binding.etSearch.setText("")
            binding.rvSearchSuggestions.visibility = View.GONE
            viewModel.clearSearch()
        }
    }

    private fun setupBarcodeScanner() {
        binding.btnBarcodeScan.setOnClickListener {
            val options = ScanOptions().apply {
                setDesiredBarcodeFormats(ScanOptions.ONE_D_CODE_TYPES)
                setPrompt("Apunta al código de barras del producto (EAN / UPC)")
                setCameraId(0)
                setBeepEnabled(true)
                setBarcodeImageEnabled(false)
                setOrientationLocked(false)
            }
            try {
                barcodeLauncher.launch(options)
            } catch (e: Exception) {
                Toast.makeText(this, "No se pudo abrir la cámara: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupPopularSearchChips() {
        val idle = binding.layoutIdleView
        idle.chipHarinaPan.setOnClickListener { executeChipSearch("Harina Pan") }
        idle.chipArrozMary.setOnClickListener { executeChipSearch("Arroz Mary") }
        idle.chipYoka.setOnClickListener { executeChipSearch("Yogurt Yoka") }
        idle.chipCharmy.setOnClickListener { executeChipSearch("Charmy") }
        idle.chipOreo.setOnClickListener { executeChipSearch("Oreo") }
        idle.chipLeche.setOnClickListener { executeChipSearch("Leche") }
        idle.chipPasta.setOnClickListener { executeChipSearch("Pasta") }
        idle.chipCafe.setOnClickListener { executeChipSearch("Café") }

        idle.btnClearRecentSearches.setOnClickListener {
            viewModel.clearRecentSearches()
        }
    }

    private fun executeChipSearch(query: String) {
        binding.etSearch.setText(query)
        binding.etSearch.setSelection(query.length)
        binding.rvSearchSuggestions.visibility = View.GONE
        viewModel.onQueryChanged(query)
    }

    private fun renderRecentSearches(searches: List<String>) {
        val container = binding.layoutIdleView.layoutRecentSearchesContainer
        val chipGroup = binding.layoutIdleView.chipGroupRecentSearches
        if (searches.isEmpty()) {
            container.visibility = View.GONE
            return
        }

        container.visibility = View.VISIBLE
        chipGroup.removeAllViews()

        for (query in searches) {
            val chip = Chip(this).apply {
                text = query
                isCloseIconVisible = true
                setChipBackgroundColorResource(R.color.card_elevated)
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_dark_primary))
                chipStrokeWidth = 1f
                setChipStrokeColorResource(R.color.card_border)
                setOnClickListener {
                    executeChipSearch(query)
                }
                setOnCloseIconClickListener {
                    viewModel.deleteRecentSearch(query)
                }
            }
            chipGroup.addView(chip)
        }
    }

    private fun setupFilterButton() {
        binding.btnFilters.setOnClickListener {
            showFilterBottomSheet()
        }
    }

    private fun showFilterBottomSheet() {
        val dialog = BottomSheetDialog(this)
        val filterBinding = DialogFiltersBinding.inflate(layoutInflater)
        dialog.setContentView(filterBinding.root)

        // Sincronizar estado inicial de tiendas
        when (currentFilterStore) {
            Store.FORUM -> filterBinding.chipStoreForum.isChecked = true
            Store.GAMA -> filterBinding.chipStoreGama.isChecked = true
            Store.CENTRAL -> filterBinding.chipStoreCentral.isChecked = true
            null -> filterBinding.chipStoreAll.isChecked = true
        }

        // Sincronizar estado inicial de ordenamiento
        when (currentSortMode) {
            SortMode.RELEVANCE -> filterBinding.chipSortRelevance.isChecked = true
            SortMode.AVAILABILITY -> filterBinding.chipSortAvailability.isChecked = true
            SortMode.CHEAPEST -> filterBinding.chipSortCheapest.isChecked = true
            SortMode.MAX_SAVINGS -> filterBinding.chipSortSavings.isChecked = true
            SortMode.NAME -> filterBinding.chipSortName.isChecked = true
        }

        filterBinding.btnResetFilters.setOnClickListener {
            currentFilterStore = null
            currentSortMode = SortMode.RELEVANCE
            applyFilterAndSort()
            dialog.dismiss()
        }

        filterBinding.btnApplyFilters.setOnClickListener {
            currentFilterStore = when (filterBinding.chipGroupStores.checkedChipId) {
                R.id.chipStoreForum -> Store.FORUM
                R.id.chipStoreGama -> Store.GAMA
                R.id.chipStoreCentral -> Store.CENTRAL
                else -> null
            }

            currentSortMode = when (filterBinding.chipGroupSort.checkedChipId) {
                R.id.chipSortAvailability -> SortMode.AVAILABILITY
                R.id.chipSortCheapest -> SortMode.CHEAPEST
                R.id.chipSortSavings -> SortMode.MAX_SAVINGS
                R.id.chipSortName -> SortMode.NAME
                else -> SortMode.RELEVANCE
            }

            applyFilterAndSort()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun applyFilterAndSort() {
        if (originalGroups.isEmpty()) return

        var filtered = originalGroups
        currentFilterStore?.let { store ->
            filtered = filtered.filter { group -> group.items.any { it.store == store } }
        }

        val q = binding.etSearch.text.toString().trim()
        val sorted = when (currentSortMode) {
            SortMode.RELEVANCE -> RelevanceScorer.filterAndRank(filtered, q)
            SortMode.AVAILABILITY -> filtered.sortedWith(compareByDescending<ComparisonGroup> { it.items.size }.thenBy { it.displayName })
            SortMode.CHEAPEST -> filtered.sortedBy { it.cheapest.totalPriceRef }
            SortMode.MAX_SAVINGS -> filtered.sortedByDescending { it.savingsRef }
            SortMode.NAME -> filtered.sortedBy { it.displayName }
        }

        val count = sorted.size
        val noun = if (count == 1) "producto" else "productos"
        binding.tvResultsCount.text = "$count $noun"
        binding.tvResultsCount.visibility = View.VISIBLE
        comparisonAdapter.submitList(sorted)
    }

    private fun setupBottomNav() {
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_search -> {
                    binding.layoutSearchTab.visibility = View.VISIBLE
                    binding.layoutShoppingListTab.visibility = View.GONE
                    true
                }
                R.id.nav_shopping_list -> {
                    binding.layoutSearchTab.visibility = View.GONE
                    binding.layoutShoppingListTab.visibility = View.VISIBLE
                    true
                }
                else -> false
            }
        }
    }

    private fun setupBcvRefresh() {
        binding.btnRefreshBcv.setOnClickListener {
            viewModel.refreshBcv()
            Toast.makeText(this, "Actualizando tasa oficial BCV...", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupClearShoppingListButton() {
        binding.btnClearShoppingList.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.shopping_list_title)
                .setMessage(R.string.clear_list_confirm)
                .setPositiveButton(R.string.confirm_yes) { _, _ ->
                    viewModel.clearShoppingList()
                }
                .setNegativeButton(R.string.confirm_no, null)
                .show()
        }
    }

    private fun setupShareShoppingListButton() {
        binding.btnShareShoppingList.setOnClickListener {
            val items = viewModel.shoppingItems.value
            if (items.isEmpty()) {
                Toast.makeText(this, "Tu lista de compras está vacía", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val basket = viewModel.basketComparison.value
            val sb = StringBuilder()
            sb.append("🛒 *Mi Lista de Compras - Resuelve Precios*\n\n")
            items.forEach { item ->
                val status = if (item.isPurchased) "✅" else "▫️"
                val bestPrice = listOfNotNull(item.gamaPriceRef, item.forumPriceRef, item.centralPriceRef).minOrNull()
                val refPrice = if (bestPrice != null && bestPrice > 0) " (Ref. ${String.format(Locale.ROOT, "%.2f", bestPrice * item.quantity)})" else ""
                sb.append("$status ${item.quantity}x ${item.displayName}$refPrice\n")
            }

            sb.append("\n━━━━━━━━━━━━━━━━━━━━\n")
            if (basket.winnerStore != null) {
                sb.append("🏆 *Mejor Opción Total:* ${basket.winnerStore.displayName}\n")
                sb.append("💵 Total estimado: Ref. ${String.format(Locale.ROOT, "%.2f", basket.winnerTotalRef)}\n")
                if (basket.maxSavingsRef > 0.0) {
                    sb.append("💎 Ahorro estimado: Ref. ${numberFormat.format(basket.maxSavingsRef)}\n")
                }
            }
            if (basket.optimalMixedSavingsRef > 0.0) {
                sb.append("✨ *Canasta Mixta Súper Ahorro:* Ref. ${String.format(Locale.ROOT, "%.2f", basket.optimalMixedTotalRef)} (¡Ahorraste Ref. ${numberFormat.format(basket.optimalMixedSavingsRef)} más!)\n")
            }
            sb.append("\n_Comparado con Resuelve Precios en Caracas_")

            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, sb.toString())
            }
            startActivity(Intent.createChooser(sendIntent, "Compartir lista de compras vía"))
        }
    }

    private fun showProductDetailBottomSheet(group: ComparisonGroup) {
        val dialog = BottomSheetDialog(this)
        val sheetBinding = DialogProductDetailBinding.inflate(layoutInflater)
        dialog.setContentView(sheetBinding.root)

        sheetBinding.tvDetailTitle.text = group.displayName
        val normalized = ProductEntityResolver.normalize(group.cheapest)
        sheetBinding.tvDetailBrand.text = normalized.brand?.uppercase() ?: "GENERAL"
        sheetBinding.tvDetailWeight.text = normalized.weightFormatted ?: "ESTÁNDAR"

        val repImage = group.items.firstOrNull { it.imageUrl.isNotBlank() }?.imageUrl
        if (!repImage.isNullOrBlank()) {
            sheetBinding.ivDetailImage.load(repImage) {
                placeholder(R.drawable.ic_launcher)
                error(R.drawable.ic_launcher)
            }
        } else {
            sheetBinding.ivDetailImage.setImageResource(R.drawable.ic_launcher)
        }

        val cheapestPrice = group.cheapest.totalPriceRef
        val mostExpensivePrice = group.mostExpensive.totalPriceRef
        if (group.items.size > 1 && group.savingsRef > 0.001) {
            val minStr = String.format(Locale.ROOT, "%.2f", cheapestPrice)
            val maxStr = String.format(Locale.ROOT, "%.2f", mostExpensivePrice)
            sheetBinding.tvDetailPriceRefRange.text = "Ref. $minStr - $maxStr"
            val savingsStr = numberFormat.format(group.savingsRef)
            val percent = ((mostExpensivePrice - cheapestPrice) / mostExpensivePrice * 100).toInt()
            sheetBinding.tvDetailSavingsNote.text = "¡Ahorras Ref. $savingsStr (-$percent%) en ${group.cheapest.store.displayName}!"
            sheetBinding.tvDetailSavingsNote.visibility = View.VISIBLE
        } else {
            val priceStr = String.format(Locale.ROOT, "%.2f", cheapestPrice)
            sheetBinding.tvDetailPriceRefRange.text = "Ref. $priceStr"
            sheetBinding.tvDetailSavingsNote.visibility = View.GONE
        }

        // Precio unitario $/kg o $/100g
        val unitPriceStr = ProductEntityResolver.calculateUnitPrice(cheapestPrice, normalized.weightGramsOrMl)
        if (unitPriceStr != null) {
            sheetBinding.tvDetailUnitPrice.text = "Precio estandarizado: $unitPriceStr"
            sheetBinding.tvDetailUnitPrice.visibility = View.VISIBLE
        } else {
            sheetBinding.tvDetailUnitPrice.visibility = View.GONE
        }

        val rate = viewModel.bcvRate.value?.vesPerRef
        if (rate != null && rate > 0) {
            val minVes = numberFormat.format(cheapestPrice * rate)
            val maxVes = numberFormat.format(mostExpensivePrice * rate)
            sheetBinding.tvDetailPriceVesRange.text = if (group.items.size > 1 && group.savingsRef > 0.001) {
                "Aprox. Bs. $minVes - $maxVes (Tasa BCV)"
            } else {
                "Aprox. Bs. $minVes (Tasa BCV)"
            }
            sheetBinding.tvDetailPriceVesRange.visibility = View.VISIBLE
        } else {
            sheetBinding.tvDetailPriceVesRange.visibility = View.GONE
        }

        // Renderizar ofertas de cada comercio
        val container = sheetBinding.containerDetailStoreRows
        container.removeAllViews()
        val inflater = LayoutInflater.from(this)
        group.items.forEachIndexed { index, item ->
            val rowBinding = ItemStorePriceRowBinding.inflate(inflater, container, false)
            when (item.store) {
                Store.GAMA -> {
                    rowBinding.tvStoreBadge.text = item.store.displayName
                    rowBinding.tvStoreBadge.setBackgroundResource(R.drawable.bg_tag_gama)
                    rowBinding.tvStoreBadge.setTextColor(ContextCompat.getColor(this, R.color.store_gama))
                }
                Store.FORUM -> {
                    rowBinding.tvStoreBadge.text = item.store.displayName
                    rowBinding.tvStoreBadge.setBackgroundResource(R.drawable.bg_tag_forum)
                    rowBinding.tvStoreBadge.setTextColor(ContextCompat.getColor(this, R.color.store_forum))
                }
                Store.CENTRAL -> {
                    rowBinding.tvStoreBadge.text = item.store.displayName
                    rowBinding.tvStoreBadge.setBackgroundResource(R.drawable.bg_tag_central)
                    rowBinding.tvStoreBadge.setTextColor(ContextCompat.getColor(this, R.color.store_central))
                }
            }
            rowBinding.tvBranchName.text = item.store.branchName
            rowBinding.tvCheapestBadge.isVisible = (index == 0 && group.items.size > 1)
            rowBinding.tvPriceRef.text = getString(R.string.price_ref_format, item.totalPriceRef)
            if (item.hasVes) {
                rowBinding.tvPriceVes.text = getString(R.string.price_ves_format, numberFormat.format(item.totalPriceVes))
                rowBinding.tvPriceVes.visibility = View.VISIBLE
            } else {
                rowBinding.tvPriceVes.visibility = View.GONE
            }

            rowBinding.root.setOnClickListener {
                if (item.productUrl.isNotEmpty()) {
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.productUrl)))
                    } catch (_: Exception) {}
                }
            }
            container.addView(rowBinding.root)
        }

        // Stepper de Cantidad
        var currentQuantity = 1
        sheetBinding.tvDetailQuantity.text = currentQuantity.toString()
        sheetBinding.btnDetailDecrement.setOnClickListener {
            if (currentQuantity > 1) {
                currentQuantity--
                sheetBinding.tvDetailQuantity.text = currentQuantity.toString()
            }
        }
        sheetBinding.btnDetailIncrement.setOnClickListener {
            if (currentQuantity < 99) {
                currentQuantity++
                sheetBinding.tvDetailQuantity.text = currentQuantity.toString()
            }
        }

        sheetBinding.btnDetailAddToList.setOnClickListener {
            viewModel.addGroupWithQuantity(group, currentQuantity)
            Toast.makeText(this, "¡$currentQuantity x ${group.displayName} agregado a tu cesta!", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.bcvRate.collect { rate ->
                        renderBcvRate(rate)
                    }
                }

                launch {
                    viewModel.uiState.collect { state ->
                        renderUiState(state)
                    }
                }

                launch {
                    viewModel.suggestions.collect { suggestions ->
                        suggestionAdapter.submitList(suggestions)
                        binding.rvSearchSuggestions.isVisible = suggestions.isNotEmpty()
                    }
                }

                launch {
                    viewModel.recentSearches.collect { searches ->
                        renderRecentSearches(searches)
                    }
                }

                launch {
                    viewModel.selectedCategory.collect { category ->
                        categoryAdapter.setSelectedCategory(category?.id)
                        if (category != null && binding.etSearch.text.toString() != category.query) {
                            binding.etSearch.setText(category.query)
                            binding.etSearch.setSelection(category.query.length)
                        }
                    }
                }

                launch {
                    viewModel.shoppingQuantities.collect { quantities ->
                        comparisonAdapter.updateShoppingListQuantities(quantities)
                    }
                }

                launch {
                    viewModel.shoppingItems.collect { items ->
                        shoppingListAdapter.submitList(items)
                        val isEmpty = items.isEmpty()
                        binding.containerShoppingListContent.isVisible = !isEmpty
                        binding.layoutEmptyShoppingList.isVisible = isEmpty
                        binding.btnClearShoppingList.isVisible = !isEmpty
                    }
                }

                launch {
                    viewModel.shoppingItemCount.collect { count ->
                        val badge = binding.bottomNav.getOrCreateBadge(R.id.nav_shopping_list)
                        badge.isVisible = count > 0
                        badge.number = count
                    }
                }

                launch {
                    viewModel.basketComparison.collect { basket ->
                        renderBasketComparison(basket)
                    }
                }
            }
        }
    }

    private fun renderBcvRate(rate: BcvRate?) {
        if (rate == null) {
            binding.tvBcvHeader.setText(R.string.bcv_unavailable)
        } else {
            val formatted = numberFormat.format(rate.vesPerRef)
            binding.tvBcvHeader.text = getString(R.string.bcv_rate_format, formatted)
        }
    }

    private fun renderUiState(state: UiState) {
        when (state) {
            is UiState.Idle -> {
                binding.pbLoading.visibility = View.GONE
                binding.layoutSkeleton.visibility = View.GONE
                binding.rvComparisons.visibility = View.GONE
                binding.layoutEmptyResults.visibility = View.GONE
                binding.layoutIdleView.root.visibility = View.VISIBLE
                binding.tvResultsCount.visibility = View.GONE
                originalGroups = emptyList()
                comparisonAdapter.submitList(emptyList())
            }
            is UiState.Loading -> {
                binding.pbLoading.visibility = View.VISIBLE
                binding.layoutIdleView.root.visibility = View.GONE
                binding.layoutEmptyResults.visibility = View.GONE
                binding.rvComparisons.visibility = View.GONE
                binding.layoutSkeleton.visibility = View.VISIBLE
                binding.tvResultsCount.visibility = View.GONE
            }
            is UiState.Success -> {
                binding.pbLoading.visibility = View.GONE
                binding.layoutIdleView.root.visibility = View.GONE
                binding.layoutSkeleton.visibility = View.GONE
                binding.layoutEmptyResults.visibility = View.GONE
                binding.rvComparisons.visibility = View.VISIBLE
                originalGroups = state.groups
                applyFilterAndSort()
            }
            is UiState.Empty -> {
                binding.pbLoading.visibility = View.GONE
                binding.layoutIdleView.root.visibility = View.GONE
                binding.layoutSkeleton.visibility = View.GONE
                binding.rvComparisons.visibility = View.GONE
                binding.layoutEmptyResults.visibility = View.VISIBLE
                val query = binding.etSearch.text.toString().trim()
                binding.tvEmptySubtitle.text = if (query.isNotEmpty()) {
                    "No encontramos productos coincidentes con \"$query\". Intenta con otra marca o escanea su código de barras."
                } else {
                    "Intenta buscar por marca (ej: Yoka, Mary) o producto general (ej: Arroz, Harina)."
                }
                binding.tvResultsCount.visibility = View.GONE
                originalGroups = emptyList()
                comparisonAdapter.submitList(emptyList())
            }
            is UiState.Error -> {
                binding.pbLoading.visibility = View.GONE
                binding.layoutIdleView.root.visibility = View.GONE
                binding.layoutSkeleton.visibility = View.GONE
                binding.rvComparisons.visibility = View.GONE
                binding.layoutEmptyResults.visibility = View.VISIBLE
                binding.tvEmptyTitle.text = "Error al consultar precios"
                binding.tvEmptySubtitle.text = state.message
                binding.tvResultsCount.visibility = View.GONE
                originalGroups = emptyList()
                comparisonAdapter.submitList(emptyList())
            }
        }
    }

    private fun renderBasketComparison(basket: BasketComparison) {
        val summaryBinding = binding.basketSummaryView

        if (basket.isEmpty) {
            summaryBinding.basketSummaryRoot.visibility = View.GONE
            return
        }
        summaryBinding.basketSummaryRoot.visibility = View.VISIBLE

        summaryBinding.tvTotalItemsCount.text = getString(
            R.string.basket_items_available,
            basket.totalDistinctProducts,
            basket.totalUnitsCount
        )

        if (basket.winnerStore != null && basket.maxSavingsRef > 0.0) {
            val savingsRefStr = numberFormat.format(basket.maxSavingsRef)
            val savingsVesStr = numberFormat.format(basket.maxSavingsVes)
            summaryBinding.tvWinnerBanner.text = getString(
                R.string.basket_winner_format,
                basket.winnerStore.displayName,
                savingsRefStr,
                savingsVesStr
            )
            summaryBinding.winnerBannerContainer.visibility = View.VISIBLE
        } else if (basket.winnerStore != null) {
            summaryBinding.tvWinnerBanner.text = "🏆 ¡La mejor opción total es ${basket.winnerStore.displayName}!"
            summaryBinding.winnerBannerContainer.visibility = View.VISIBLE
        } else {
            summaryBinding.winnerBannerContainer.visibility = View.GONE
        }

        // Fila Gama
        summaryBinding.tvGamaWinnerBadge.isVisible = (basket.winnerStore == Store.GAMA)
        summaryBinding.tvGamaAvailability.text = "(${basket.gamaAvailableCount}/${basket.totalDistinctProducts})"
        if (basket.gamaAvailableCount > 0) {
            summaryBinding.tvGamaBasketTotalRef.text = getString(R.string.price_ref_format, basket.gamaTotalRef)
            if (basket.bcvRate != null) {
                summaryBinding.tvGamaBasketTotalVes.text = getString(R.string.price_ves_format, numberFormat.format(basket.gamaTotalVes))
                summaryBinding.tvGamaBasketTotalVes.visibility = View.VISIBLE
            } else {
                summaryBinding.tvGamaBasketTotalVes.visibility = View.GONE
            }
        } else {
            summaryBinding.tvGamaBasketTotalRef.setText(R.string.basket_not_available)
            summaryBinding.tvGamaBasketTotalVes.visibility = View.GONE
        }

        // Fila Forum
        summaryBinding.tvForumWinnerBadge.isVisible = (basket.winnerStore == Store.FORUM)
        summaryBinding.tvForumAvailability.text = "(${basket.forumAvailableCount}/${basket.totalDistinctProducts})"
        if (basket.forumAvailableCount > 0) {
            summaryBinding.tvForumBasketTotalRef.text = getString(R.string.price_ref_format, basket.forumTotalRef)
            if (basket.bcvRate != null) {
                summaryBinding.tvForumBasketTotalVes.text = getString(R.string.price_ves_format, numberFormat.format(basket.forumTotalVes))
                summaryBinding.tvForumBasketTotalVes.visibility = View.VISIBLE
            } else {
                summaryBinding.tvForumBasketTotalVes.visibility = View.GONE
            }
        } else {
            summaryBinding.tvForumBasketTotalRef.setText(R.string.basket_not_available)
            summaryBinding.tvForumBasketTotalVes.visibility = View.GONE
        }

        // Fila Central
        summaryBinding.tvCentralWinnerBadge.isVisible = (basket.winnerStore == Store.CENTRAL)
        summaryBinding.tvCentralAvailability.text = "(${basket.centralAvailableCount}/${basket.totalDistinctProducts})"
        if (basket.centralAvailableCount > 0) {
            summaryBinding.tvCentralBasketTotalRef.text = getString(R.string.price_ref_format, basket.centralTotalRef)
            if (basket.bcvRate != null) {
                summaryBinding.tvCentralBasketTotalVes.text = getString(R.string.price_ves_format, numberFormat.format(basket.centralTotalVes))
                summaryBinding.tvCentralBasketTotalVes.visibility = View.VISIBLE
            } else {
                summaryBinding.tvCentralBasketTotalVes.visibility = View.GONE
            }
        } else {
            summaryBinding.tvCentralBasketTotalRef.setText(R.string.basket_not_available)
            summaryBinding.tvCentralBasketTotalVes.visibility = View.GONE
        }

        // Canasta Mixta Súper Ahorro ("El Verdadero Resuelve")
        if (basket.optimalMixedSavingsRef > 0.05 && basket.totalDistinctProducts > 1) {
            summaryBinding.layoutOptimalMixedBanner.visibility = View.VISIBLE
            val savingsMixedStr = numberFormat.format(basket.optimalMixedSavingsRef)
            summaryBinding.tvOptimalMixedDescription.text = "Comprando cada producto donde es más barato ahorras Ref. $savingsMixedStr adicional"
            summaryBinding.tvOptimalMixedTotalRef.text = getString(R.string.price_ref_format, basket.optimalMixedTotalRef)
        } else {
            summaryBinding.layoutOptimalMixedBanner.visibility = View.GONE
        }

        // Progreso Modo Pasillo
        if (basket.totalDistinctProducts > 0) {
            summaryBinding.tvAisleProgress.visibility = View.VISIBLE
            summaryBinding.tvAisleProgress.text = "🛒 Modo Pasillo: ${basket.purchasedCount} de ${basket.totalDistinctProducts} artículos marcados en el carrito"
        } else {
            summaryBinding.tvAisleProgress.visibility = View.GONE
        }
    }
}
