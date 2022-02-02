package com.danielstiner.janus

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertIterableEquals


class JanusApiTest {

    @Test
    fun create_success() = simulateJanus { server ->
        val api = JanusApi(server.url("/janus"))

        val sessionId = api.createSession()

        assertEquals(SimulatedJanus.creatableSessionId, sessionId)
    }

    @Test
    fun long_poll_success() = simulateJanus { server ->
        val api = JanusApi(server.url("/janus"))

        val events = api.longPollSession(SimulatedJanus.validSessionId)

        assertIterableEquals(listOf(SessionEvent("keepalive")), events)
    }

    @Test
    fun long_poll_nosuchsession() = simulateJanus { server ->
        val api = JanusApi(server.url("/janus"))

        assertThrows(JanusApi.NoSuchSessionException::class.java) {
            api.longPollSession(SimulatedJanus.noSuchSessionId)
        }
    }

    @Test
    fun destroy_nosuchsession() = simulateJanus { server ->
        val api = JanusApi(server.url("/janus"))

        assertThrows(JanusApi.NoSuchSessionException::class.java) {
            api.destroy(SimulatedJanus.noSuchSessionId)
        }
    }

    private fun simulateJanus(block: (MockWebServer) -> Unit) {
        val server = MockWebServer()
        server.dispatcher = SimulatedJanus
        server.start()
        block(server)
        server.shutdown()
    }

    private object SimulatedJanus : Dispatcher() {
        val validSessionId = SessionId(1L)
        val noSuchSessionId = SessionId(42L)
        val creatableSessionId = SessionId(1234567890123456789L)

        override fun dispatch(request: RecordedRequest) = when {
            request.requestUrl!!.encodedPath == "/janus" && request.method == "POST" -> {
                val body = request.body.readByteString().string(Charsets.UTF_8)
                val json = Json.parseToJsonElement(body)
                val transactionId = json.jsonObject["transaction"]!!.jsonPrimitive.content
                MockResponse().setBody(
                    """
                        {
                           "janus": "success",
                           "transaction": "$transactionId",
                           "data": {
                              "id": ${creatableSessionId.id}
                           }
                        }
                    """.trimIndent()
                )
            }
            request.requestUrl!!.encodedPath == "/janus/${validSessionId.id}" && request.method == "GET" -> {
                MockResponse().setBody(
                    """
                        [
                            {
                              "janus": "keepalive"
                           }
                        ]
                    """.trimIndent()
                )
            }
            else -> MockResponse().setResponseCode(404)
        }
    }
}