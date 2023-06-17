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
import java.io.OutputStreamWriter
import java.math.BigInteger
import java.net.ServerSocket
import java.net.Socket
import java.security.spec.*
import java.util.HexFormat
import java.util.concurrent.atomic.AtomicBoolean

const val CLOSED = "CLOSED"
const val SUCCESS = "SUCCESS"
const val ERROR = "ERROR"
const val VULNERABLE = "VULNERABLE"

private val log = LoggerFactory.getLogger("Server")

@Serializable
class Command(val type: String, val value: String) {
    fun toRequest(): Request = when (type) {
        "OID" -> OID(value)
        "Name" -> Named(value)
        "Params" -> parseParamsJson(value)
        else -> throw RuntimeException("Unknown request type $type")
    }
}

@Serializable
class ParamsRequest(val type: String, val fp: String?, val m: Int?, val ks: IntArray?, val b: String, val a: String,
                    val x: String, val y: String, val n: String, val h: Int, val seed: String?,
                    val ed: String?, val edFactors: List<String>?)

@Serializable
class Response(val type: String, val info: String? = null)

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
                handleClient(client)
            }
        }
    }

    private fun handleClient(client: Socket) {
        val writer = client.getOutputStream().writer()
        log.debug("New client $client")
        client.getInputStream().reader().use {
            while (!stopped.get() && !client.isClosed) {
                val received = it.readUntil("\n$")
                if (received.isEmpty())
                    continue

                log.info("Received: $received")
                val request = Json.decodeFromString<Command>(received).toRequest()
                val result: Result = try {
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
                    is Validated, is Undefined -> Response(SUCCESS)
                    is TechError -> Response(ERROR, result.message)
                    is Vulnerable -> Response(VULNERABLE, result.message)
                }

                writer.write(Json.encodeToString(response))
                writer.write("\n$\n")
                writer.flush()
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

