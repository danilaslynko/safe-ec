package ru.mtuci

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.InetAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.Base64

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

        val input = BufferedInputStream(socket.getInputStream())
        val response = input.readUntil("$")
        if (response.isEmpty())
            return ""
        
        return String(Base64.getDecoder().decode(response), StandardCharsets.UTF_8)
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