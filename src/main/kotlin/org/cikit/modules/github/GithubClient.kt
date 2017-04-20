package org.cikit.modules.github

import io.vertx.core.Handler
import io.vertx.core.Vertx
import io.vertx.core.http.HttpClientOptions
import io.vertx.core.http.HttpClientResponse
import io.vertx.core.http.HttpHeaders
import io.vertx.kotlin.core.json.Json
import io.vertx.kotlin.core.json.obj
import io.vertx.kotlin.coroutines.awaitEvent
import io.vertx.kotlin.coroutines.dispatcher
import io.vertx.kotlin.coroutines.toChannel
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.cancel
import kotlinx.coroutines.experimental.channels.produce
import org.cikit.core.contentType
import org.cikit.core.decodeJson
import org.cikit.core.isMatchedBy
import java.util.*

class GithubClient(private val vx: Vertx,
                   private val httpClientOptions: HttpClientOptions = io.vertx.kotlin.core.http.HttpClientOptions(),
                   private val uri: String = "https://api.github.com",
                   username: String, password: String) {

    private val auth = Base64.getUrlEncoder().encodeToString("$username:$password".toByteArray())

    fun listRepositories() = produce<GithubRepository>(vx.dispatcher()) {
        val resp = awaitEvent<HttpClientResponse> { handler ->
            vx.createHttpClient(httpClientOptions)
                    .get("$uri/user/repos")
                    .putHeader(HttpHeaders.AUTHORIZATION, "Basic $auth")
                    .handler(handler)
                    .exceptionHandler { ex ->
                        close(ex)
                    }
                    .end()
        }
        val readChannel = resp.toChannel(vx)
        try {
            when {
                resp.statusCode() == 200 -> {
                    decodeJson(readChannel, channel)
                    close()
                }
                resp.contentType.isMatchedBy("application/json") -> {
                    val error = decodeJson<GithubError>(readChannel)
                    throw GithubException(resp.statusCode(), error)
                }
                else -> throw IllegalStateException()
            }
        } finally {
            readChannel.cancel()
        }
    }

    fun listComments(owner: String, repo: String, commit: String? = null) = produce<GithubComment>(vx.dispatcher()) {
        val resp = awaitEvent<HttpClientResponse> { handler ->
            vx.createHttpClient(httpClientOptions)
                    .get("$uri/repos/$owner/$repo/${if (commit == null) "" else "commits/$commit"}/comments")
                    .putHeader(HttpHeaders.AUTHORIZATION, "Basic $auth")
                    .handler(handler)
                    .exceptionHandler { ex ->
                        close(ex)
                    }
                    .end()
        }
        val readChannel = resp.toChannel(vx)
        try {
            when {
                resp.statusCode() == 200 -> {
                    decodeJson(readChannel, channel)
                    close()
                }
                resp.contentType.isMatchedBy("application/json") -> {
                    val error = decodeJson<GithubError>(readChannel)
                    throw GithubException(resp.statusCode(), error)
                }
                else -> throw IllegalStateException()
            }
        } finally {
            readChannel.cancel()
        }
    }

    fun postComment(owner: String, repo: String, commit: String, body: String,
                    path: String? = null, position: Int? = 0) = async(vx.dispatcher()) {
        val reqBody = listOf(
                "body" to body,
                "path" to path,
                "position" to position)
                .filterNot { it.second == null }
                .let { Json.obj(it).toBuffer() }
        val resp = awaitEvent<HttpClientResponse> { handler ->
            vx.createHttpClient(httpClientOptions)
                    .post("$uri/repos/$owner/$repo/commits/$commit/comments")
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .putHeader(HttpHeaders.AUTHORIZATION, "Basic $auth")
                    .handler(handler)
                    .exceptionHandler { ex ->
                        coroutineContext.cancel(ex)
                    }
                    .end(reqBody)
        }
        val readChannel = resp.toChannel(vx)
        try {
            when {
                resp.statusCode() == 201 -> return@async decodeJson<GithubComment>(readChannel)
                resp.contentType.isMatchedBy("application/json") -> {
                    val error = decodeJson<GithubError>(readChannel)
                    throw GithubException(resp.statusCode(), error)
                }
                else -> throw IllegalStateException()
            }
        } finally {
            readChannel.cancel()
        }
    }

    fun readFile(owner: String, repo: String, path: String) = async(vx.dispatcher()) {
        val resp = awaitEvent<HttpClientResponse> { handler ->
            vx.createHttpClient(httpClientOptions)
                    .get("$uri/repos/$owner/$repo/contents/$path")
                    .putHeader(HttpHeaders.AUTHORIZATION, "Basic $auth")
                    .handler(handler)
                    .exceptionHandler { ex ->
                        coroutineContext.cancel(ex)
                    }
                    .end()
        }
        val readChannel = resp.toChannel(vx)
        try {
            when {
                resp.statusCode() == 200 -> return@async decodeJson<GithubContents>(readChannel)
                resp.contentType.isMatchedBy("application/json") -> {
                    val error = decodeJson<GithubError>(readChannel)
                    throw GithubException(resp.statusCode(), error)
                }
                else -> throw IllegalStateException()
            }
        } finally {
            readChannel.cancel()
        }
    }

}

