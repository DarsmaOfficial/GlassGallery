package com.darsma.glassgallery.data

import android.content.Context

/**
 * Lightweight persistence for favorited video IDs and the chosen sort order.
 * Backed by SharedPreferences — no extra dependency, survives app restarts.
 */
class FavoritesStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("glass_gallery_prefs", Context.MODE_PRIVATE)

    fun loadFavorites(): Set<Long> =
        prefs.getStringSet(KEY_FAVS, emptySet())
            ?.mapNotNull { it.toLongOrNull() }
            ?.toSet()
            ?: emptySet()

    fun saveFavorites(ids: Set<Long>) {
        prefs.edit()
            .putStringSet(KEY_FAVS, ids.map { it.toString() }.toSet())
            .apply()
    }

    fun loadSortOrder(): SortOrder =
        runCatching { SortOrder.valueOf(prefs.getString(KEY_SORT, null) ?: "") }
            .getOrDefault(SortOrder.DEFAULT)

    fun saveSortOrder(order: SortOrder) {
        prefs.edit().putString(KEY_SORT, order.name).apply()
    }

    private companion object {
        const val KEY_FAVS = "favorite_ids"
        const val KEY_SORT = "sort_order"
    }
}
