package org.cikit.core

import io.vertx.core.http.HttpClientResponse
import io.vertx.core.http.HttpHeaders
import io.vertx.ext.web.MIMEHeader
import io.vertx.ext.web.Route
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.impl.ParsableMIMEValue
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.experimental.cancel
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.withTimeout
import java.util.concurrent.TimeUnit

fun Route.coroutineHandler(timeout: Long? = 10000L, fn: suspend (RoutingContext) -> Unit): Route {
    return handler { ctx ->
        launch(ctx.vertx().dispatcher()) {
            ctx.request().connection().closeHandler {
                this.coroutineContext.cancel(RuntimeException("client disconnected"))
            }
            try {
                if (timeout == null) {
                    fn(ctx)
                } else {
                    withTimeout(timeout, TimeUnit.MILLISECONDS) {
                        fn(ctx)
                    }
                }
            } catch (e: Exception) {
                ctx.fail(e)
            }
        }
    }
}

val HttpClientResponse.contentType: MIMEHeader?
    get() = getHeader(HttpHeaders.CONTENT_TYPE)?.let(::ParsableMIMEValue)

fun MIMEHeader?.isMatchedBy(value: String): Boolean {
    return this?.isMatchedBy(ParsableMIMEValue(value)) ?: false
}
