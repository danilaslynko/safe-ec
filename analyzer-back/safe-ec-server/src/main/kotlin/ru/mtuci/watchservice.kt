package ru.mtuci

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import java.io.File
import java.nio.file.*
import java.nio.file.WatchKey
import java.nio.file.StandardWatchEventKinds.*

fun File.asWatchChannel() = WatchChannel(file = this)

class WatchChannel(
    val file: File,
    private val channel: Channel<WatchEvent<Path>> = Channel()
) : Channel<WatchEvent<Path>> by channel {

    private val watchService: WatchService = FileSystems.getDefault().newWatchService()
    private val registeredKeys = ArrayList<WatchKey>()
    private val path: Path = if (file.isFile) {
        file.parentFile
    } else {
        file
    }.toPath()

    private fun registerPaths() {
        registeredKeys.apply {
            forEach { it.cancel() }
            clear()
        }
        registeredKeys += path.register(watchService, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE)
    }

    init {
        GlobalScope.launch(Dispatchers.IO) {
            var shouldRegisterPath = true

            while (!isClosedForSend) {
                if (shouldRegisterPath) {
                    registerPaths()
                    shouldRegisterPath = false
                }

                val monitorKey = watchService.take()
                val dirPath = monitorKey.watchable() as? Path ?: break
                monitorKey.pollEvents().forEach {
                    val eventPath = dirPath.resolve(it.context() as Path)

                    if (eventPath.toFile().absolutePath != file.absolutePath) {
                        return@forEach
                    }

                    channel.send(it as WatchEvent<Path>)
                }

                if (!monitorKey.reset()) {
                    monitorKey.cancel()
                    close()
                    break
                }
                else if (isClosedForSend) {
                    break
                }
            }
        }
    }

    override fun close(cause: Throwable?): Boolean {
        registeredKeys.apply {
            forEach { it.cancel() }
            clear()
        }

        return channel.close(cause)
    }
}
