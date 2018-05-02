package org.cikit.core

import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.channels.Channel
import kotlinx.coroutines.experimental.channels.ReceiveChannel
import kotlinx.coroutines.experimental.channels.SendChannel
import kotlinx.coroutines.experimental.launch
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import kotlin.coroutines.experimental.CoroutineContext

sealed class StdinSource
object StdinInherit : StdinSource()
object StdinClose : StdinSource()
class StdinFrom(val file: File) : StdinSource()

private fun StdinSource.configure(pb: ProcessBuilder) {
    when (this) {
        is StdinInherit -> pb.redirectError(ProcessBuilder.Redirect.INHERIT)
        is StdinFrom -> pb.redirectError(ProcessBuilder.Redirect.to(file))
        else -> pb.redirectError(ProcessBuilder.Redirect.PIPE)
    }
}

sealed class StdoutTarget
object StdoutInherit : StdoutTarget()
object StdoutClose : StdoutTarget()
class StdoutTo(val file: File, val append: Boolean = false) : StdoutTarget()

private fun StdoutTarget.configure(pb: ProcessBuilder) {
    when (this) {
        is StdoutInherit -> pb.redirectOutput(ProcessBuilder.Redirect.INHERIT)
        is StdoutTo -> if (append) {
            pb.redirectOutput(ProcessBuilder.Redirect.appendTo(file))
        } else {
            pb.redirectOutput(ProcessBuilder.Redirect.to(file))
        }
        else -> pb.redirectOutput(ProcessBuilder.Redirect.PIPE)
    }
}

sealed class StderrTarget
object StderrInherit : StderrTarget()
object StderrToStdout : StderrTarget()
object StderrClose : StderrTarget()
class StderrTo(val file: File, val append: Boolean = false) : StderrTarget()

private fun StderrTarget.configure(pb: ProcessBuilder) {
    when (this) {
        is StderrInherit -> pb.redirectError(ProcessBuilder.Redirect.INHERIT)
        is StderrToStdout -> pb.redirectErrorStream(true)
        is StderrTo -> if (append) {
            pb.redirectError(ProcessBuilder.Redirect.appendTo(file))
        } else {
            pb.redirectError(ProcessBuilder.Redirect.to(file))
        }
        else -> pb.redirectError(ProcessBuilder.Redirect.PIPE)
    }
}

interface SpawnedProcess {
    val process: Process
}

interface SpawnedReadableProcess: SpawnedProcess {
    val stdout: ReceiveChannel<ByteBuffer>
}

interface SpawnedWritableProcess: SpawnedProcess {
    val stdin: SendChannel<ByteBuffer>
}

interface SpawnedProcess2: SpawnedProcess, SpawnedReadableProcess, SpawnedWritableProcess

interface SpawnedProcess3: SpawnedProcess2 {
    val stderr: ReceiveChannel<ByteBuffer>
}

private suspend fun ReceiveChannel<ByteBuffer>.copyToStream(out: OutputStream) {
    try {
        out.use { _out ->
            for (buffer in this) {
                if (buffer.hasArray()) {
                    _out.write(buffer.array(), buffer.arrayOffset(), buffer.remaining())
                } else {
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)
                    _out.write(bytes)
                }
            }
        }
    } catch (ex: Throwable) {
        cancel(ex)
    }
}

private suspend fun InputStream.copyToChannel(ch: SendChannel<ByteBuffer>) {
    try {
        val buffer = ByteArray(1024 * 4)
        use { `in` ->
            while (true) {
                val read = `in`.read(buffer)
                if (read < 0) break
                val bytes = ByteBuffer.allocate(read)
                bytes.put(buffer, 0, read)
                bytes.flip()
                ch.send(bytes)
            }
        }
        ch.close()
    } catch (ex: Throwable) {
        ch.close(ex)
    }
}

fun spawn(vararg args: String,
          wd: File? = null,
          env: Map<String, String> = emptyMap(),
          stdin: StdinSource = StdinInherit,
          stdout: StdoutTarget = StdoutInherit,
          stderr: StderrTarget = StderrInherit): SpawnedProcess {
    val pb = ProcessBuilder(*args)
    if (wd != null) pb.directory(wd)
    if (env.isNotEmpty()) pb.environment().putAll(env)
    stdin.configure(pb)
    stdout.configure(pb)
    stderr.configure(pb)
    val process = pb.start()
    if (stdin === StdinClose) process.outputStream.close()
    if (stdout === StdoutClose) process.inputStream.close()
    if (stderr === StderrClose) process.errorStream.close()
    return object: SpawnedProcess {
        override val process: Process = process
    }
}

