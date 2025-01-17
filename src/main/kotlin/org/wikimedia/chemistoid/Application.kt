package org.wikimedia.chemistoid

import ch.qos.logback.classic.Logger
import io.github.dan2097.jnainchi.InchiStatus
import io.ktor.http.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.jvm.javaio.*
import org.openscience.cdk.DefaultChemObjectBuilder
import org.openscience.cdk.depict.DepictionGenerator
import org.openscience.cdk.inchi.InChIGeneratorFactory
import org.slf4j.LoggerFactory

fun main() {
    embeddedServer(Netty, host = "0.0.0.0", port = 8000) {
        routing {
            get("/") { call.respondText("InChI render service using CDK, https://github.com/ebraminio/Chemistoid") }
            get(Regex("/InChI=.*")) { render(call) }
        }
    }.start(wait = true)
}

private val logger = LoggerFactory.getLogger("org.wikimedia.chemistoid") as Logger

private suspend fun render(call: RoutingCall) {
    val inchi = "InChI=${call.request.uri.split("InChI=")[1]}"
    val factory = InChIGeneratorFactory.getInstance()

    val structure = runCatching {
        factory.getInChIToStructure(inchi, DefaultChemObjectBuilder.getInstance())
    }.onFailure { logger.error("Failure on to structure", it) }.getOrNull() ?: run {
        return call.respondText(
            "internal server error",
            status = HttpStatusCode.InternalServerError,
        )
    }

    val ret = structure.status
    if (ret == InchiStatus.ERROR) return call.respondText("failed to render", status = HttpStatusCode.BadRequest)
    val depiction = DepictionGenerator().depict(structure.atomContainer)
    call.respondBytesWriter(ContentType.Image.SVG) {
        depiction.writeTo("svg", toOutputStream())
    }
}
