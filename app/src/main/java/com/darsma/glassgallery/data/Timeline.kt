package com.darsma.glassgallery.data

import java.util.Calendar

/** One dated section of the timeline grid. */
data class TimelineSection(val label: String, val items: List<Video>)

private val MONTHS = arrayOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)

/** "Today" / "Yesterday" / "This Week" / "March 2026" for a unix-seconds stamp. */
fun timelineLabel(dateAddedSec: Long, now: Calendar = Calendar.getInstance()): String {
    if (dateAddedSec <= 0L) return "Earlier"
    val then = Calendar.getInstance().apply { timeInMillis = dateAddedSec * 1000L }

    fun dayKey(c: Calendar) = c.get(Calendar.YEAR) * 1000 + c.get(Calendar.DAY_OF_YEAR)
    val today = dayKey(now)
    val that  = dayKey(then)
    if (that == today) return "Today"

    val yesterday = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
    if (that == dayKey(yesterday)) return "Yesterday"

    val weekAgo = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -6) }
    if (then.timeInMillis >= weekAgo.timeInMillis && then.timeInMillis <= now.timeInMillis) {
        return "This Week"
    }

    val month = MONTHS.getOrElse(then.get(Calendar.MONTH)) { "" }
    return "$month ${then.get(Calendar.YEAR)}"
}

/** Groups a date-sorted list into labelled sections, preserving item order. */
fun List<Video>.toTimeline(): List<TimelineSection> {
    if (isEmpty()) return emptyList()
    val now = Calendar.getInstance()
    val sections = mutableListOf<TimelineSection>()
    var currentLabel: String? = null
    var bucket = mutableListOf<Video>()
    for (item in this) {
        val label = timelineLabel(item.dateAdded, now)
        if (label != currentLabel) {
            if (bucket.isNotEmpty() && currentLabel != null) {
                sections += TimelineSection(currentLabel, bucket)
            }
            currentLabel = label
            bucket = mutableListOf()
        }
        bucket += item
    }
    if (bucket.isNotEmpty() && currentLabel != null) {
        sections += TimelineSection(currentLabel, bucket)
    }
    return sections
}
