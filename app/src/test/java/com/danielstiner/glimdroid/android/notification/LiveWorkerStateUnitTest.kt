package com.danielstiner.glimdroid.android.notification

import com.danielstiner.glimdroid.data.model.*
import com.danielstiner.glimdroid.notification.LiveNotification
import com.danielstiner.glimdroid.notification.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
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
    fun decode_older_format_throws() {
        assertThrows(kotlinx.serialization.SerializationException::class.java) {
            State.decodeFromString("""{"notifications":{"1":{"id":1,"channel":{"id":{"id":1},"title":"Title","matureContent":false,"language":null,"category":{"name":"Category"},"tags":[],"streamer":{"username":"username","displayName":"DisplayName","avatarUrl":null},"stream":null}}}}""")
        }
    }

    private val sampleChannel = Channel(
        id = ChannelId(1),
        title = "Title",
        matureContent = false,
        language = null,
        category = Category(name = "Category"),
        subcategory = Subcategory(name = "Subcategory"),
        tags = listOf(),
        streamer = Streamer(
            username = "username",
            displayName = "DisplayName",
            avatarUrl = null
        ),
        stream = null,
    )
}