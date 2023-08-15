package ru.mtuci

import java.io.InputStream
import java.lang.StringBuilder


fun InputStream.readUntil(suffix: String): String {
    val strBuilder = StringBuilder()
    var next = read()

    while (next != -1) {
        if (next != 0) {
            strBuilder.append(next.toChar())
            if (strBuilder.endsWith(suffix))
                return strBuilder.removeSuffix(suffix).toString()
        }

        next = read()
    }

    return strBuilder.toString()
}

fun isBase64(cs: CharSequence) =
    Regex("^([A-Za-z0-9+/]{4})*([A-Za-z0-9+/]{3}=|[A-Za-z0-9+/]{2}==)?\$").matches(cs)
