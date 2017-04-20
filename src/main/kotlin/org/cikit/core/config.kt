package org.cikit.core

data class ListenConfig(
        val host: String = "127.0.0.1",
        val port: Int = 7000)

data class LogConfig(
        val file: String? = null)

data class Config(
        val listen: ListenConfig = ListenConfig(),
        val log: LogConfig = LogConfig())
