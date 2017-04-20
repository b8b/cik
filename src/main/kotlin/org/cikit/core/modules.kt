package org.cikit.core

import com.fasterxml.jackson.core.JsonParser

interface Module {

    val name: String

    suspend fun start(core: CoreModule, configParser: JsonParser)

}
