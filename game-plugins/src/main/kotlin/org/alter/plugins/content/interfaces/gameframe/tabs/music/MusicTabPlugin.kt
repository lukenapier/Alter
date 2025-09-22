package org.alter.plugins.content.interfaces.gameframe.tabs.music

import org.alter.api.*
import org.alter.api.dsl.*
import org.alter.api.ext.*
import org.alter.game.Server
import org.alter.game.model.World
import org.alter.game.model.entity.Player
import org.alter.game.plugin.KotlinPlugin
import org.alter.game.plugin.PluginRepository
import org.alter.game.service.music.MusicService

class MusicTabPlugin(
    repository: PluginRepository,
    world: World,
    server: Server,
) : KotlinPlugin(repository, world, server) {

    init {
        onLogin {
            player.syncMusicSettings()
        }

        onButton(MUSIC_INTERFACE_ID, AUTO_BUTTON_COMPONENT) {
            val service = musicService(player) ?: return@onButton
            if (!player.music.autoPlayEnabled) {
                service.setAutoPlay(player, true, notify = false)
                player.syncMusicSettings()
            }
        }

        onButton(MUSIC_INTERFACE_ID, MANUAL_BUTTON_COMPONENT) {
            val service = musicService(player) ?: return@onButton
            if (player.music.autoPlayEnabled) {
                service.setAutoPlay(player, false, notify = false)
                player.syncMusicSettings()
            }
        }

        onButton(MUSIC_INTERFACE_ID, LOOP_BUTTON_COMPONENT) {
            val service = musicService(player) ?: return@onButton
            val newValue = !player.music.loopEnabled
            service.setLoop(player, newValue, notify = false)
            player.syncMusicSettings()
        }

        val trackHandler: Player.() -> Unit = handler@{
            val service = musicService(this) ?: return@handler
            val option = getInteractingOption()
            if (option != PRIMARY_PLAY_OPTION) {
                return@handler
            }
            val trackId = getInteractingSlot()
            if (trackId < 0) {
                return@handler
            }
            service.onManualPlay(this, trackId)
            syncMusicSettings()
        }

        onButton(MUSIC_INTERFACE_ID, TRACK_LIST_COMPONENT) {
            player.trackHandler()
        }

        onButton(MUSIC_INTERFACE_ID, TRACK_LIST_ALT_COMPONENT) {
            player.trackHandler()
        }
    }

    private fun musicService(player: Player): MusicService? =
        player.world.getService(MusicService::class.java, searchSubclasses = true)

    companion object {
        private const val MUSIC_INTERFACE_ID = 239
        private const val TRACK_LIST_COMPONENT = 3
        private const val TRACK_LIST_ALT_COMPONENT = 6
        private const val AUTO_BUTTON_COMPONENT = 10
        private const val MANUAL_BUTTON_COMPONENT = 13
        private const val LOOP_BUTTON_COMPONENT = 16
        private const val PRIMARY_PLAY_OPTION = 1
    }
}
