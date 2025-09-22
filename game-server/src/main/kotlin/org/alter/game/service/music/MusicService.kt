package org.alter.game.service.music

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import gg.rsmod.util.ServerProperties
import io.github.oshai.kotlinlogging.KotlinLogging
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import net.rsprot.protocol.game.outgoing.interfaces.IfSetText
import org.alter.game.Server
import org.alter.game.model.World
import org.alter.game.model.entity.Player
import org.alter.game.service.Service
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.math.ceil

class MusicService : Service {
    private val logger = KotlinLogging.logger {}
    private val mapper = jacksonObjectMapper()

    private lateinit var world: World
    private lateinit var audio: AudioService

    private val tracks = mutableMapOf<Int, MusicTrack>()
    private val tracksByName = mutableMapOf<String, MusicTrack>()
    private val regionRules = Int2ObjectOpenHashMap<MutableList<RegionMusicRule>>()

    override fun init(
        server: Server,
        world: World,
        serviceProperties: ServerProperties,
    ) {
        this.world = world
        audio =
            world.getService(AudioService::class.java, searchSubclasses = true)
                ?: throw IllegalStateException("AudioService must be configured before MusicService")

        val trackPath = resolvePath(serviceProperties.getOrDefault("tracks-path", DEFAULT_TRACK_PATH))
        val rulePath = resolvePath(serviceProperties.getOrDefault("region-rules-path", DEFAULT_RULE_PATH))

        loadTracks(trackPath)
        loadRegionRules(rulePath)
    }

    fun onLogin(player: Player) {
        val state = player.music
        if (!state.autoPlayEnabled && state.currentTrackId != null) {
            startSong(player, state.currentTrackId!!, force = true)
            return
        }
        handleAreaMusic(player, player.tile.regionId, isLogin = true)
    }

    fun onRegionChange(player: Player, fromRegion: Int, toRegion: Int) {
        if (fromRegion == toRegion) {
            return
        }
        handleAreaMusic(player, toRegion, isLogin = false)
    }

    fun onManualPlay(player: Player, trackId: Int) {
        val track = tracks[trackId]
        if (track == null) {
            player.writeMessage("Unknown track id $trackId.")
            return
        }
        if (track.isJingle) {
            player.writeMessage("${track.name} is a jingle and cannot be played as background music.")
            return
        }
        val state = player.music
        if (!state.isUnlocked(trackId)) {
            player.writeMessage("You have not unlocked ${track.name} yet.")
            return
        }
        state.autoPlayEnabled = false
        state.manualTrackId = trackId
        startSong(player, trackId)
    }

    fun setAutoPlay(player: Player, enabled: Boolean, notify: Boolean = true) {
        val state = player.music
        if (state.autoPlayEnabled == enabled) {
            if (notify) {
                player.writeMessage("Auto-play is already ${if (enabled) "enabled" else "disabled"}.")
            }
            return
        }
        state.autoPlayEnabled = enabled
        if (enabled) {
            state.manualTrackId = null
            if (notify) {
                player.writeMessage("Music auto-play enabled.")
            }
            handleAreaMusic(player, player.tile.regionId, isLogin = false)
        } else {
            if (notify) {
                player.writeMessage("Music auto-play disabled.")
            }
        }
    }

    fun setLoop(player: Player, enabled: Boolean, notify: Boolean = true) {
        val state = player.music
        if (state.loopEnabled == enabled) {
            if (notify) {
                player.writeMessage("Music loop mode is already ${if (enabled) "enabled" else "disabled"}.")
            }
            return
        }
        state.loopEnabled = enabled
        if (notify) {
            player.writeMessage("Music loop mode ${if (enabled) "enabled" else "disabled"}.")
        }
    }

    fun unlock(player: Player, trackId: Int, notify: Boolean) {
        val track = tracks[trackId] ?: return
        val state = player.music
        if (state.isUnlocked(trackId)) {
            return
        }
        state.unlocked.set(trackId)
        if (notify && !track.isJingle) {
            player.writeMessage("You have unlocked a new music track: ${track.name}.")
        }
    }

    fun unlockAll(player: Player) {
        val state = player.music
        tracks.values.forEach { track -> state.unlocked.set(track.id) }
        player.writeMessage("All music tracks unlocked.")
    }

    fun reset(player: Player) {
        player.music.reset()
        player.writeMessage("Music settings reset.")
        handleAreaMusic(player, player.tile.regionId, isLogin = false)
    }

    fun playJingle(player: Player, jingleId: Int, delayTicks: Int = 0) {
        queueJingle(player, jingleId, delayTicks)
    }

    fun debug(player: Player) {
        val state = player.music
        val region = player.tile.regionId
        val areaTrack = chooseTrackForRegion(player, region)
        player.writeMessage(
            buildString {
                append("Music debug -> region=").append(region)
                append(", current=").append(state.currentTrackId ?: "none")
                append(", pending=").append(state.pendingSongId ?: "none")
                append(", auto=").append(state.autoPlayEnabled)
                append(", loop=").append(state.loopEnabled)
                append(", areaChoice=").append(if (areaTrack != -1) areaTrack else "none")
            },
        )
    }

    fun getTrack(trackId: Int): MusicTrack? = tracks[trackId]

    fun findTrack(query: String): MusicTrack? = tracksByName[query.lowercase()]

    fun cyclePlayer(player: Player) {
        val state = player.music
        val resumeCycle = state.resumeAtCycle
        if (state.pendingJingleId != null && resumeCycle != null && world.currentCycle >= resumeCycle) {
            state.pendingJingleId = null
            state.resumeAtCycle = null
            val next = state.pendingSongId ?: state.currentTrackId
            state.pendingSongId = null
            if (next != null) {
                startSong(player, next, force = true)
            }
        }
    }

