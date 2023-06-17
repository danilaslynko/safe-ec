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

const val PORT = 7999

class ProtocolTest {
    @Test
    fun testCheckCommand() {
        val data = "test data to check"
        val deserializedData = "deserialized data"
        val errorMessage = "Error message"

        `when`(testPlugin.check(any(Named::class.java))).thenAnswer {
            val passed = it.getArgument<String>(0)
            assertEquals(deserializedData, passed)
            return@thenAnswer Result.validated(errorMessage)
        }

        val response = client.send(Json.encodeToString(Command("Name", data)))
        val decoded = Json.decodeFromString<Response>(response)
        assertEquals(VULNERABLE, decoded.type)
        assertEquals(errorMessage, decoded.info)

        verify(testPlugin, times(1)).check(any(Named::class.java))
    }

    @Test
    fun testOther() {
        val response = client.send(Json.encodeToString(Command("anyOther", "data")))
        val decoded = Json.decodeFromString<Response>(response)

        assertEquals(ERROR, decoded.type)
        assertEquals("Unknown command 'anyOther'", decoded.info)

        verify(testPlugin, never()).check(any(Named::class.java))
        verify(testPlugin, never()).check(any(OID::class.java))
        verify(testPlugin, never()).check(any(Params::class.java))
    }

    @BeforeEach
    fun reset() {
        reset(testPlugin)
    }

    companion object {
        private val testPlugin = spy(TestPlugin())
        private val client  = TestClient(PORT)
        private val server = spy(Server(PORT, TestPlugins(listOf(testPlugin))))

        @JvmStatic
        @BeforeAll
        fun init() {
            GlobalScope.launch {
                server.go()
            }
        }

        @JvmStatic
        @AfterAll
        fun destroy() {
            server.stop()
        }
    }
}