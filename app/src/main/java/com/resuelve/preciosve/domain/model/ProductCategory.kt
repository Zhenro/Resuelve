package com.resuelve.preciosve.domain.model

/**
 * Categorías populares de supermercados en Caracas para navegación rápida.
 */
data class ProductCategory(
    val id: String,
    val name: String,
    val emoji: String,
    val query: String
) {
    companion object {
        val PRESET_CATEGORIES = listOf(
            ProductCategory("harinas", "Harinas y Granos", "🌾", "harina"),
            ProductCategory("lacteos", "Lácteos y Quesos", "🧀", "queso"),
            ProductCategory("carnes", "Pollo y Carnes", "🥩", "pollo"),
            ProductCategory("pastas", "Pastas y Arroz", "🍝", "arroz"),
            ProductCategory("aceites", "Aceites y Enlatados", "🥫", "aceite"),
            ProductCategory("cafe", "Café y Azúcar", "☕", "cafe"),
            ProductCategory("snacks", "Galletas y Snacks", "🍪", "galletas"),
            ProductCategory("bebidas", "Bebidas y Jugos", "🥤", "jugo"),
            ProductCategory("limpieza", "Aseo y Limpieza", "🧼", "detergente")
        )
    }
}
