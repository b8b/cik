package org.cikit.core

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.vertx.core.Vertx
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.reflect.full.memberProperties

private fun readConfig(p: JsonParser): Map<String, JsonParser> {
    val configParserMap = mutableMapOf<String, JsonParser>()

    val coreConfigBuffer = JsonObjectBuffer()
    var isCoreConfig = true
    var fieldName = ""
    var moduleConfigBuffer = JsonObjectBuffer()

    parse@ while (true) {
        val token = p.nextToken()
        when (token) {
            null -> {
                //EOF
                break@parse
            }
            JsonToken.FIELD_NAME -> {
                if (fieldName.isEmpty()) {
                    fieldName = p.currentName
                } else {
                    moduleConfigBuffer.processEvent(p)
                }
                isCoreConfig = Config::class.memberProperties.any { it.name == fieldName }
                if (isCoreConfig) coreConfigBuffer.processEvent(p)
            }
            else -> {
                if (fieldName.isEmpty()) {
                    coreConfigBuffer.processEvent(p)?.let {
                        configParserMap[""] = it.asParser(p)
                                .also { it.nextToken() }
                        return configParserMap
                    }
                } else {
                    if (isCoreConfig) {
                        coreConfigBuffer.processEvent(p)
                    }
                    moduleConfigBuffer.processEvent(p)?.let {
                        if (!isCoreConfig) {
                            configParserMap[fieldName] = it.asParser(p)
                                    .also { it.nextToken() }
                            moduleConfigBuffer = JsonObjectBuffer()
                        }
                        isCoreConfig = true
                        fieldName = ""
                    }
                }
            }
        }
    }
    throw RuntimeException("unexpected EOF")
}

fun main(args: Array<String>) {
    System.setProperty(
            io.vertx.core.logging.LoggerFactory.LOGGER_DELEGATE_FACTORY_CLASS_NAME,
            io.vertx.core.logging.SLF4JLogDelegateFactory::class.java.name)
    LoggerFactory.getLogger(io.vertx.core.logging.LoggerFactory::class.java)

    val argsMap = args.associate {
        val i = it.indexOf('=')
        if (i < 0)
            Pair(it, null)
        else
            Pair(it.substring(0, i), it.substring(i.inc()))
    }
    val configFile = argsMap["-config"]?.let { File(it) }
    val configParser = if (configFile == null)
        JsonFactory().createParser("{}")
    else
        YAMLFactory().createParser(configFile)
    val configParserMap = readConfig(configParser)
    val objectMapper = jacksonObjectMapper()
    val coreConfig = objectMapper.readValue<Config>(configParserMap[""]!!)

    configureLogging(coreConfig.log)

    val vx = Vertx.vertx()
    CoreModule(vx, coreConfig, objectMapper, configParserMap).start(argsMap)
}
