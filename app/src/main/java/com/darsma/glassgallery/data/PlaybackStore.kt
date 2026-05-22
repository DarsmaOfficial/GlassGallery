package com.darsma.glassgallery.data

import android.content.Context

/**
 * Remembers the last playback position for each video so the user can pick up
 * exactly where they left off. Backed by SharedPreferences — free and instant.
 *
 * Positions are dropped once a video is essentially finished, so a completed
 * video starts fresh next time instead of resuming at the last second.
 */
class PlaybackStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("glass_gallery_playback", Context.MODE_PRIVATE)

    /** Saved position in ms, or 0 if none / finished. */
    fun getPosition(videoId: Long): Long =
        prefs.getLong(videoId.toString(), 0L)

    fun savePosition(videoId: Long, positionMs: Long, durationMs: Long) {
        val key = videoId.toString()
        // Near the start or near the end → not worth resuming.
        val tooEarly = positionMs < 5_000L
        val tooLate  = durationMs > 0L && positionMs > durationMs - 5_000L
        if (tooEarly || tooLate) {
            prefs.edit().remove(key).apply()
        } else {
            prefs.edit().putLong(key, positionMs).apply()
        }
    }

    fun clear(videoId: Long) {
        prefs.edit().remove(videoId.toString()).apply()
    }
}
