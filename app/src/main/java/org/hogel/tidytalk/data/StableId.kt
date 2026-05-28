package org.hogel.tidytalk.data

import java.security.MessageDigest

private const val B62 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"

private fun encodeBase62Fixed(value: Long, width: Int): String {
    val sb = StringBuilder(width)
    var x = value
    repeat(width) {
        sb.append(B62[(x % 62).toInt()])
        x /= 62
    }
    return sb.reverse().toString()
}

private fun digestPrefixLong(bytes: ByteArray, byteCount: Int): Long {
    var v = 0L
    repeat(byteCount) { v = (v shl 8) or (bytes[it].toLong() and 0xFF) }
    return v
}

/**
 * Assigns short, deterministic IDs to [keys] using SHA-256. Default width is
 * 6 chars (32 bit of digest); any keys colliding at that width are re-encoded
 * at 8 chars (48 bit) so every returned ID is unique within the call.
 *
 * The point is stability across deletes: removing one item from the input does
 * not renumber the others, which is what callers compare across AI turns.
 */
fun assignStableIds(keys: List<String>): Map<String, String> {
    if (keys.isEmpty()) return emptyMap()
    val md = MessageDigest.getInstance("SHA-256")
    val short = keys.associateWith { encodeBase62Fixed(digestPrefixLong(md.digest(it.toByteArray()), 4), 6) }
    val collided = short.entries
        .groupBy { it.value }
        .values
        .filter { it.size > 1 }
        .flatMapTo(mutableSetOf()) { group -> group.map { it.key } }
    if (collided.isEmpty()) return short
    return keys.associateWith { key ->
        if (key in collided) {
            encodeBase62Fixed(digestPrefixLong(md.digest(key.toByteArray()), 6), 8)
        } else {
            short.getValue(key)
        }
    }
}
