package org.cikit.modules.github

import com.fasterxml.jackson.databind.ObjectMapper
import io.vertx.core.Vertx
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.kotlin.coroutines.toChannel
import org.cikit.core.coroutineHandler
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import javax.crypto.Mac

class GithubHandler(private val vx: Vertx,
                    private val om: ObjectMapper,
                    private val config: GithubConfig) {

    companion object {
        private const val GH_EVENT_HEADER = "x-github-event"
        private const val GH_DELIVERY_HEADER = "x-github-delivery"
        private const val GH_HUB_SIGNATURE = "x-hub-signature"
        const val SIGNATURE_ALGORITHM = "HmacSHA1"
    }

    private val logger = LoggerFactory.getLogger(javaClass)
    private val or = om.readerFor(GithubPushHook::class.java)

    private fun handlePush(delivery: String, sig: String?, data: ByteArray) {
        val info = or.readValue<GithubPushHook>(data)
        logger.info("push event received: $info")
    }

    suspend fun handle(ctx: RoutingContext) {
        val mac = config.keySpec?.let { key ->
            Mac.getInstance(key.algorithm).also { mac -> mac.init(key) }
        }
        val readChannel = ctx.request().toChannel(vx)
        val data = ByteArrayOutputStream().use { out ->
            for (buffer in readChannel) {
                val bytes = buffer.bytes
                mac?.update(bytes)
                out.write(bytes)
            }
            out.toByteArray()
        }
        val sig = if (mac == null) null else {
            val sigBytes = mac.doFinal()
            val sig = "sha1=" + sigBytes.joinToString(separator = "") { b ->
                String.format("%02x", b.toInt() and 0xff)
            }
            if (ctx.request().getHeader(GH_HUB_SIGNATURE) != sig) {
                logger.info("signature mismatch: expected $sig")
                ctx.fail(401)
                return
            }
            sig
        }
        val delivery = ctx.request().getHeader(GH_DELIVERY_HEADER)
        val event = ctx.request().getHeader(GH_EVENT_HEADER)
        logger.info("delivery[$delivery]: received $event event")
        when (event) {
            "push" -> handlePush(delivery, sig, data)
        }
        ctx.response().end()
    }

    fun setupRoutes(router: Router) {
        val route = router.post("/hook")
                .consumes("application/json")
                .coroutineHandler { handle(it) }
        logger.info("listening for github hooks on $route")
    }

}
