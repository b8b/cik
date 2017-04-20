import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.restassured.RestAssured
import io.restassured.http.ContentType
import io.vertx.core.Vertx
import io.vertx.core.http.HttpServer
import io.vertx.ext.web.Router
import io.vertx.kotlin.core.VertxOptions
import io.vertx.kotlin.core.http.HttpServerOptions
import io.vertx.kotlin.coroutines.awaitResult
import kotlinx.coroutines.experimental.runBlocking
import org.cikit.core.LogConfig
import org.cikit.core.configureLogging
import org.cikit.modules.github.GithubConfig
import org.cikit.modules.github.GithubHandler
import org.hamcrest.text.IsEmptyString
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test

class GithubHandlerTest {

    companion object {
        lateinit var om: ObjectMapper
        lateinit var vx: Vertx
        lateinit var server: HttpServer
        lateinit var router: Router

        @BeforeClass
        @JvmStatic
        fun setupClass(): Unit = runBlocking {
            configureLogging(LogConfig())
            om = jacksonObjectMapper()
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            vx = Vertx.vertx(VertxOptions(blockedThreadCheckInterval = 60_000))
            router = Router.router(vx)
            GithubHandler(vx, om, GithubConfig("changeit", "alice", "flowers"))
                    .setupRoutes(router)
            server = awaitResult {
                vx.createHttpServer(HttpServerOptions(idleTimeout = 10))
                        .requestHandler(router::accept)
                        .listen(0, it)
            }
            RestAssured.port = server.actualPort()
            println("mock server started on port ${server.actualPort()}")
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

    @Test
    fun testPush() {
        RestAssured
                .given()
                .contentType(ContentType.JSON)
                .header("x-github-event", "push")
                .header("x-github-delivery", "3514887b-4322-11e8-b299-dcfe07e12057")
                .header("x-hub-signature", "sha1=ebbafb1e3d4ddab1468e4a4276eee5f0eaa47c37")
                .body(javaClass.getResourceAsStream("/github/push.json").reader().readText())
                .`when`()
                .post("/hook")
                .then()
                .statusCode(200)
                .body(IsEmptyString.isEmptyOrNullString())
    }
}
