package com.rishit.blemesh

/** Remembers recent message IDs so the same packet is never shown or relayed twice. */
class SeenCache(
    private val maxEntries: Int = 1000,
    private val expiryMs: Long = 30 * 60 * 1000L,
) {
    private val map = object : LinkedHashMap<String, Long>(16, 0.75f, false) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean =
            size > maxEntries
    }

    /** Returns true if the id is new (and records it), false if already seen. */
    @Synchronized
    fun addIfNew(id: String, now: Long = System.currentTimeMillis()): Boolean {
        val t = map[id]
        if (t != null && now - t < expiryMs) return false
        map[id] = now
        return true
    }
}
