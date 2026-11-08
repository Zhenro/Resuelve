package com.resuelve.preciosve.ui.adapter

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.resuelve.preciosve.R
import com.resuelve.preciosve.databinding.ItemComparisonGroupBinding
import com.resuelve.preciosve.databinding.ItemStorePriceRowBinding
import com.resuelve.preciosve.domain.model.ComparisonGroup
import com.resuelve.preciosve.domain.model.ProductItem
import com.resuelve.preciosve.domain.model.Store
import java.text.NumberFormat
import java.util.Locale

class ComparisonAdapter(
    private val onProductClick: (ComparisonGroup) -> Unit,
    private val onAddToListClick: (ComparisonGroup) -> Unit,
    private val onStoreItemClick: ((ComparisonGroup, ProductItem) -> Unit)? = null
) : ListAdapter<ComparisonGroup, ComparisonAdapter.GroupViewHolder>(DiffCallback) {

    private var shoppingListQuantities: Map<String, Int> = emptyMap()

    fun updateShoppingListQuantities(quantities: Map<String, Int>) {
        this.shoppingListQuantities = quantities
        notifyItemRangeChanged(0, itemCount, PAYLOAD_SHOPPING_STATUS)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupViewHolder {
        val binding = ItemComparisonGroupBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return GroupViewHolder(binding, onProductClick, onAddToListClick, onStoreItemClick)
    }

    override fun onBindViewHolder(holder: GroupViewHolder, position: Int) {
        val item = getItem(position)
        val quantityInList = shoppingListQuantities[item.key] ?: 0
        holder.bind(item, quantityInList)
    }

    override fun onBindViewHolder(holder: GroupViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(PAYLOAD_SHOPPING_STATUS)) {
            val item = getItem(position)
            val quantityInList = shoppingListQuantities[item.key] ?: 0
            holder.updateShoppingStatus(quantityInList)
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    class GroupViewHolder(
        private val binding: ItemComparisonGroupBinding,
        private val onProductClick: (ComparisonGroup) -> Unit,
        private val onAddToListClick: (ComparisonGroup) -> Unit,
        private val onStoreItemClick: ((ComparisonGroup, ProductItem) -> Unit)?
    ) : RecyclerView.ViewHolder(binding.root) {

        private val context: Context = binding.root.context
        private val numberFormat: NumberFormat = NumberFormat.getNumberInstance(
            Locale.Builder().setLanguage("es").setRegion("VE").build()
        ).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }

        fun bind(group: ComparisonGroup, quantityInList: Int) {
            binding.tvGroupTitle.text = group.displayName

            // Cargar imagen representativa en la tarjeta maestra
            val representativeImage = group.items.firstOrNull { it.imageUrl.isNotBlank() }?.imageUrl
            if (!representativeImage.isNullOrBlank()) {
                binding.ivGroupThumbnail.visibility = View.VISIBLE
                binding.ivGroupThumbnail.load(representativeImage) {
                    placeholder(R.drawable.ic_launcher)
                    error(R.drawable.ic_launcher)
                }
            } else {
                binding.ivGroupThumbnail.setImageResource(R.drawable.ic_launcher)
            }

            // Indicador de disponibilidad en comercios del Catálogo Maestro
            val storeCount = group.items.size
            val storeNoun = if (storeCount == 1) "comercio" else "comercios"
            binding.tvAvailabilityBadge.text = "Disponible en $storeCount $storeNoun"

            // Rango de Precios en el Catálogo Unificado
            val cheapestPrice = group.cheapest.totalPriceRef
            val mostExpensivePrice = group.mostExpensive.totalPriceRef
            if (storeCount > 1 && group.savingsRef > 0.001) {
                val minStr = String.format(Locale.ROOT, "%.2f", cheapestPrice)
                val maxStr = String.format(Locale.ROOT, "%.2f", mostExpensivePrice)
                binding.tvPriceRange.text = "Ref. $minStr - $maxStr"
            } else {
                val priceStr = String.format(Locale.ROOT, "%.2f", cheapestPrice)
                binding.tvPriceRange.text = "Ref. $priceStr"
            }

            // Precio por unidad de medida (kg, 100g, L)
            val normalized = com.resuelve.preciosve.domain.util.ProductEntityResolver.normalize(group.cheapest)
            val unitPriceFormatted = com.resuelve.preciosve.domain.util.ProductEntityResolver.calculateUnitPrice(cheapestPrice, normalized.weightGramsOrMl)
            if (unitPriceFormatted != null) {
                binding.tvUnitPrice.text = unitPriceFormatted
                binding.tvUnitPrice.visibility = View.VISIBLE
            } else {
                binding.tvUnitPrice.visibility = View.GONE
            }

            // Insignia informativa en la barra de acción inferior
            if (storeCount > 1 && group.savingsRef > 0.0) {
                val percent = ((mostExpensivePrice - cheapestPrice) / mostExpensivePrice * 100).toInt()
                val savingsStr = numberFormat.format(group.savingsRef)
                binding.tvSavingsBadge.text = "Ahorro máx: Ref. $savingsStr (-$percent%)"
            } else {
                val storeNames = group.items.joinToString(" • ") { it.store.displayName }
                binding.tvSavingsBadge.text = storeNames
            }

            // Renderizado de lista comparativa compacta de tiendas
            renderStoreRows(group)

            // Estado del botón AGREGAR A LISTA >
            updateShoppingStatus(quantityInList)

            binding.root.setOnClickListener {
                onProductClick(group)
            }

            binding.btnToggleShoppingList.setOnClickListener {
                onAddToListClick(group)
            }
        }

        fun updateShoppingStatus(quantityInList: Int) {
            if (quantityInList > 0) {
                binding.btnToggleShoppingList.setBackgroundResource(R.drawable.bg_btn_reserve_done)
                binding.tvAddToListText.text = "EN TU LISTA ($quantityInList)"
                binding.ivAddToListIcon.setImageResource(R.drawable.ic_check)
            } else {
                binding.btnToggleShoppingList.setBackgroundResource(R.drawable.bg_btn_reserve)
                binding.tvAddToListText.text = "AGREGAR A LISTA"
                binding.ivAddToListIcon.setImageResource(R.drawable.ic_arrow_forward)
            }
        }

        private fun renderStoreRows(group: ComparisonGroup) {
            val container = binding.storesContainer
            val inflater = LayoutInflater.from(context)

            val targetCount = group.items.size
            while (container.childCount < targetCount) {
                val rowBinding = ItemStorePriceRowBinding.inflate(inflater, container, false)
                container.addView(rowBinding.root)
            }
            while (container.childCount > targetCount) {
                container.removeViewAt(container.childCount - 1)
            }

            for (i in 0 until targetCount) {
                val childView = container.getChildAt(i)
                val rowBinding = ItemStorePriceRowBinding.bind(childView)
                val item = group.items[i]
                val isCheapest = (i == 0 && group.items.size > 1)

                bindStoreRow(rowBinding, group, item, isCheapest)
            }
        }

        private fun bindStoreRow(
            rowBinding: ItemStorePriceRowBinding,
            group: ComparisonGroup,
            item: ProductItem,
            isCheapest: Boolean
        ) {
            when (item.store) {
                Store.GAMA -> {
                    rowBinding.tvStoreBadge.text = item.store.displayName
                    rowBinding.tvStoreBadge.setBackgroundResource(R.drawable.bg_tag_gama)
                    rowBinding.tvStoreBadge.setTextColor(ContextCompat.getColor(context, R.color.store_gama))
                }
                Store.FORUM -> {
                    rowBinding.tvStoreBadge.text = item.store.displayName
                    rowBinding.tvStoreBadge.setBackgroundResource(R.drawable.bg_tag_forum)
                    rowBinding.tvStoreBadge.setTextColor(ContextCompat.getColor(context, R.color.store_forum))
                }
                Store.CENTRAL -> {
                    rowBinding.tvStoreBadge.text = item.store.displayName
                    rowBinding.tvStoreBadge.setBackgroundResource(R.drawable.bg_tag_central)
                    rowBinding.tvStoreBadge.setTextColor(ContextCompat.getColor(context, R.color.store_central))
                }
            }

            rowBinding.tvBranchName.text = item.store.branchName
            rowBinding.tvCheapestBadge.isVisible = isCheapest

            rowBinding.tvPriceRef.text = context.getString(R.string.price_ref_format, item.totalPriceRef)

            if (item.hasVes) {
                val vesFormatted = numberFormat.format(item.totalPriceVes)
                rowBinding.tvPriceVes.text = context.getString(R.string.price_ves_format, vesFormatted)
                rowBinding.tvPriceVes.visibility = View.VISIBLE
            } else {
                rowBinding.tvPriceVes.visibility = View.GONE
            }

            rowBinding.root.setOnClickListener {
                if (onStoreItemClick != null) {
                    onStoreItemClick.invoke(group, item)
                } else if (item.productUrl.isNotEmpty()) {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(item.productUrl))
                        context.startActivity(intent)
                    } catch (_: Exception) {}
                }
            }
        }
    }

    companion object {
        private const val PAYLOAD_SHOPPING_STATUS = "payload_shopping_status"

        object DiffCallback : DiffUtil.ItemCallback<ComparisonGroup>() {
            override fun areItemsTheSame(oldItem: ComparisonGroup, newItem: ComparisonGroup): Boolean =
                oldItem.key == newItem.key

            override fun areContentsTheSame(oldItem: ComparisonGroup, newItem: ComparisonGroup): Boolean =
                oldItem == newItem
        }
    }
}
