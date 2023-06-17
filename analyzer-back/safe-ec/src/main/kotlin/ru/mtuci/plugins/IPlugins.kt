package ru.mtuci.plugins

interface IPlugins {
    val list: List<Plugin>
    suspend fun monitor()
    fun stop()
}