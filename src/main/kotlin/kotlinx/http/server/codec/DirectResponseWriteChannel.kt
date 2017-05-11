package kotlinx.http.server.codec

import kotlinx.sockets.channels.*
import java.nio.*

internal class DirectResponseWriteChannel(val limit: Long, val socket: WriteChannel) : WriteChannel {
    private var written = 0L

    override fun shutdownOutput() {
    }

    suspend override fun write(src: ByteBuffer) {
        val size = src.remaining()

        if (written + size > limit) {
            throw IllegalArgumentException("Response body size limit violation")
        }

        socket.write(src)
        written += (size - src.remaining())
    }
}