fun spawnForReading(vararg args: String,
                    wd: File? = null,
                    env: Map<String, String> = emptyMap(),
                    stdin: StdinSource = StdinInherit,
                    stderr: StderrTarget = StderrInherit,
                    pool: CoroutineContext = CommonPool): SpawnedReadableProcess {
    val pb = ProcessBuilder(*args)
    if (wd != null) pb.directory(wd)
    if (env.isNotEmpty()) pb.environment().putAll(env)
    stdin.configure(pb)
    stderr.configure(pb)
    val process = pb.start()
    if (stdin === StdinClose) process.outputStream.close()
    if (stderr === StderrClose) process.errorStream.close()
    val stdOutChannel = Channel<ByteBuffer>()
    launch(pool) {
        process.inputStream.copyToChannel(stdOutChannel)
    }
    return object: SpawnedReadableProcess {
        override val process: Process = process
        override val stdout: ReceiveChannel<ByteBuffer> = stdOutChannel
    }
}

fun spawnForWriting(vararg args: String,
                    wd: File? = null,
                    env: Map<String, String> = emptyMap(),
                    stdout: StdoutTarget = StdoutInherit,
                    stderr: StderrTarget = StderrInherit,
                    pool: CoroutineContext = CommonPool): SpawnedWritableProcess {
    val pb = ProcessBuilder(*args)
    if (wd != null) pb.directory(wd)
    if (env.isNotEmpty()) pb.environment().putAll(env)
    stdout.configure(pb)
    stderr.configure(pb)
    val process = pb.start()
    if (stderr === StderrClose) process.errorStream.close()
    if (stdout === StdoutClose) process.inputStream.close()
    val stdInChannel = Channel<ByteBuffer>()
    launch(pool) {
        stdInChannel.copyToStream(process.outputStream)
    }
    return object: SpawnedWritableProcess {
        override val process: Process = process
        override val stdin: SendChannel<ByteBuffer> = stdInChannel
    }
}

fun spawn2(vararg args: String,
           wd: File? = null,
           env: Map<String, String> = emptyMap(),
           stderr: StderrTarget = StderrInherit,
           pool: CoroutineContext = CommonPool): SpawnedProcess2 {
    val pb = ProcessBuilder(*args)
    if (wd != null) pb.directory(wd)
    if (env.isNotEmpty()) pb.environment().putAll(env)
    stderr.configure(pb)
    val process = pb.start()
    if (stderr === StderrClose) process.errorStream.close()
    val stdInChannel = Channel<ByteBuffer>()
    launch(pool) {
        stdInChannel.copyToStream(process.outputStream)
    }
    val stdOutChannel = Channel<ByteBuffer>()
    launch(pool) {
        process.inputStream.copyToChannel(stdOutChannel)
    }
    return object: SpawnedProcess2 {
        override val process: Process = process
        override val stdin: SendChannel<ByteBuffer> = stdInChannel
        override val stdout: ReceiveChannel<ByteBuffer> = stdOutChannel
    }
}

fun spawn3(vararg args: String,
           wd: File? = null,
           env: Map<String, String> = emptyMap(),
           pool: CoroutineContext = CommonPool): SpawnedProcess3 {
    val sb = ProcessBuilder(*args)
    if (wd != null) sb.directory(wd)
    if (env.isNotEmpty()) sb.environment().putAll(env)
    val process = sb.start()
    val stdInChannel = Channel<ByteBuffer>()
    launch(pool) {
        stdInChannel.copyToStream(process.outputStream)
    }
    val stdOutChannel = Channel<ByteBuffer>()
    launch(pool) {
        process.inputStream.copyToChannel(stdOutChannel)
    }
    val stdErrChannel = Channel<ByteBuffer>()
    launch(pool) {
        process.errorStream.copyToChannel(stdErrChannel)
    }
    return object: SpawnedProcess3 {
        override val process: Process = process
        override val stdin: SendChannel<ByteBuffer> = stdInChannel
        override val stdout: ReceiveChannel<ByteBuffer> = stdOutChannel
        override val stderr: ReceiveChannel<ByteBuffer> = stdErrChannel
    }
}
