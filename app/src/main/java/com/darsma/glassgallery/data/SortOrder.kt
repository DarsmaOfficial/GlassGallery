package com.darsma.glassgallery.data

/** All ways the gallery grid can be ordered. */
enum class SortOrder(val label: String) {
    DATE_NEWEST("Newest first"),
    DATE_OLDEST("Oldest first"),
    NAME_AZ("Name (A–Z)"),
    NAME_ZA("Name (Z–A)"),
    SIZE_LARGEST("Largest first"),
    SIZE_SMALLEST("Smallest first"),
    DURATION_LONGEST("Longest first"),
    DURATION_SHORTEST("Shortest first");

    companion object {
        val DEFAULT = DATE_NEWEST
    }
}

/** Returns a new list sorted by the given order. */
fun List<Video>.sortedBy(order: SortOrder): List<Video> = when (order) {
    SortOrder.DATE_NEWEST       -> sortedByDescending { it.dateAdded }
    SortOrder.DATE_OLDEST       -> sortedBy { it.dateAdded }
    SortOrder.NAME_AZ           -> sortedBy { it.title.lowercase() }
    SortOrder.NAME_ZA           -> sortedByDescending { it.title.lowercase() }
    SortOrder.SIZE_LARGEST      -> sortedByDescending { it.sizeBytes }
    SortOrder.SIZE_SMALLEST     -> sortedBy { it.sizeBytes }
    SortOrder.DURATION_LONGEST  -> sortedByDescending { it.duration }
    SortOrder.DURATION_SHORTEST -> sortedBy { it.duration }
}
