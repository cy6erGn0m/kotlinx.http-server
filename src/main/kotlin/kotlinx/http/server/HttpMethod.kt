package kotlinx.http.server

class HttpMethod(val name: String,
                 val bodyExpected: Boolean,
                 val id: Int = -1
                 ) {
    val hash = name.hashCodeLowerCase()

    companion object {
        val Get = HttpMethod("GET", false, 0)
        val Post = HttpMethod("POST", true, 1)
        val Put = HttpMethod("PUT", true, 2)
        val Delete = HttpMethod("DELETE", false, 3)
        val Head = HttpMethod("HEAD", false, 4)
        val Options = HttpMethod("OPTIONS", false, 5)

        val known = arrayOf(Get, Post, Put, Delete, Head, Options)
        val table: Array<HttpMethod?>
        var size: Int = 0

        private fun index(h: Int, size: Int) = size - 1 + (h % size)

        init {
            var result: Array<HttpMethod?>? = null
            outer@for (size in known.size + 1 .. known.size * 2 + 1) {
                val tmp = arrayOfNulls<HttpMethod>(size * 2 + 1)

                for (m in known) {
                    val h = index(m.hash, size)
                    if (tmp[h] != null) continue@outer
                    tmp[h] = m
                }

                result = tmp
                this.size = size
                break
            }

            if (result == null) throw IllegalStateException()
            table = result
        }

        fun lookup(h: Int) = table[index(h, size)]?.takeIf { it.hash == h }

        @JvmStatic
        fun main(args: Array<String>) {
            for (m in known) {
                println("${m.name} ${m.hash} ${7 + m.hash % 7}")
            }
        }
    }
}

