import kotlinx.coroutines.experimental.runBlocking
import org.cikit.core.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer

class CmdTest {

    @Test
    fun testSpawn() {
        val openssl = listOf("/Program Files/Git/mingw64/bin/openssl.exe")
                .firstOrNull { File(it).exists() }
                ?: "openssl"
        val p = spawn(openssl, "version", stdout = StdoutClose)
        val rc = p.process.waitFor()
        assertEquals(0, rc)
    }

    @Test
    fun testRead() {
        val openssl = listOf("/Program Files/Git/mingw64/bin/openssl.exe")
                .firstOrNull { File(it).exists() }
                ?: "openssl"
        val p = spawnForReading(openssl, "rand", "-hex", "8")
        val result = StringBuilder()
        runBlocking {
            for (buffer in p.stdout) {
                result.append(String(buffer.array(), buffer.arrayOffset(), buffer.remaining()))
            }
        }
        println(p.process.waitFor())
        println(result)
        assertTrue(result.trim().all { it.isDigit() || (it in 'a'..'f') || (it in 'A'..'F') })
        assertEquals(16, result.trim().length)
    }

    @Test
    fun testReadAndMergeStderr() {
        val openssl = listOf("/Program Files/Git/mingw64/bin/openssl.exe")
                .firstOrNull { File(it).exists() }
                ?: "openssl"
        val p = spawnForReading(openssl, "rand", "-hex", "8",
                env = mapOf("RANDFILE" to "/nowhere/does/not/exist"),
                stdin = StdinClose, stderr = StderrToStdout)
        val result = StringBuilder()
        runBlocking {
            for (buffer in p.stdout) {
                result.append(String(buffer.array(), buffer.arrayOffset(), buffer.remaining()))
            }
        }
        println(p.process.waitFor())
        println(result)
        assertTrue(result.toString().substringBefore('\n').trim().all {
            it.isDigit() || (it in 'a'..'f') || (it in 'A'..'F')
        })
        assertEquals("unable to write 'random state'",
                result.toString().substringAfter('\n').trim())
    }

    @Test
    fun testWrite() {
        val openssl = listOf("/Program Files/Git/mingw64/bin/openssl.exe")
                .firstOrNull { File(it).exists() }
                ?: "openssl"
        val p = spawnForWriting(openssl, "rsa", "-check", stdout = StdoutClose, stderr = StderrClose)
        runBlocking {
            p.stdin.send(ByteBuffer.wrap(("-----BEGIN RSA PRIVATE KEY-----\n" +
                    "MC0CAQACBQDGUHoVAgMBAAECBQDA0YqlAgMA7HsCAwDWrwIDAMENAgJO4QICfxk=\n" +
                    "-----END RSA PRIVATE KEY-----\n").toByteArray()))
            p.stdin.close()
        }
        val rc = p.process.waitFor()
        assertEquals(0, rc)
    }

    @Test
    fun testSpawn2() {
        val openssl = listOf("/Program Files/Git/mingw64/bin/openssl.exe")
                .firstOrNull { File(it).exists() }
                ?: "openssl"
        val p = spawn2(openssl, "dgst", "-sha256", "-r")
        runBlocking {
            p.stdin.send(ByteBuffer.wrap("hello world".toByteArray()))
            p.stdin.close()
        }
        val result = StringBuilder()
        runBlocking {
            for (buffer in p.stdout) {
                result.append(String(buffer.array(), buffer.arrayOffset(), buffer.remaining()))
            }
        }
        println(p.process.waitFor())
        assertEquals("b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9 *stdin", result.trim())
    }

    @Test
    fun testSpawn3() {
        val openssl = listOf("/Program Files/Git/mingw64/bin/openssl.exe")
                .firstOrNull { File(it).exists() }
                ?: "openssl"
        val p = spawn3(openssl, "dgst", "-sha258", "-r")
        p.stdin.close()
        var outIsEmpty = true
        runBlocking {
            for (buffer in p.stdout) {
                if (buffer.hasRemaining()) outIsEmpty = false
            }
        }
        val result = StringBuilder()
        runBlocking {
            for (buffer in p.stderr) {
                result.append(String(buffer.array(), buffer.arrayOffset(), buffer.remaining()))
            }
        }
        println(p.process.waitFor())
        assertTrue(outIsEmpty)
        assertEquals("unknown option '-sha258'",
                result.toString().substringBefore('\n').trim())
    }

}
