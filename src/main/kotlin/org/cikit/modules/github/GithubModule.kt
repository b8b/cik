package org.cikit.modules.github

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.module.kotlin.readValue
import org.cikit.core.CoreModule
import org.cikit.core.Module
import org.slf4j.LoggerFactory

class GithubModule : Module {

    override val name = "github"

    private val logger = LoggerFactory.getLogger(name)

    override suspend fun start(core: CoreModule, configParser: JsonParser) {
        val githubConfig = core.objectMapper.readValue<GithubConfig>(configParser)
        val router = core.subRouter("/modules/github")
        logger.info("installing GithubHandler on /modules/github")
        GithubHandler(core.vx, core.objectMapper, githubConfig).setupRoutes(router)
    }

}
