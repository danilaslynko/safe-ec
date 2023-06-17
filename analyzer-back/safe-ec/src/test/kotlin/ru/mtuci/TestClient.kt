package ru.mtuci

import java.net.InetAddress
import java.net.Socket

class TestClient(private val port: Int) {
    fun send(data: String): String {
        val socket = Socket(InetAddress.getLocalHost(), port)
        socket.soTimeout = 10_000
        socket.use {
            val writer = socket.getOutputStream().writer()
            val toWrite = "$data\n$"
            writer.write(toWrite)
            writer.flush()
            return socket.getInputStream().reader().use { it.readUntil("\n$") }
        }
    }
}