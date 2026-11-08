package com.resuelve.preciosve.domain.model

/**
 * Supermercados soportados. El orden de declaración se usa como desempate
 * determinista cuando dos productos tienen exactamente el mismo precio.
 */
enum class Store(val displayName: String, val branchName: String) {
    GAMA(displayName = "Gama", branchName = "Santa Eduvigis"),
    FORUM(displayName = "Forum", branchName = "Plaza Venezuela"),
    CENTRAL(displayName = "Central Madeirense", branchName = "La Alameda")
}
