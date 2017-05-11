package kotlinx.http.server

import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.*
import java.nio.*

private val bufferSize = 8192
private val buffersCount = 100000

fun main(args: Array<String>) {
    val OK = "OK".toByteArray()
    val MethodNotAllowed = "Method Not Allowed".toByteArray()
    val NotFound = "Not Found".toByteArray()
    val HelloWorld = ByteBuffer.wrap("Hello, World\r\n".toByteArray())
    val HelloWorldLength = HelloWorld.remaining().toString()

    val bufferPool = Channel<ByteBuffer>(buffersCount)

    launch(CommonPool) {
        for (i in 1..buffersCount) {
            bufferPool.offer(ByteBuffer.allocate(bufferSize))
        }
    }

    val j = runServer(bufferPool = bufferPool, handler = object : RequestHandler {
        suspend override fun handle(request: HttpRequest, session: Session) {
            when {
                request.method != HttpMethod.Get -> {
                    session.status(405, MethodNotAllowed)
                    session.header("Connection", "close")
                    session.header("Content-Length", "0")
                    session.commit()
                    session.flush()
                }
                request.uri != "/" -> {
                    val connection = request.headerValueFirst("Connection") ?: defaultConnectionForVersion(request.version)

                    val message = "Resource at ${request.uri} was not found\r\n".toByteArray()
                    session.status(404, NotFound)
                    session.header("Content-Length", message.size.toString())
                    session.header("Connection", connection)
                    session.commit()
                    session.rawOutput.write(ByteBuffer.wrap(message))
                    session.flush()
                }
                else -> {
                    val connection = request.headerValueFirst("Connection") ?: defaultConnectionForVersion(request.version)

                    session.status(200, OK)
                    session.header("Content-Length", HelloWorldLength)
                    session.header("Connection", connection)
                    session.commit()
                    session.rawOutput.write(HelloWorld.duplicate())
                    session.flush()
                }
            }
        }
    })

    runBlocking {
        j.join()
    }
}