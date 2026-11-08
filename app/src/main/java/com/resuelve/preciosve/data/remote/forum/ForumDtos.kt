package com.resuelve.preciosve.data.remote.forum

import kotlinx.serialization.Serializable

@Serializable
data class ForumGoodsResponseDto(
    val data: List<ForumGoodDto>? = null,
    val total: Int? = null
)

@Serializable
data class ForumGoodDto(
    val id: Long,
    val name: String? = null,
    val details: String? = null,
    val price_e2: Long? = null,
    val picture_urls: List<String>? = null,
    val sku: String? = null,
    val unit: String? = null,
    val presentation: String? = null
)
