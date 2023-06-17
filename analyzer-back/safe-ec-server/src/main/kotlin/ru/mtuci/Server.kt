package ru.mtuci

import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import ru.mtuci.plugins.IPlugins
import ru.mtuci.plugins.Plugin
import ru.mtuci.plugins.Request
import ru.mtuci.plugins.Request.Named
import ru.mtuci.plugins.Request.OID
import ru.mtuci.plugins.Request.Params
import ru.mtuci.plugins.Request.Params.Supplementary
import ru.mtuci.plugins.Result
import ru.mtuci.plugins.Result.TechError
import ru.mtuci.plugins.Result.Undefined
import ru.mtuci.plugins.Result.Validated
import ru.mtuci.plugins.Result.Vulnerable
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.math.BigInteger
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.security.spec.*
import java.util.Base64
import java.util.HexFormat
import java.util.concurrent.atomic.AtomicBoolean

const val CLOSED = "CLOSED"
const val SUCCESS = "SUCCESS"
const val ERROR = "ERROR"
const val VULNERABLE = "VULNERABLE"

private val log = LoggerFactory.getLogger("Server")

@Serializable
class Command(val id: String, val type: String, val value: String) {
    fun toRequest(): Request = when (type) {
        "OID" -> OID(value)
        "Name" -> Named(value)
        "Params" -> parseParamsJson(value)
        else -> throw RuntimeException("Unknown request type '$type'")
    }
}

@Serializable
class ParamsRequest(val type: String, val fp: String?, val m: Int?, val ks: IntArray?, val b: String, val a: String,
                    val x: String, val y: String, val n: String, val h: Int, val seed: String?,
                    val ed: String?, val edFactors: List<String>?)

@Serializable
class Response(val reqId: String?, val type: String, val info: String? = null)

class Server(private val port: Int, private val plugins: IPlugins) {

    private val stopped = AtomicBoolean(true)
    private val socket = ServerSocket(port)

    suspend fun go() = withContext(Dispatchers.IO) {
        stopped.set(false)
        log.info("Server is listening on port $port")
        while (!stopped.get()) {
            val client = socket.accept()
            client.soTimeout = 10_000

            if (stopped.get()) {
                client.getOutputStream().writer().use { it.write(CLOSED) }
                return@withContext
            }

            launch(Dispatchers.IO) {
                try {
                    handleClient(client)
                }
                catch (e: Exception) {
                    log.error("Cannot handle client", e)
                }
                finally {
                    if (!client.isClosed)
                        client.close()
                    
                    log.info("Client {} finished", client)
                }
            }
        }
    }

    private fun handleClient(client: Socket) {
        val out = BufferedOutputStream(client.getOutputStream())
        val input = BufferedInputStream(client.getInputStream())
        log.debug("New client {}", client)
        while (!stopped.get() && !client.isClosed) {
            var command: Command? = null
            val result: Result = try {
                val received = input.readUntil("$")
                log.debug("Received: $received")
                if (received.isEmpty() || received == "CLOSE") {
                    log.info("Socket closed")
                    break
                }

                val decodedBytes = Base64.getDecoder().decode(received)
                val decoded = String(decodedBytes, StandardCharsets.UTF_8)
                log.info("Request: $decoded")
                command = Json.decodeFromString<Command>(decoded)
                val request = command.toRequest()
                val plugins = plugins.list.groupBy { p: Plugin -> p.priority() }.toSortedMap()
                plugins.keys.fold(Undefined() as Result) { acc, priority ->
                    when (acc) {
                        is Vulnerable, is TechError -> acc
                        is Undefined, is Validated -> plugins[priority]!!.fold(acc) { acc, plugin ->
                            when (acc) {
                                is Vulnerable, is TechError -> acc
                                is Undefined, is Validated -> request.accept(plugin)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                log.error("Got error while running plugins", e)
                TechError(e.message)
            }

            val response = when (result) {
                is Validated, is Undefined -> Response(command?.id, SUCCESS)
                is TechError -> Response(command?.id, ERROR, result.message)
                is Vulnerable -> Response(command?.id, VULNERABLE, result.message)
            }

            try {
                val serialized = Json.encodeToString(response)
                log.info("Response: $serialized")
                val encoded = Base64.getEncoder().encode(serialized.toByteArray(StandardCharsets.UTF_8))
                out.write(encoded)
                out.write("$".toByteArray(StandardCharsets.UTF_8))
                out.flush()
            } catch (e: Exception) {
                log.error("Cannot send response", e)
            }
        }
    }

    fun stop() {
        this.stopped.set(true)
        this.socket.close()
    }
}

// Ожидается форма Вейерштрасса
fun parseParamsJson(json: String): Params = try {
    val parsed = Json.decodeFromString<ParamsRequest>(json)

    val field: ECField = when (val type = parsed.type) {
        "prime" -> ECFieldFp(bi(parsed.fp!!))
        "binary" -> ECFieldF2m(parsed.m!!, parsed.ks)
        else -> throw RuntimeException("Unsupported curve type $type")
    }
    val seed = parsed.seed?.let { HexFormat.of().parseHex(it) }
    val ec = ECParameterSpec(
        EllipticCurve(field, bi(parsed.a), bi(parsed.b), seed),
        ECPoint(bi(parsed.x), bi(parsed.y)),
        bi(parsed.n),
        parsed.h
    )
    val supplementary = Supplementary(
        parsed.ed?.let { bi(it) },
        parsed.edFactors?.let { it.map { bi(it) } }
    )
    Params(ec, supplementary)
}
catch (e: Exception) {
    log.error("Got error while parsing json $json", e)
    throw e
}

private fun bi(hex: String) = BigInteger(hex, 16)

