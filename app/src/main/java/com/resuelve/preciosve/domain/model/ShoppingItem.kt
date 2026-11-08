package com.resuelve.preciosve.domain.model

/**
 * Representa un producto en la lista de compras del usuario con sus precios
 * en cada uno de los 3 supermercados.
 */
data class ShoppingItem(
    val id: String,
    val displayName: String,
    val imageUrl: String,
    val quantity: Int,
    val gamaPriceRef: Double?,
    val forumPriceRef: Double?,
    val centralPriceRef: Double?,
    val gamaUrl: String,
    val forumUrl: String,
    val centralUrl: String,
    val addedAtMillis: Long,
    val isPurchased: Boolean = false
) {
    /** Precio total en Gama para la cantidad seleccionada (o null si no está disponible) */
    val totalGamaRef: Double?
        get() = gamaPriceRef?.let { it * quantity }

    /** Precio total en Forum para la cantidad seleccionada (o null si no está disponible) */
    val totalForumRef: Double?
        get() = forumPriceRef?.let { it * quantity }

    /** Precio total en Central para la cantidad seleccionada (o null si no está disponible) */
    val totalCentralRef: Double?
        get() = centralPriceRef?.let { it * quantity }

    /** Tienda más barata para este producto individual */
    val cheapestStore: Store?
        get() {
            val prices = listOfNotNull(
                gamaPriceRef?.let { Store.GAMA to it },
                forumPriceRef?.let { Store.FORUM to it },
                centralPriceRef?.let { Store.CENTRAL to it }
            )
            return prices.minByOrNull { it.second }?.first
        }
}
