package org.alter.game.service.music

import net.rsprot.protocol.game.outgoing.sound.MidiJingle
import org.alter.game.model.Tile
import org.alter.game.model.entity.Player

/**
 * High-level audio primitives that the server can invoke to drive the
 * Alter client sound system.
 */
interface PacketSender {
    fun playSong(
        player: Player,
        trackId: Int,
        fadeOutDelay: Int = DEFAULT_SONG_FADE_DELAY,
        fadeOutSpeed: Int = DEFAULT_SONG_FADE_SPEED,
        fadeInDelay: Int = DEFAULT_SONG_FADE_DELAY,
        fadeInSpeed: Int = DEFAULT_SONG_FADE_SPEED,
    )

    fun stopSong(
        player: Player,
        fadeOutDelay: Int = DEFAULT_SONG_FADE_DELAY,
        fadeOutSpeed: Int = DEFAULT_SONG_FADE_SPEED,
    )

    fun playJingle(
        player: Player,
        jingleId: Int,
        delayTicks: Int = 0,
    ): MidiJingle

    fun playSound(
        player: Player,
        soundId: Int,
        radius: Int = DEFAULT_SOUND_VOLUME,
        delayTicks: Int = 0,
    )

    fun playAreaSound(
        tile: Tile,
        soundId: Int,
        radius: Int,
        volume: Int = DEFAULT_SOUND_VOLUME,
        delayTicks: Int = 0,
    )

    companion object {
        const val DEFAULT_SONG_FADE_DELAY = 0
        const val DEFAULT_SONG_FADE_SPEED = 6
        const val DEFAULT_SOUND_VOLUME = 1
    }
}
