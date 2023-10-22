package ru.mtuci

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import ru.mtuci.plugins.Request.Named
import ru.mtuci.plugins.Request.OID
import ru.mtuci.plugins.Request.Params
import ru.mtuci.plugins.Result

const val PORT = 8000

class ProtocolTest {
    @Test
    fun testCheckCommand() {
        val data = "test data to check"
        val errorMessage = "Error message"

        `when`(testPlugin.check(any(Named::class.java))).thenAnswer {
            val passed = it.getArgument<Named>(0)
            assertEquals(data, passed.name)
            return@thenAnswer Result.validated("TEST", errorMessage)
        }

        client.send(Json.encodeToString(Command("1", "Name", data)))
        val response = client.receive()
        val decoded = Json.decodeFromString<Response>(response)
        assertEquals(VULNERABLE, decoded.type)
        assertEquals(errorMessage, decoded.info)

        verify(testPlugin, times(1)).check(any(Named::class.java))
    }

    @Test
    fun testOther() {
        client.send(Json.encodeToString(Command("1", "anyOther", "data")))
        val response = client.receive()
        val decoded = Json.decodeFromString<Response>(response)

        assertEquals(ERROR, decoded.type)
        assertEquals("Unknown request type 'anyOther'", decoded.info)

        verify(testPlugin, never()).check(any(Named::class.java))
        verify(testPlugin, never()).check(any(OID::class.java))
        verify(testPlugin, never()).check(any(Params::class.java))
    }
    
    @Test
    fun testMultiple() {
        `when`(testPlugin.check(any(Named::class.java))).thenAnswer { Result.Validated() }
        
        client.send(Json.encodeToString(Command("1", "Name", "data")))
        client.send(Json.encodeToString(Command("2", "Name", "data")))
        client.send(Json.encodeToString(Command("3", "Name", "data")))

        var response = client.receive()
        var decoded = Json.decodeFromString<Response>(response)
        assertEquals("1", decoded.reqId)

        response = client.receive()
        decoded = Json.decodeFromString<Response>(response)
        assertEquals("2", decoded.reqId)

        response = client.receive()
        decoded = Json.decodeFromString<Response>(response)
        assertEquals("3", decoded.reqId)
    }

    @BeforeEach
    fun reset() {
        reset(testPlugin)
    }

    companion object {
        private val testPlugin = spy(TestPlugin())
        private val client = TestClient(PORT)
        private val server = spy(Server(PORT, TestPlugins(listOf(testPlugin))))

        @JvmStatic
        @BeforeAll
        fun init() {
            GlobalScope.launch {
                server.go()
            }
            client.open()
        }

        @JvmStatic
        @AfterAll
        fun destroy() {
            client.close()
            server.stop()
        }
    }
}