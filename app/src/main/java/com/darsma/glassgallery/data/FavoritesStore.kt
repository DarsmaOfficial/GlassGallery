package com.darsma.glassgallery.data

import android.content.Context
import java.util.HashSet

/**
 * Small, defensive preference store for favorites, sorting and grid density.
 *
 * SharedPreferences#getStringSet can expose a mutable backing set and throws if
 * an older/corrupt value has a different type. Every read therefore copies the
 * set inside runCatching, and every write uses a fresh HashSet. The lock keeps
 * rapid heart taps from interleaving preference operations.
 */
class FavoritesStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val lock = Any()

    fun loadFavorites(): Set<Long> = synchronized(lock) {
        runCatching {
            HashSet(prefs.getStringSet(KEY_FAVS, emptySet()) ?: emptySet())
                .asSequence()
                .mapNotNull(String::toLongOrNull)
                .filter { it > 0L }
                .toSet()
        }.getOrElse {
            // A bad value must never be allowed to crash the gallery. Remove
            // only the damaged key and continue with an empty favorite set.
            runCatching { prefs.edit().remove(KEY_FAVS).commit() }
            emptySet()
        }
    }

    /** Returns true only when the newest set was durably written. */
    fun saveFavorites(ids: Set<Long>): Boolean = synchronized(lock) {
        runCatching {
            val safeValues = ids.asSequence()
                .filter { it > 0L }
                .map(Long::toString)
                .toCollection(HashSet())
            prefs.edit().putStringSet(KEY_FAVS, safeValues).commit()
        }.getOrDefault(false)
    }

    fun loadSortOrder(): SortOrder = synchronized(lock) {
        runCatching { SortOrder.valueOf(prefs.getString(KEY_SORT, null) ?: "") }
            .getOrDefault(SortOrder.DEFAULT)
    }

    fun saveSortOrder(order: SortOrder): Boolean = synchronized(lock) {
        runCatching { prefs.edit().putString(KEY_SORT, order.name).commit() }
            .getOrDefault(false)
    }

    fun loadGridColumns(filterName: String, defaultColumns: Int): Int = synchronized(lock) {
        runCatching {
            prefs.getInt(gridKey(filterName), defaultColumns).coerceIn(MIN_COLUMNS, MAX_COLUMNS)
        }.getOrDefault(defaultColumns.coerceIn(MIN_COLUMNS, MAX_COLUMNS))
    }

    fun saveGridColumns(filterName: String, columns: Int): Boolean = synchronized(lock) {
        runCatching {
            prefs.edit()
                .putInt(gridKey(filterName), columns.coerceIn(MIN_COLUMNS, MAX_COLUMNS))
                .commit()
        }.getOrDefault(false)
    }

    private fun gridKey(filterName: String) = "grid_columns_${filterName.lowercase()}"

    private companion object {
        const val PREFS_NAME = "glass_gallery_prefs"
        const val KEY_FAVS = "favorite_ids"
        const val KEY_SORT = "sort_order"
        const val MIN_COLUMNS = 2
        const val MAX_COLUMNS = 5
    }
}
