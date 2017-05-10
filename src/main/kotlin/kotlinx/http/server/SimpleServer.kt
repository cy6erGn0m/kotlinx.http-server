package kotlinx.http.server

import kotlinx.coroutines.experimental.*
import kotlinx.sockets.*
import kotlinx.sockets.Socket
import kotlinx.sockets.channels.*
import kotlinx.sockets.selector.*
import java.net.*
import java.nio.*
import java.util.concurrent.*

private val bufferSize = 8192
private val buffersCount = 100000
private val bufferPool = ArrayBlockingQueue<ByteBuffer>(buffersCount)

fun main(args: Array<String>) {
    runServer() { request, socket ->
        val keepAlive = request.headerFirst("Connection")?.value(request.headersBody) ?: defaultConnectionForVersion(request.version)

        when {
            request.method != HttpMethod.Get -> socket.respond(405, "Method Not Allowed", request.version, "close", "Method not allowed")
            request.uri != "/" -> socket.respond(404, "Not Found", request.version, keepAlive, "Resource at ${request.uri} was not found")
            else -> socket.respond(200, "OK", request.version, keepAlive, "Hello, World!")
        }
    }
}

fun runServer(port: Int = 8080, handler: suspend (HttpRequest, ReadWriteSocket) -> Unit) {
    for (i in 1..buffersCount) {
        bufferPool.put(ByteBuffer.allocate(bufferSize))
    }

    runBlocking {
        aSocket(ExplicitSelectorManager()).tcp().bind(InetSocketAddress(port)).use { server ->
            while (true) {
                try {
                    val client = server.accept()
                    launch(CommonPool) {
                        client.use {
                            handleClient(client, handler)
                        }
                    }
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
            }
        }
    }
}

private suspend fun handleClient(client: Socket, handler: suspend (HttpRequest, ReadWriteSocket) -> Unit) {
    val bb = bufferPool.poll() ?: ByteBuffer.allocate(bufferSize)
    val hb = bufferPool.poll() ?: ByteBuffer.allocate(bufferSize)
    bb.flip()

    try {
        loop@ while (true) {
            bb.compact()
            val parser = HttpParser(bb, hb)
            val request = withTimeoutOrNull(10L, TimeUnit.SECONDS) { parser.parse(client) } ?: break@loop

            handler(request, client)
        }
    } finally {
        bufferPool.offer(bb)
        bufferPool.offer(hb)
    }
}

private fun defaultConnectionForVersion(version: HttpVersion) = if (version == HttpVersion.HTTP11) "keep-alive" else "close"

suspend fun WriteChannel.respond(code: Int, statusMessage: String, version: HttpVersion, connection: String?, content: String) {
    write(ByteBuffer.wrap(buildString(256) {
        append(version.text)
        append(' ')
        append(code)
        append(' ')
        append(statusMessage)
        append("\r\n")

        if (connection != null) {
            append("Connection: "); append(connection); append("\r\n")
        }

        append("Content-Type: text/plain\r\n")
        append("Content-Length: "); append((content.length + 2).toString()); append("\r\n")
        append("\r\n")
        append(content)
        append("\r\n")
    }.toByteArray(Charsets.ISO_8859_1)))
}
