package org.alter.game.saving.impl

import org.alter.game.model.entity.Client
import org.alter.game.saving.DocumentHandler
import org.bson.Document

class MusicSerialisation(override val name: String = "music") : DocumentHandler {
    override fun fromDocument(client: Client, doc: Document) {
        val state = client.music
        state.reset()
        val unlocked = doc.getList("unlocked", Number::class.java) ?: emptyList()
        unlocked.forEach { state.unlocked.set(it.toInt()) }
        state.autoPlayEnabled = doc.getBoolean("auto", true)
        state.loopEnabled = doc.getBoolean("loop", false)
        state.currentTrackId = doc.getInteger("current")
        state.manualTrackId = doc.getInteger("manual")
    }

    override fun asDocument(client: Client): Document {
        val state = client.music
        val unlocked = mutableListOf<Int>()
        var bit = state.unlocked.nextSetBit(0)
        while (bit >= 0) {
            unlocked += bit
            bit = state.unlocked.nextSetBit(bit + 1)
        }
        return Document().apply {
            append("auto", state.autoPlayEnabled)
            append("loop", state.loopEnabled)
            state.currentTrackId?.let { append("current", it) }
            state.manualTrackId?.let { append("manual", it) }
            append("unlocked", unlocked)
        }
    }

    fun applyDefaults(client: Client) {
        client.music.reset()
    }
}
