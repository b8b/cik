import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpServer
import io.vertx.ext.web.Router
import io.vertx.kotlin.core.VertxOptions
import io.vertx.kotlin.core.http.HttpClientOptions
import io.vertx.kotlin.core.http.HttpServerOptions
import io.vertx.kotlin.coroutines.awaitResult
import kotlinx.coroutines.experimental.runBlocking
import org.cikit.core.LogConfig
import org.cikit.core.configureLogging
import org.cikit.core.coroutineHandler
import org.cikit.modules.github.GithubClient
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.BeforeClass
import org.junit.Test

class GithubClientTest {

    companion object {
        lateinit var vx: Vertx
        lateinit var server: HttpServer
        lateinit var router: Router
        lateinit var githubClient: GithubClient

        @BeforeClass
        @JvmStatic
        fun setupClass(): Unit = runBlocking {
            configureLogging(LogConfig())
            vx = Vertx.vertx(VertxOptions(blockedThreadCheckInterval = 60_000))
            router = Router.router(vx)
            server = awaitResult {
                vx.createHttpServer(HttpServerOptions(idleTimeout = 10))
                        .requestHandler(router::accept)
                        .listen(0, it)
            }
            println("http server started on port ${server.actualPort()}")
            githubClient = GithubClient(vx, HttpClientOptions(
                    defaultHost = "localhost", defaultPort = server.actualPort(), ssl = false),
                    "http://localhost:${server.actualPort()}", "dummy", "xxx")
        }

        @AfterClass
        @JvmStatic
        fun tearDownClass(): Unit = runBlocking {
            awaitResult<Void> { handler ->
                vx.close(handler)
            }
            Unit
        }

    }

    @After
    fun tearDown() {
        router.clear()
    }

    @Test
    fun testListRepositories() = runBlocking {
        var counter = 0
        router.get("/user/repos").coroutineHandler { ctx ->
            counter++
            ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .end(javaClass.getResourceAsStream("/github/listRepositories.json").use {
                        Buffer.buffer(it.readBytes())
                    })
        }
        val repoList = githubClient.listRepositories()
        val repo1 = repoList.receive()
        assertEquals("88881111", repo1.id)
        assertNull(repoList.receiveOrNull())
        assertEquals(1, counter)
    }

    @Test
    fun testListComments() = runBlocking {
        var counter = 0
        router.get("/repos/b8b/cikit/comments").coroutineHandler { ctx ->
            counter++
            ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .end(javaClass.getResourceAsStream("/github/listComments.json").use {
                        Buffer.buffer(it.readBytes())
                    })
        }
        val commentList = githubClient.listComments("b8b", "cikit")
        val comment1 = commentList.receive()
        assertEquals("28635059", comment1.id)
        val comment2 = commentList.receive()
        assertEquals("28635096", comment2.id)
        assertNull(commentList.receiveOrNull())
        assertEquals(1, counter)
    }

    @Test
    fun testPostComment() = runBlocking {
        var counter = 0
        router.post("/repos/b8b/cikit/commits/mock/comments").coroutineHandler { ctx ->
            counter++
            ctx.response()
                    .setStatusCode(201)
                    .putHeader("Content-Type", "application/json")
                    .end(javaClass.getResourceAsStream("/github/listComments.json").use {
                        val om = jacksonObjectMapper()
                        val comment1 = om.readValue<List<Map<String, Any>>>(it).first()
                        om.writeValueAsString(comment1)
                    })
        }
        val comment1 = githubClient.postComment("b8b", "cikit",
                "mock", "hello test").await()
        assertEquals("28635059", comment1.id)
        assertEquals(1, counter)
    }


    @Test
    fun testReadFile() = runBlocking {
        var counter = 0
        router.get("/repos/b8b/cikit/contents/README.md").coroutineHandler { ctx ->
            counter++
            ctx.response()
                    .putHeader("cOntent-tYpe", "application/json")
                    .end(javaClass.getResourceAsStream("/github/readFile.json").use {
                        Buffer.buffer(it.readBytes())
                    })
        }
        val contents = githubClient.readFile("b8b", "cikit", "README.md").await()
        assertEquals("# ciKit", contents.contentAsString.trim())
        assertEquals(1, counter)
    }

}
