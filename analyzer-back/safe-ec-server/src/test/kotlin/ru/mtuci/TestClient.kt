package ru.mtuci

import org.slf4j.LoggerFactory
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.InetAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.Base64

private val log = LoggerFactory.getLogger("ru.mtuci.TestClient");

class TestClient(private val port: Int) {
    private lateinit var socket: Socket

    fun send(data: String) {
        if (socket.isClosed)
            open()
        
        val out = BufferedOutputStream(socket.getOutputStream())
        val dataBytes = data.toByteArray(StandardCharsets.UTF_8)
        val encoded = if (!isBase64(data))
            Base64.getEncoder().encode(dataBytes)
        else
            dataBytes
        
        out.write(encoded)
        out.write("$".toByteArray(StandardCharsets.UTF_8))
        out.flush()
    }
    
    fun receive(): String {
        if (socket.isClosed)
            open()

        val input = socket.getInputStream()
        val response = input.readUntil("$")
        log.debug("Got response: {}", response)
        if (response.isEmpty())
            return ""

        val decoded = String(Base64.getDecoder().decode(response), StandardCharsets.UTF_8)
        log.debug("Decoded response: {}", decoded)
        return decoded
    }
    
    fun open() {
        socket = Socket(InetAddress.getLocalHost(), port)
        socket.soTimeout = 10_000
    }
    
    fun close() {
        socket.getOutputStream().write("CLOSE".toByteArray(StandardCharsets.UTF_8))
        socket.close()
    }
}