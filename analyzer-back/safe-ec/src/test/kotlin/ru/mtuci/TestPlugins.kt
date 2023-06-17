package ru.mtuci

import ru.mtuci.plugins.IPlugins
import ru.mtuci.plugins.Plugin

class TestPlugins(override val list: List<Plugin<Any>>) : IPlugins {
    override suspend fun monitor() {}
    override fun stop() {}
}