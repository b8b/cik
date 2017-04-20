package org.cikit.core

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.core.async.ByteArrayFeeder
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.util.TokenBuffer
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.vertx.core.buffer.Buffer
import kotlinx.coroutines.experimental.channels.Channel
import kotlinx.coroutines.experimental.channels.ReceiveChannel
import kotlinx.coroutines.experimental.channels.SendChannel
import java.io.IOException

class JsonObjectBuffer {

    private var buffer: TokenBuffer? = null
    private var objectDepth = -1
    private var arrayDepth = 0

    private fun createBuffer(p: JsonParser): TokenBuffer {
        objectDepth = -1
        arrayDepth = 0
        TokenBuffer(p).let {
            buffer = it
            return it
        }
    }

    fun processEvent(p: JsonParser): TokenBuffer? {
        var result: TokenBuffer? = null
        val token = p.currentToken()
        when (token) {
            JsonToken.START_OBJECT -> {
                (buffer ?: createBuffer(p)).copyCurrentEvent(p)
                objectDepth++
            }
            JsonToken.END_OBJECT -> {
                buffer?.copyCurrentEvent(p)
                if (objectDepth == 0 && arrayDepth == 0) {
                    buffer?.let {
                        it.flush()
                        result = it
                    }
                    buffer = null
                }
                objectDepth--
            }
            JsonToken.START_ARRAY -> {
                buffer?.copyCurrentEvent(p)
                arrayDepth++
            }
            JsonToken.END_ARRAY -> {
                buffer?.copyCurrentEvent(p)
                arrayDepth--
            }
            else -> buffer?.copyCurrentEvent(p)
        }
        return result
    }

}

suspend inline fun <reified T> decodeJson(readChannel: ReceiveChannel<Buffer>): T {
    val resultChannel = Channel<T>(1)
    decodeJson(readChannel, resultChannel)
    return resultChannel.receiveOrNull() ?: throw IOException("error decoding empty json stream")
}

suspend inline fun <reified T> decodeJson(readChannel: ReceiveChannel<Buffer>, writeChannel: SendChannel<T>) {
    val objBuffer = JsonObjectBuffer()
    val objReader = jacksonObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .readerFor(T::class.java)
    val p = JsonFactory().createNonBlockingByteArrayParser()
    parse@ while (true) {
        val token = p.nextToken()
        when (token) {
            null -> {
                //EOF
                break@parse
            }
            JsonToken.NOT_AVAILABLE -> {
                with(p.nonBlockingInputFeeder as ByteArrayFeeder) {
                    val buffer = readChannel.receiveOrNull()
                    if (buffer == null) {
                        endOfInput()
                    } else {
                        val bytes = buffer.bytes
                        feedInput(bytes, 0, bytes.size)
                    }
                }
            }
            else -> objBuffer.processEvent(p)?.let {
                writeChannel.send(objReader.readValue(it.asParser(p).also { it.nextToken() }))
            }
        }
    }
}
