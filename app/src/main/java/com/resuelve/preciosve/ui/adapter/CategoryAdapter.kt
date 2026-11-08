package com.resuelve.preciosve.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.resuelve.preciosve.R
import com.resuelve.preciosve.databinding.ItemCategoryChipBinding
import com.resuelve.preciosve.domain.model.ProductCategory

class CategoryAdapter(
    private val categories: List<ProductCategory>,
    private val onCategoryClick: (ProductCategory) -> Unit
) : RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder>() {

    private var selectedCategoryId: String? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val binding = ItemCategoryChipBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CategoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        holder.bind(categories[position])
    }

    override fun getItemCount(): Int = categories.size

    fun setSelectedCategory(categoryId: String?) {
        val previousSelected = selectedCategoryId
        selectedCategoryId = categoryId

        categories.indexOfFirst { it.id == previousSelected }.takeIf { it >= 0 }?.let { notifyItemChanged(it) }
        categories.indexOfFirst { it.id == selectedCategoryId }.takeIf { it >= 0 }?.let { notifyItemChanged(it) }
    }

    inner class CategoryViewHolder(
        private val binding: ItemCategoryChipBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(category: ProductCategory) {
            val isSelected = category.id == selectedCategoryId

            binding.tvChipEmoji.text = category.emoji
            binding.tvChipName.text = category.name.uppercase()

            val context = binding.root.context
            if (isSelected) {
                binding.chipContainer.setBackgroundResource(R.drawable.bg_category_squircle_selected)
                binding.tvChipName.setTextColor(ContextCompat.getColor(context, R.color.white))
            } else {
                binding.chipContainer.setBackgroundResource(R.drawable.bg_category_squircle)
                binding.tvChipName.setTextColor(ContextCompat.getColor(context, R.color.text_light_secondary))
            }

            binding.root.setOnClickListener {
                if (selectedCategoryId == category.id) {
                    setSelectedCategory(null)
                } else {
                    setSelectedCategory(category.id)
                }
                onCategoryClick(category)
            }
        }
    }
}
