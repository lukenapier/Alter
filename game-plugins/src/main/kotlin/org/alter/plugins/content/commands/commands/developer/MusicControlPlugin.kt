package org.alter.plugins.content.commands.commands.developer

import org.alter.api.ext.getCommandArgs
import org.alter.api.ext.message
import org.alter.api.ext.player
import org.alter.api.ext.syncMusicSettings
import org.alter.game.Server
import org.alter.game.model.World
import org.alter.game.model.priv.Privilege
import org.alter.game.plugin.KotlinPlugin
import org.alter.game.plugin.PluginRepository
import org.alter.game.service.music.MusicService

class MusicControlPlugin(
    repository: PluginRepository,
    world: World,
    server: Server,
) : KotlinPlugin(repository, world, server) {

    init {
        onCommand("music", Privilege.DEV_POWER, description = "Control music playback") {
            val args = player.getCommandArgs()
            val service = player.world.getService(MusicService::class.java, searchSubclasses = true)
            if (service == null) {
                player.message("Music service is unavailable.")
                return@onCommand
            }

            if (args.isEmpty()) {
                player.message("Usage: ::music <auto|loop|play|unlock|reset|debug> ...")
                return@onCommand
            }

            when (args[0].lowercase()) {
                "auto" -> {
                    if (args.size < 2) {
                        player.message("Usage: ::music auto <on|off>")
                        return@onCommand
                    }
                    when (args[1].lowercase()) {
                        "on", "true", "1" -> {
                            service.setAutoPlay(player, true)
                            player.syncMusicSettings()
                        }
                        "off", "false", "0" -> {
                            service.setAutoPlay(player, false)
                            player.syncMusicSettings()
                        }
                        else -> player.message("Usage: ::music auto <on|off>")
                    }
                }

                "loop" -> {
                    if (args.size < 2) {
                        player.message("Usage: ::music loop <on|off>")
                        return@onCommand
                    }
                    when (args[1].lowercase()) {
                        "on", "true", "1" -> {
                            service.setLoop(player, true)
                            player.syncMusicSettings()
                        }
                        "off", "false", "0" -> {
                            service.setLoop(player, false)
                            player.syncMusicSettings()
                        }
                        else -> player.message("Usage: ::music loop <on|off>")
                    }
                }

                "play" -> {
                    if (args.size < 2) {
                        player.message("Usage: ::music play <track id|name>")
                        return@onCommand
                    }
                    val query = args.copyOfRange(1, args.size).joinToString(" ")
                    val track = query.toIntOrNull()?.let { service.getTrack(it) } ?: service.findTrack(query)
                    if (track == null) {
                        player.message("Unknown track: $query")
                        return@onCommand
                    }
                    service.onManualPlay(player, track.id)
                    player.syncMusicSettings()
                }

                "unlock" -> {
                    if (args.size >= 2 && args[1].equals("all", ignoreCase = true)) {
                        service.unlockAll(player)
                    } else {
                        player.message("Usage: ::music unlock all")
                    }
                }

                "reset" -> {
                    service.reset(player)
                    player.syncMusicSettings()
                }

                "debug" -> service.debug(player)

                else -> player.message("Usage: ::music <auto|loop|play|unlock|reset|debug> ...")
            }
        }
    }
}
