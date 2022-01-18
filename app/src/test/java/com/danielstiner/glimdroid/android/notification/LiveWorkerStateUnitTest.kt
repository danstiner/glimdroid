package com.danielstiner.glimdroid.android.notification

import com.danielstiner.glimdroid.data.model.Category
import com.danielstiner.glimdroid.data.model.Channel
import com.danielstiner.glimdroid.data.model.ChannelId
import com.danielstiner.glimdroid.data.model.Streamer
import com.danielstiner.glimdroid.notification.LiveNotification
import com.danielstiner.glimdroid.notification.State
import org.junit.Assert.assertEquals
import org.junit.Test

class LiveWorkerStateUnitTest {
    @Test
    fun encode_then_decode_empty_state() {
        val expected = State(mutableMapOf())
        val actual = State.decodeFromString(expected.encodeToString())
        assertEquals(expected, actual)
    }

    @Test
    fun encode_then_decode_with_notification() {
        val channel = sampleChannel
        val expected = State(
            notifications = mutableMapOf(
                Pair(
                    channel.id.id.toString(),
                    LiveNotification(channel.id.id.toInt(), sampleChannel)
                ),
            )
        )
        val actual = State.decodeFromString(expected.encodeToString())
        println(expected.encodeToString())
        assertEquals(expected, actual)
    }

    @Test
    fun decode_current_format() {
        val channel = sampleChannel
        val expected = State(
            notifications = mutableMapOf(
                Pair(
                    channel.id.id.toString(),
                    LiveNotification(channel.id.id.toInt(), sampleChannel)
                ),
            )
        )
        val actual =
            State.decodeFromString("""{"notifications":{"1":{"id":1,"channel":{"id":{"id":1},"title":"Title","matureContent":false,"language":null,"category":{"name":"Category"},"tags":[],"streamer":{"username":"username","displayName":"DisplayName","avatarUrl":null},"stream":null}}}}""")
        assertEquals(expected, actual)
    }

    private val sampleChannel = Channel(
        id = ChannelId(1),
        title = "Title",
        matureContent = false,
        language = null,
        category = Category(name = "Category"),
        tags = listOf(),
        streamer = Streamer(
            username = "username",
            displayName = "DisplayName",
            avatarUrl = null
        ),
        stream = null,
    )
}