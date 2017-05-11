package kotlinx.http.server

import kotlinx.sockets.channels.*

suspend fun Session.commit() {
    if (rawOutput.remaining < 2) {
        flush()
    }
    rawOutput.putByte(0x0d)
    rawOutput.putByte(0x0a)
}

interface Session {
    fun body(): ReadChannel
    val rawOutput: BufferedWriteChannel

    fun chunkedOutput(): WriteChannel
    fun directOutput(length: Long): WriteChannel
}

suspend fun Session.status(code: Int, description: String, version: HttpVersion = HttpVersion.HTTP11) {
    status(code, description.toByteArray(), version)
}

suspend fun Session.status(code: Int, description: ByteArray, version: HttpVersion = HttpVersion.HTTP11) {
    require(code in 100..999)

    val codeBytes = statusCodeBytes[code]
    val estimate = version.bytes.size + 1 + 3 + 1 + description.size + 2

    with(rawOutput) {
        if (remaining < estimate) {
            flush()
        }

        putBytes(version.bytes)
        putByte(0x20)

        putByte(codeBytes[0])
        putByte(codeBytes[1])
        putByte(codeBytes[2])

        putByte(0x20)
        putBytes(description)

        putByte(0x0d)
        putByte(0x0a)
    }
}

suspend fun Session.header(name: String, value: String) {
    val estimate = name.length + 2 + value.length

    with(rawOutput) {
        if (remaining < estimate) {
            flush()
        }

        putString0(name)
        putByte(0x3a) // :
        putByte(0x20)
        putString0(value)

        putByte(0x0d)
        putByte(0x0a)
    }
}

suspend fun Session.flush() {
    rawOutput.flush()
}

private val statusCodeBytes = (0..999).map { it.toString().toByteArray() }.toTypedArray()

private fun BufferedWriteChannel.putString0(s: String) {
    for (idx in 0..s.length - 1) {
        putByte(s[idx].toByte())
    }
}
