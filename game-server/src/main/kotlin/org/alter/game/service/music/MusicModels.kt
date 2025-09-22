package org.alter.game.service.music

import java.util.BitSet

/** Represents a single entry in the music track table. */
data class MusicTrack(val id: Int, val name: String, val isJingle: Boolean = false)

/** Optional tighter bounds inside a 64x64 region that a rule applies to. */
data class MusicRegionBounds(val x: Int, val z: Int, val width: Int, val height: Int) {
    fun contains(localX: Int, localZ: Int): Boolean =
        localX in x until (x + width) && localZ in z until (z + height)

    val area: Int get() = width * height
}

/** Mapping between a region (and optional bounds) and a track. */
data class RegionMusicRule(
    val regionId: Int,
    val trackId: Int,
    val priority: Int = 0,
    val bounds: MusicRegionBounds? = null,
) {
    val area: Int get() = bounds?.area ?: Int.MAX_VALUE
}

/** Per-player state used by the music system. */
class PlayerMusicState {
    val unlocked: BitSet = BitSet()
    var autoPlayEnabled: Boolean = true
    var loopEnabled: Boolean = false
    var currentTrackId: Int? = null
    var pendingJingleId: Int? = null
    var resumeAtCycle: Int? = null
    var pendingSongId: Int? = null
    var manualTrackId: Int? = null

    fun isUnlocked(trackId: Int): Boolean = unlocked.get(trackId)

    fun reset() {
        unlocked.clear()
        autoPlayEnabled = true
        loopEnabled = false
        currentTrackId = null
        pendingJingleId = null
        resumeAtCycle = null
        pendingSongId = null
        manualTrackId = null
    }
}
