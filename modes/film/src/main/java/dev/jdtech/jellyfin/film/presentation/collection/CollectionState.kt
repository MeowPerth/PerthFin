package dev.jdtech.jellyfin.film.presentation.collection

import dev.jdtech.jellyfin.models.CollectionSection

data class CollectionState(
    val sections: List<CollectionSection> = emptyList(),
    val columns: Int = 2,
    val isLoading: Boolean = false,
    val error: Exception? = null,
)
