package dev.gezgin.sample.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class SortOrder { RELEVANCE, PRICE_ASC, PRICE_DESC }
