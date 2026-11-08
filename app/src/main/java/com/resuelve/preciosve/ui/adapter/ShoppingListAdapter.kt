package com.resuelve.preciosve.ui.adapter

import android.content.Context
import android.graphics.Paint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.resuelve.preciosve.R
import com.resuelve.preciosve.databinding.ItemShoppingProductBinding
import com.resuelve.preciosve.domain.model.ShoppingItem
import java.text.NumberFormat
import java.util.Locale

class ShoppingListAdapter(
    private val onQuantityChange: (id: String, newQuantity: Int) -> Unit,
    private val onDeleteClick: (id: String) -> Unit,
    private val onTogglePurchased: (id: String, isPurchased: Boolean) -> Unit
) : ListAdapter<ShoppingItem, ShoppingListAdapter.ShoppingItemViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ShoppingItemViewHolder {
        val binding = ItemShoppingProductBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ShoppingItemViewHolder(binding, onQuantityChange, onDeleteClick, onTogglePurchased)
    }

    override fun onBindViewHolder(holder: ShoppingItemViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ShoppingItemViewHolder(
        private val binding: ItemShoppingProductBinding,
        private val onQuantityChange: (id: String, newQuantity: Int) -> Unit,
        private val onDeleteClick: (id: String) -> Unit,
        private val onTogglePurchased: (id: String, isPurchased: Boolean) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        private val context: Context = binding.root.context
        private val numberFormat: NumberFormat = NumberFormat.getNumberInstance(
            Locale.Builder().setLanguage("es").setRegion("VE").build()
        ).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }

        fun bind(item: ShoppingItem) {
            binding.tvShoppingProductTitle.text = item.displayName
            binding.tvQuantity.text = item.quantity.toString()

            // Modo Pasillo: Estado Comprado vs Pendiente
            if (item.isPurchased) {
                binding.tvShoppingProductTitle.paintFlags =
                    binding.tvShoppingProductTitle.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                itemView.alpha = 0.55f
                binding.ivCheckPurchased.setColorFilter(
                    ContextCompat.getColor(context, R.color.store_gama)
                )
            } else {
                binding.tvShoppingProductTitle.paintFlags =
                    binding.tvShoppingProductTitle.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                itemView.alpha = 1.0f
                binding.ivCheckPurchased.setColorFilter(
                    ContextCompat.getColor(context, R.color.text_dark_secondary)
                )
            }

            binding.btnTogglePurchased.setOnClickListener {
                onTogglePurchased(item.id, !item.isPurchased)
            }

            // Precios individuales por supermercado
            binding.tvGamaPrice.text = item.totalGamaRef?.let {
                context.getString(R.string.price_ref_format, it)
            } ?: context.getString(R.string.basket_not_available)

            binding.tvForumPrice.text = item.totalForumRef?.let {
                context.getString(R.string.price_ref_format, it)
            } ?: context.getString(R.string.basket_not_available)

            binding.tvCentralPrice.text = item.totalCentralRef?.let {
                context.getString(R.string.price_ref_format, it)
            } ?: context.getString(R.string.basket_not_available)

            // Imagen con Coil
            if (item.imageUrl.isNotBlank()) {
                binding.ivShoppingProduct.load(item.imageUrl) {
                    placeholder(R.drawable.ic_launcher)
                    error(R.drawable.ic_launcher)
                }
            } else {
                binding.ivShoppingProduct.setImageResource(R.drawable.ic_launcher)
            }

            // Acciones de cantidad
            binding.btnDecrement.setOnClickListener {
                onQuantityChange(item.id, item.quantity - 1)
            }

            binding.btnIncrement.setOnClickListener {
                onQuantityChange(item.id, item.quantity + 1)
            }

            binding.btnDelete.setOnClickListener {
                onDeleteClick(item.id)
            }
        }
    }

    companion object {
        object DiffCallback : DiffUtil.ItemCallback<ShoppingItem>() {
            override fun areItemsTheSame(oldItem: ShoppingItem, newItem: ShoppingItem): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: ShoppingItem, newItem: ShoppingItem): Boolean =
                oldItem == newItem
        }
    }
}
