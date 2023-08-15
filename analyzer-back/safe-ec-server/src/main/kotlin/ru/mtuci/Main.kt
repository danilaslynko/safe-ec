package ru.mtuci

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import ru.mtuci.plugins.Plugins
import java.nio.file.Paths

fun main(args: Array<String>) {
    val log = LoggerFactory.getLogger("Server")

    val port = Integer.parseInt(args.elementAtOrElse(0) { "15555" })
    val pluginsDir = Paths.get(System.getProperty("user.dir")).resolve("plugins").toAbsolutePath()
    val plugins = Plugins(pluginsDir.toString())

    val server = Server(port, plugins)
    GlobalScope.launch {
        log.debug("Launching plugins monitor")
        plugins.monitor()
    }
    GlobalScope.launch {
        log.debug("Launching server")
        server.go()
    }
    log.info("Enter 'q' to exit")
    while (readln() != "q")
        log.debug("Unrecognized command, print 'q' to finish")

    plugins.stop()
    server.stop()
    log.info("Server stopped")
}