package kotlinx.http.server

import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.Channel
import kotlinx.http.server.codec.*
import kotlinx.sockets.*
import kotlinx.sockets.Socket
import kotlinx.sockets.adapters.*
import kotlinx.sockets.channels.*
import kotlinx.sockets.selector.*
import java.io.*
import java.net.*
import java.nio.*
import java.nio.channels.*
import kotlin.coroutines.experimental.*

private val bufferSize = 8192
private val buffersCount = 100000

fun runServer(context: CoroutineContext = CommonPool, port: Int = 8080, handler: RequestHandler): Job {
    val bufferPool = Channel<ByteBuffer>(buffersCount)
    val selector = ExplicitSelectorManager()

    launch(context) {
        for (i in 1..buffersCount) {
            bufferPool.offer(ByteBuffer.allocate(bufferSize))
        }
    }

    return launch(context) {
        aSocket(selector).tcp().bind(InetSocketAddress(port)).use { server ->
            while (true) {
                try {
                    val client = server.accept()
                    launch(context) {
                        client.use {
                            handleClient(client, handler, bufferPool)
                        }
                    }
                } catch (e: ClosedChannelException) {
                    break
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
            }
        }
    }.apply {
        disposeOnCompletion(selector)
        invokeOnCompletion {
            while (true) {
                bufferPool.poll() ?: break
            }

            bufferPool.close()
        }
    }
}

interface RequestHandler {
    suspend fun handle(request: HttpRequest, session: Session)
}

private class SessionImpl(pool: Channel<ByteBuffer>, destination: WriteChannel) : Session {
    var body: ReadChannel = EmptyReadChannel

    override val rawOutput = destination.bufferedWrite(pool)

    override fun body() = body

    override fun chunkedOutput(): WriteChannel {
        return ChunkedResponseWriteChannel(rawOutput)
    }

    override fun directOutput(length: Long): WriteChannel {
        return DirectResponseWriteChannel(length, rawOutput)
    }
}

private suspend fun handleClient(client: Socket, handler: RequestHandler, bufferPool: Channel<ByteBuffer>) {
    val bb = bufferPool.poll() ?: ByteBuffer.allocate(bufferSize)
    val hb = bufferPool.poll() ?: ByteBuffer.allocate(bufferSize)
    val rb = bufferPool.poll() ?: ByteBuffer.allocate(bufferSize)
    bb.clear().flip()

    val session = SessionImpl(bufferPool, client)

    try {
        loop@ while (true) {
            bb.compact()
            val parser = HttpParser(bb, hb)
            val request = try { parser.parse(client) } catch (e: IOException) { null } ?: break@loop
            session.body = when {
                !request.method.bodyExpected -> EmptyReadChannel
                request.isChunked() -> ChunkedRequestReadChannel(client, bb)
                else -> {
                    val length = request.headerFirst("Content-Length")?.value(request.headersBody)?.toLongOrNull()
                    if (length == null || length == 0L) EmptyReadChannel
                    else LimitedRequestReadChannel(bb, client, length)
                }
            }

            try {
                handler.handle(request, session)
                session.rawOutput.flush()
            } finally {
                session.rawOutput.reset()
            }
        }
    } finally {
        bufferPool.offer(bb)
        bufferPool.offer(hb)
        bufferPool.offer(rb)
    }
}

fun HttpRequest.isChunked() = headerFirst("Transfer-Encoding")?.value(headersBody)?.contains("chunked") ?: false

fun defaultConnectionForVersion(version: HttpVersion) = if (version == HttpVersion.HTTP11) "keep-alive" else "close"
