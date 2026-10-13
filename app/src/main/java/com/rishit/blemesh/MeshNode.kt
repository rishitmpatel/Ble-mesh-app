package com.rishit.blemesh

import android.os.Handler
import android.os.Looper
import java.util.Random

/**
 * Mesh logic on top of BleTransport: dedup, TTL, random relay delay,
 * and cancelling a relay if other phones already rebroadcast the same id.
 * Main thread only.
 */
class MeshNode(
    private val transport: BleTransport,
    private val onMessage: (String) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val seen = SeenCache()
    private val dupCount = HashMap<String, Int>() // id -> duplicates heard while relay pending
    private val rng = Random()

    val maxPayloadBytes: Int get() = transport.maxPayloadBytes
    val extendedSupported: Boolean get() = transport.extendedSupported

    /** Returns false if the text is too long for one packet on this phone. */
    fun send(text: String): Boolean {
        if (text.toByteArray(Charsets.UTF_8).size > maxPayloadBytes) return false
        val p = Packet.create(text)
        seen.addIfNew(p.idHex) // so we ignore our own echo coming back
        transport.send(p)
        return true
    }

    fun onReceive(p: Packet) {
        val key = p.idHex

        // Relay already scheduled for this id: just count the duplicate.
        dupCount[key]?.let {
            dupCount[key] = it + 1
            return
        }
        if (!seen.addIfNew(key)) return

        if (p.isBroadcast) onMessage(p.text())

        if (p.ttl <= 1) return
        dupCount[key] = 0
        val delay = RELAY_MIN_MS + rng.nextInt((RELAY_MAX_MS - RELAY_MIN_MS).toInt())
        handler.postDelayed({
            val dups = dupCount.remove(key) ?: 0
            if (dups < DUP_CANCEL) transport.send(p.withTtl(p.ttl - 1))
        }, delay)
    }

    companion object {
        const val RELAY_MIN_MS = 50L
        const val RELAY_MAX_MS = 500L
        const val DUP_CANCEL = 3
    }
}