    fun onLevelUp(player: Player, skillId: Int, newLevel: Int) {
        queueJingle(player, LEVEL_UP_JINGLE_ID)
        val sfx = if (newLevel >= 99) LEVEL_99_SFX_ID else LEVEL_UP_SFX_ID
        audio.playSound(player, sfx, PacketSender.DEFAULT_SOUND_VOLUME, 0)
    }

    private fun handleAreaMusic(player: Player, regionId: Int, isLogin: Boolean) {
        val state = player.music
        if (!state.autoPlayEnabled) {
            return
        }
        val trackId = chooseTrackForRegion(player, regionId)
        if (trackId == -1) {
            return
        }
        unlock(player, trackId, notify = !isLogin)
        if (state.currentTrackId == trackId && state.pendingSongId == null) {
            return
        }
        startSong(player, trackId)
    }

    private fun chooseTrackForRegion(player: Player, regionId: Int): Int {
        val rules = regionRules[regionId] ?: return -1
        if (rules.isEmpty()) {
            return -1
        }
        val baseX = (regionId shr 8) shl 6
        val baseZ = (regionId and 0xff) shl 6
        val localX = player.tile.x - baseX
        val localZ = player.tile.z - baseZ

        val best =
            rules.filter { rule ->
                rule.bounds?.contains(localX, localZ) ?: true
            }.maxWithOrNull(ruleComparator)
        return best?.trackId ?: -1
    }

    private fun startSong(player: Player, trackId: Int, force: Boolean = false) {
        val track = tracks[trackId]
        if (track == null || track.isJingle) {
            return
        }
        val state = player.music
        if (!force && state.pendingJingleId != null) {
            state.pendingSongId = trackId
            state.currentTrackId = trackId
            updateTrackComponent(player, trackId)
            return
        }
        audio.playSong(
            player = player,
            trackId = trackId,
            fadeOutDelay = DEFAULT_FADE_OUT_DELAY,
            fadeOutSpeed = DEFAULT_FADE_OUT_SPEED,
            fadeInDelay = DEFAULT_FADE_IN_DELAY,
            fadeInSpeed = DEFAULT_FADE_IN_SPEED,
        )
        state.currentTrackId = trackId
        state.pendingSongId = null
        updateTrackComponent(player, trackId)
    }

    private fun updateTrackComponent(player: Player, trackId: Int) {
        val title = trackName(trackId)
        player.write(IfSetText(239, 6, title))
    }

    private fun queueJingle(player: Player, jingleId: Int, delayTicks: Int = 0) {
        val message = audio.playJingle(player, jingleId, delayTicks)
        val state = player.music
        state.pendingJingleId = jingleId
        state.pendingSongId = state.pendingSongId ?: state.currentTrackId
        val waitTicks = delayTicks + delayToTicks(message.lengthInMillis)
        state.resumeAtCycle = world.currentCycle + waitTicks
    }

    private fun delayToTicks(lengthMillis: Int): Int = ceil(lengthMillis / TICK_LENGTH_MILLIS).toInt().coerceAtLeast(1)

    private fun trackName(trackId: Int): String = tracks[trackId]?.name ?: "Track $trackId"

    private fun loadTracks(path: Path) {
        if (!Files.exists(path)) {
            logger.warn { "Music track registry not found at $path" }
            return
        }
        runCatching { mapper.readValue<List<MusicTrack>>(path.toFile()) }
            .onSuccess { list ->
                list.forEach { track ->
                    tracks[track.id] = track
                    tracksByName[track.name.lowercase()] = track
                }
                logger.info { "Loaded ${list.size} music tracks." }
            }
            .onFailure { ex -> logger.error(ex) { "Unable to load music tracks from $path" } }
    }

    private fun loadRegionRules(path: Path) {
        if (!Files.exists(path)) {
            logger.warn { "Region music rule table not found at $path" }
            return
        }
        runCatching { mapper.readValue<List<RegionMusicRule>>(path.toFile()) }
            .onSuccess { list ->
                list.forEach { rule ->
                    if (!tracks.containsKey(rule.trackId)) {
                        logger.warn { "Skipping region rule $rule because track ${rule.trackId} is unknown." }
                        return@forEach
                    }
                    val rules = regionRules.computeIfAbsent(rule.regionId) { mutableListOf() }
                    rules.add(rule)
                }
                regionRules.values.forEach { rules -> rules.sortWith(ruleComparator.reversed()) }
                logger.info { "Loaded ${list.size} region music rules." }
            }
            .onFailure { ex -> logger.error(ex) { "Unable to load region music rules from $path" } }
    }

    private fun resolvePath(value: Any): Path =
        when (value) {
            is String -> Paths.get(value)
            is Path -> value
            else -> Paths.get(value.toString())
        }

    companion object {
        private val ruleComparator =
            compareByDescending<RegionMusicRule> { it.priority }
                .thenBy { it.area }
                .thenBy { it.trackId }

        private const val DEFAULT_FADE_OUT_DELAY = 1
        private const val DEFAULT_FADE_OUT_SPEED = 8
        private const val DEFAULT_FADE_IN_DELAY = 0
        private const val DEFAULT_FADE_IN_SPEED = 8
        private const val TICK_LENGTH_MILLIS = 600.0

        private const val LEVEL_UP_JINGLE_ID = 2396
        private const val LEVEL_UP_SFX_ID = 2384
        private const val LEVEL_99_SFX_ID = 2379

        private const val DEFAULT_TRACK_PATH = "../data/music_tracks.json"
        private const val DEFAULT_RULE_PATH = "../data/region_music_rules.json"
    }
}
