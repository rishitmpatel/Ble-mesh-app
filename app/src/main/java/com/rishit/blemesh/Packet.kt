package com.rishit.blemesh

import java.security.SecureRandom

/** Bluetooth SIG reserves 0xFFFF for testing. Replace before any real release. */
const val COMPANY_ID = 0xFFFF

/**
 * Wire format (v1 draft, matches README):
 *   id (8) | ttl (1) | dest (4) | payload (n)
 * Payload is plain UTF-8 for now. Encryption comes in build step 3.
 * dest = 4 zero bytes means broadcast (everyone shows it).
 */
class Packet(
    val id: ByteArray,
    val ttl: Int,
    val dest: ByteArray,
    val payload: ByteArray,
) {
    val idHex: String get() = id.joinToString("") { "%02x".format(it) }
    val isBroadcast: Boolean get() = dest.all { it == 0.toByte() }

    fun text(): String = String(payload, Charsets.UTF_8)

    fun withTtl(newTtl: Int) = Packet(id, newTtl, dest, payload)

    fun toBytes(): ByteArray {
        val out = ByteArray(HEADER + payload.size)
        id.copyInto(out, 0)
        out[ID_LEN] = ttl.toByte()
        dest.copyInto(out, ID_LEN + 1)
        payload.copyInto(out, HEADER)
        return out
    }

    companion object {
        const val ID_LEN = 8
        const val DEST_LEN = 4
        const val HEADER = ID_LEN + 1 + DEST_LEN
        const val DEFAULT_TTL = 5

        val BROADCAST = ByteArray(DEST_LEN)
        private val rng = SecureRandom()

        fun create(text: String, dest: ByteArray = BROADCAST, ttl: Int = DEFAULT_TTL): Packet {
            val id = ByteArray(ID_LEN).also { rng.nextBytes(it) }
            return Packet(id, ttl, dest, text.toByteArray(Charsets.UTF_8))
        }

        fun fromBytes(b: ByteArray): Packet? {
            if (b.size < HEADER) return null
            return Packet(
                id = b.copyOfRange(0, ID_LEN),
                ttl = b[ID_LEN].toInt() and 0xFF,
                dest = b.copyOfRange(ID_LEN + 1, HEADER),
                payload = b.copyOfRange(HEADER, b.size),
            )
        }
    }
}
