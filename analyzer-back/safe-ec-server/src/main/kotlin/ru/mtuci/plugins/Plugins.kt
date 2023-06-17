package ru.mtuci.plugins

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import org.slf4j.LoggerFactory
import ru.mtuci.asWatchChannel
import java.io.File
import java.net.URLClassLoader
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchEvent
import java.util.Collections
import java.util.ServiceLoader
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

fun pluginString(plugin: Plugin): String =
"""
${plugin.javaClass}{
    name=${plugin.name()},
    description=${plugin.description()},
    priority=${plugin.priority()}
}
"""

class Plugins(path: String) : IPlugins {
    private val log = LoggerFactory.getLogger(javaClass);

    private val lock = ReentrantReadWriteLock()
    private val container = HashMap<Path, Pair<ClassLoader, Plugin>>()
    private val path: Path
    private val channel: Channel<WatchEvent<Path>>

    override val list: List<Plugin> get() = lock.read { Collections.unmodifiableList(container.values.map { it.second }) }

    init {
        val file = File(path)
        this.path = file.toPath().toAbsolutePath()
        file.list()?.filter { it.endsWith(".jar") }
            ?.forEach { lock.write { load(this.path.resolve(it)) } }
        this.channel = file.asWatchChannel()
    }

    override suspend fun monitor() {
        log.info("Plugins monitor started for directory $path")
        channel.consumeEach { e ->
            if (!e.context().toString().endsWith(".jar"))
                return@consumeEach

            lock.write {
                when(e.kind()) {
                    StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE -> {
                        if (!unload(e.context()))
                            return@consumeEach
                    }
                }
                when(e.kind()) {
                    StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_CREATE -> {
                        load(e.context())
                    }
                }
            }
        }
    }

    private fun load(jar: Path) {
        val cl = URLClassLoader(arrayOf(jar.toUri().toURL()))
        val plugins = ServiceLoader.load(Plugin::class.java, cl)
        plugins.findFirst().ifPresent {
            container[jar] = Pair(cl, it)
            log.info("Loaded plugin from jar ${jar.toAbsolutePath()} with classloader=$cl:\n${pluginString(it)}")
        }
    }

    private fun unload(jar: Path): Boolean {
        if (!container.containsKey(jar))
            return false

        val p = container.remove(jar)
        val res = p != null
        if (res)
            log.info("Unloaded plugin from ${jar.toAbsolutePath()}, plugin=${p!!.second}, classloader=${p.first}")
        return res
    }

    override fun stop() {
        this.channel.close()
        log.info("Stopped plugins monitor for directory $path")
    }
}
