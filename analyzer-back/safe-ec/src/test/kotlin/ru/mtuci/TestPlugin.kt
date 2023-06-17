package ru.mtuci

import ru.mtuci.plugins.Plugin
import ru.mtuci.plugins.Priority

class TestPlugin : Plugin {
    override fun priority() = Priority.NORMAL
    override fun name() = "Test plugin"
}