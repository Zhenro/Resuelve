package com.resuelve.preciosve.data.remote.gama

import kotlinx.serialization.Serializable

@Serializable
data class GamaSearchResponseDto(
    val products: List<GamaProductDto>? = null
)

@Serializable
data class GamaProductDto(
    val code: String,
    val name: String? = null,
    val price: GamaPriceDto? = null,
    val totalWithVatPrice: GamaPriceDto? = null,
    val taxWithDiscount: GamaPriceDto? = null,
    val images: List<GamaImageDto>? = null,
    val seoName: String? = null
)

@Serializable
data class GamaPriceDto(
    val value: Double? = null,
    val formattedValue: String? = null,
    val currencyIso: String? = null
)

@Serializable
data class GamaImageDto(
    val format: String? = null,
    val imageType: String? = null,
    val url: String? = null
)

@Serializable
data class GamaCurrenciesResponseDto(
    val currencies: List<GamaCurrencyDto>? = null
)

@Serializable
data class GamaCurrencyDto(
    val isocode: String,
    val conversion: String? = null,
    val active: Boolean? = null,
    val symbol: String? = null
)
