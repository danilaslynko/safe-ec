package ru.mtuci

import java.io.Reader

fun Reader.readUntil(suffix: String): String {
    var result = StringBuilder()
    var curr = read()
    while (curr != -1) {
        val char = curr.toChar()
        result.append(char)
        if (result.length >= suffix.length && (result.length - suffix.length until result.length - 1)
                .filterIndexed { suffixIndex, resultIndex -> result[resultIndex] != suffix[suffixIndex] }
                .none()) { break }

        curr = read()
    }
    if (result.endsWith(suffix))
        result = result.delete(result.length - suffix.length, result.length)

    return result.toString()
}