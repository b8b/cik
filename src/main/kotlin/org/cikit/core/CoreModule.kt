package org.cikit.core

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.ObjectMapper
import io.vertx.core.Vertx
import io.vertx.core.http.HttpServer
import io.vertx.ext.web.Router
import io.vertx.kotlin.core.http.HttpServerOptions
import io.vertx.kotlin.coroutines.awaitResult
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.experimental.launch
import org.slf4j.LoggerFactory
import java.util.*

class CoreModule(val vx: Vertx,
                 val config: Config,
                 val objectMapper: ObjectMapper,
                 private val moduleConfig: Map<String, JsonParser>) {

    private val logger = LoggerFactory.getLogger("core")
    private val router: Router = Router.router(vx)

    fun subRouter(mountPoint: String): Router = Router.router(vx)
            .also { router.mountSubRouter(mountPoint, it) }

    fun start(argsMap: Map<String, String?>) {
        launch(vx.dispatcher()) {
            try {
                awaitResult<HttpServer> {
                    vx.createHttpServer(HttpServerOptions(idleTimeout = 10))
                            .requestHandler(router::accept)
                            .listen(config.listen.port, config.listen.host, it)
                }
                logger.info("started listener on port ${config.listen.host}:${config.listen.port}")

                val modules = ServiceLoader.load(Module::class.java).sortedBy { it.name }
                modules.forEach { module ->
                    val name = module.name
                    val configParser = moduleConfig[name]
                            ?: objectMapper.factory.createParser("{}")
                    logger.info("starting module $name")
                    module.start(this@CoreModule, configParser)
                }
            } catch (ex: Throwable) {
                logger.error("error on starting application: $ex", ex)
                ex.printStackTrace()
                vx.close {
                    System.exit(1)
                }
            }
        }
    }

}
