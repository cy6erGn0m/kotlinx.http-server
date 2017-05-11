package kotlinx.http.server.codec

import kotlinx.coroutines.experimental.*
import kotlinx.sockets.channels.*
import java.nio.*
import java.nio.channels.*

internal class ChunkedResponseWriteChannel(val output: BufferedWriteChannel) : WriteChannel {
    private var closed = false

    override fun shutdownOutput() {
        if (!closed) {
            closed = true
            runBlocking {
                output.putByte(0x30)
                output.putByte(0x0d)
                output.putByte(0x0a)
                output.putByte(0x0d)
                output.putByte(0x0a)

                output.flush()
            }
        }
    }

    suspend override fun write(src: ByteBuffer) {
        if (closed) throw ClosedChannelException()

        if (src.hasRemaining()) {
            output.putString(src.remaining().toString(16), Charsets.ISO_8859_1)

            output.ensureCapacity(2)
            output.putByte(0x0d)
            output.putByte(0x0a)
            output.write(src)
            output.ensureCapacity(2)
            output.putByte(0x0d)
            output.putByte(0x0a)
        }
    }
}