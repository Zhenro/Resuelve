package com.resuelve.preciosve.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.resuelve.preciosve.databinding.ItemSearchSuggestionBinding

class SuggestionAdapter(
    private val onSuggestionClick: (String) -> Unit
) : ListAdapter<String, SuggestionAdapter.SuggestionViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SuggestionViewHolder {
        val binding = ItemSearchSuggestionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return SuggestionViewHolder(binding, onSuggestionClick)
    }

    override fun onBindViewHolder(holder: SuggestionViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class SuggestionViewHolder(
        private val binding: ItemSearchSuggestionBinding,
        private val onSuggestionClick: (String) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(suggestion: String) {
            binding.tvSuggestionText.text = suggestion
            binding.root.setOnClickListener {
                onSuggestionClick(suggestion)
            }
        }
    }

    companion object {
        object DiffCallback : DiffUtil.ItemCallback<String>() {
            override fun areItemsTheSame(oldItem: String, newItem: String): Boolean = oldItem == newItem
            override fun areContentsTheSame(oldItem: String, newItem: String): Boolean = oldItem == newItem
        }
    }
}
