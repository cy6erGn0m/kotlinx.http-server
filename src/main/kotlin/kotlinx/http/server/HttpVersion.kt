package kotlinx.http.server

enum class HttpVersion(val text: String) {
    HTTP10("HTTP/1.0"),
    HTTP11("HTTP/1.1");

    val bytes: ByteArray = text.toByteArray(Charsets.ISO_8859_1)

    companion object {
        val byHash = values().associateBy { it.text.hashCodeLowerCase() }
        val longest = values().map { it.bytes.size }.max()
    }
}