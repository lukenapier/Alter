package org.alter.game.service.music

import gg.rsmod.util.ServerProperties
import net.rsprot.protocol.game.outgoing.sound.MidiJingle
import net.rsprot.protocol.game.outgoing.sound.MidiSongStop
import net.rsprot.protocol.game.outgoing.sound.MidiSongV2
import net.rsprot.protocol.game.outgoing.sound.SynthSound
import org.alter.game.Server
import org.alter.game.model.Tile
import org.alter.game.model.World
import org.alter.game.model.entity.AreaSound
import org.alter.game.model.entity.Player
import org.alter.game.service.Service

/**
 * Thin wrapper around the rsprot sound packets. This keeps the low-level
 * packet construction code in a single place so the higher level music
 * system can focus on gameplay rules.
 */
class AudioService : Service, PacketSender {
    private lateinit var world: World

    override fun init(
        server: Server,
        world: World,
        serviceProperties: ServerProperties,
    ) {
        this.world = world
    }

    override fun playSong(
        player: Player,
        trackId: Int,
        fadeOutDelay: Int,
        fadeOutSpeed: Int,
        fadeInDelay: Int,
        fadeInSpeed: Int,
    ) {
        player.write(MidiSongV2(trackId, fadeOutDelay, fadeOutSpeed, fadeInDelay, fadeInSpeed))
    }

    override fun stopSong(
        player: Player,
        fadeOutDelay: Int,
        fadeOutSpeed: Int,
    ) {
        player.write(MidiSongStop(fadeOutDelay, fadeOutSpeed))
    }

    override fun playJingle(
        player: Player,
        jingleId: Int,
        delayTicks: Int,
    ): MidiJingle {
        val message = if (delayTicks > 0) MidiJingle(jingleId, delayTicks) else MidiJingle(jingleId)
        player.write(message)
        return message
    }

    override fun playSound(
        player: Player,
        soundId: Int,
        radius: Int,
        delayTicks: Int,
    ) {
        val clampedRadius = radius.coerceAtLeast(0)
        player.write(SynthSound(soundId, clampedRadius, delayTicks))
    }

    override fun playAreaSound(
        tile: Tile,
        soundId: Int,
        radius: Int,
        volume: Int,
        delayTicks: Int,
    ) {
        world.spawn(AreaSound(tile, soundId, radius, volume, delayTicks))
    }
}
