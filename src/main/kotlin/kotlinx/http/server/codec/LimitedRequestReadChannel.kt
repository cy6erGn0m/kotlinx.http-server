package kotlinx.http.server.codec

import kotlinx.sockets.channels.*
import java.nio.*

internal class LimitedRequestReadChannel(private val buffer: ByteBuffer, val source: ReadChannel, var remaining: Long = 0L) : ReadChannel {
    suspend override fun read(dst: ByteBuffer): Int {
        if (remaining == 0L) return -1

        var copied = 0
        while (buffer.hasRemaining() && dst.hasRemaining() && remaining > 0L) {
            dst.put(buffer.get())
            remaining--
            copied++
        }

        if (remaining >= dst.remaining()) {
            val rc = source.read(dst)

            return if (rc >= 0) {
                remaining -= rc
                rc + copied
            } else copied
        }

        val sub = dst.slice()
        sub.limit(minOf(dst.remaining(), remaining.toInt()))
        val rc = source.read(sub)
        if (rc >= 0) {
            remaining -= rc
            dst.position(dst.position() + rc)
            return rc + copied
        }

        return rc
    }
}