package kotlinx.http.server.codec

import kotlinx.sockets.channels.*
import java.nio.*

internal class ChunkedRequestReadChannel(val source: ReadChannel, private val buffer: ByteBuffer) : ReadChannel {
    private var chunkSize: Long = -1

    suspend override fun read(dst: ByteBuffer): Int {
        if (ensureChunk()) return -1
        if (chunkSize == 0L) return -1
        var copied = 0

        while (buffer.hasRemaining() && dst.hasRemaining() && chunkSize > 0L) {
            dst.put(buffer.get())
            chunkSize--
            copied++
        }

        if (chunkSize == 0L) chunkSize = -1L
        if (!dst.hasRemaining() || chunkSize == -1L) return copied

        val rc = if (chunkSize >= dst.remaining()) {
            source.read(dst)
        } else {
            val rc = dst.slice().let { sub ->
                sub.limit(minOf(dst.remaining(), chunkSize.toInt()))
                source.read(sub)
            }
            if (rc > 0) dst.position(dst.position() + rc)

            rc
        }

        when (rc) {
            -1 -> {
                chunkSize = 0
                return copied
            }
            else -> {
                chunkSize -= rc
                if (chunkSize == 0L) chunkSize = -1L
                copied += rc
            }
        }

        return copied
    }

    private suspend fun ensureChunk(): Boolean {
        var size = 0L

        chunkLoop@ while (chunkSize == -1L) {
            if (!buffer.hasRemaining()) {
                buffer.clear()
                if (source.read(buffer) == -1) {
                    chunkSize = 0
                    return true
                }
                buffer.flip()
            }

            while (buffer.hasRemaining()) {
                val ch = buffer.get()

                when (ch) {
                    0x0D.toByte() -> {
                    }
                    0x0A.toByte() -> {
                        chunkSize = size
                        break@chunkLoop
                    }
                    in 0x61..0x66 -> size = size * 16 + (ch - 0x61 + 10)
                    in 0x30..0x39 -> size = size * 16 + (ch - 0x30)
                    else -> throw IllegalStateException("Wrong chunk number")
                }
            }
        }

        return false
    }
}