package com.example.localledger.domain.importing

object NotificationText {
    private val countPrefix = Regex("""[\[【［]\s*\d{1,4}\s*条\s*[\]】］]\s*""")
    private val clerkPrefix = Regex("""[\[【［]\s*店员消息\s*[\]】］]\s*""")

    fun plain(value: CharSequence?): String {
        if (value == null || value.isEmpty()) return ""
        val out = StringBuilder(value.length)
        for (index in 0 until value.length) {
            out.append(value[index])
        }
        return out.toString().trim()
    }

    fun join(parts: List<CharSequence?>): String {
        return parts
            .map(::plain)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString("\n")
    }

    fun normalize(raw: String): String {
        val mapped = buildString(raw.length) {
            for (ch in raw) {
                append(
                    when (ch) {
                        in '０'..'９' -> '0' + (ch - '０')
                        '．' -> '.'
                        '，' -> ','
                        '￥' -> '¥'
                        '\u00A0', '\u3000', '\u2007', '\u202F' -> ' '
                        '：' -> ':'
                        else -> ch
                    },
                )
            }
        }
        return clerkPrefix.replace(countPrefix.replace(mapped, ""), "")
            .replace(Regex("""[ \t]+\n"""), "\n")
            .trim()
    }
}
